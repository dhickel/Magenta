package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlanService {
    private static final int MAX_QUEUED_QUESTIONS = 5;

    private final ChatPlanRepository planRepository;
    private final ChatMemoryRepository chatMemoryRepository;
    private final ChatMarkdownRenderer markdownRenderer;

    public PlanService(ChatPlanRepository planRepository, ChatMemoryRepository chatMemoryRepository) {
        this(planRepository, chatMemoryRepository, new ChatMarkdownRenderer());
    }

    @Autowired
    public PlanService(
        ChatPlanRepository planRepository,
        ChatMemoryRepository chatMemoryRepository,
        ChatMarkdownRenderer markdownRenderer
    ) {
        this.planRepository = planRepository;
        this.chatMemoryRepository = chatMemoryRepository;
        this.markdownRenderer = markdownRenderer;
    }

    public ExecutionPlan beginPlan(String conversationId, String prePlanningModel, String executionModel) {
        int startOrder = chatMemoryRepository.findByConversationId(conversationId).size();
        Instant now = Instant.now();
        ExecutionPlan existing = planRepository.find(conversationId).orElse(null);
        return planRepository.save(new ExecutionPlan(
            conversationId,
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            "goal_and_deliverables",
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            normalize(prePlanningModel),
            normalize(executionModel),
            List.of(),
            0,
            startOrder,
            null,
            existing == null ? now : existing.createdAt(),
            now
        ));
    }

    public ExecutionPlan beginPlan(String conversationId) {
        return beginPlan(conversationId, null, null);
    }

    public Optional<ExecutionPlan> activePlan(String conversationId) {
        return planRepository.find(conversationId);
    }

    public List<String> listConversationIds() {
        return planRepository.findConversationIds();
    }

    public PlanMode mode(String conversationId) {
        return planRepository.find(conversationId)
            .map(ExecutionPlan::mode)
            .orElse(PlanMode.NORMAL);
    }

    public String prePlanningModel(String conversationId) {
        return planRepository.find(conversationId)
            .map(ExecutionPlan::prePlanningModel)
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    public String executionModel(String conversationId) {
        return planRepository.find(conversationId)
            .map(ExecutionPlan::executionModel)
            .filter(StringUtils::hasText)
            .orElse(null);
    }

    public String finalMessage(String conversationId) {
        return planRepository.find(conversationId)
            .map(ExecutionPlan::finalMessage)
            .orElse(null);
    }

    public ExecutionPlan updateDraftPlan(
        String conversationId,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> deliverables,
        List<String> steps,
        List<String> acceptanceCriteria
    ) {
        return updateDraftPlan(
            conversationId,
            goal,
            title,
            summary,
            notes,
            deliverables,
            null,
            null,
            null,
            steps,
            acceptanceCriteria
        );
    }

    public ExecutionPlan updateDraftPlan(
        String conversationId,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> deliverables,
        List<String> inputs,
        List<String> outputs,
        List<String> assumptions,
        List<String> steps,
        List<String> acceptanceCriteria
    ) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_update");
        return planRepository.save(new ExecutionPlan(
            existing.conversationId(),
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            existing.planningTask(),
            choose(goal, existing.goal()),
            choose(title, existing.title()),
            choose(summary, existing.summary()),
            mergeNotes(existing.notes(), notes),
            deliverables == null ? existing.deliverables() : cleanList(deliverables),
            inputs == null ? existing.inputs() : cleanList(inputs),
            outputs == null ? existing.outputs() : cleanList(outputs),
            assumptions == null ? existing.assumptions() : cleanList(assumptions),
            steps == null ? existing.steps() : orderedSteps(steps),
            acceptanceCriteria == null ? existing.acceptanceCriteria() : cleanList(acceptanceCriteria),
            existing.executionEvidence(),
            existing.validationFeedback(),
            existing.prePlanningModel(),
            existing.executionModel(),
            existing.pendingQuestions(),
            existing.pendingQuestionIndex(),
            existing.planStartMessageOrder(),
            existing.finalMessage(),
            existing.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan saveDraftPlan(
        String conversationId,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> steps,
        List<String> assumptions,
        List<String> acceptanceCriteria
    ) {
        return updateDraftPlan(
            conversationId,
            goal,
            title,
            summary,
            notes,
            StringUtils.hasText(title) ? List.of(title.trim()) : null,
            null,
            null,
            assumptions,
            steps,
            acceptanceCriteria
        );
    }

    public ExecutionPlan setGoal(String conversationId, String goal) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_set_goal");
        String normalizedGoal = normalize(goal);
        if (normalizedGoal == null) {
            throw new IllegalArgumentException("plan_set_goal requires a goal");
        }
        return planRepository.save(new ExecutionPlan(
            existing.conversationId(),
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            nextTaskAfterGoal(existing),
            normalizedGoal,
            choose(existing.title(), titleFromGoal(normalizedGoal)),
            existing.summary(),
            existing.notes(),
            existing.deliverables(),
            existing.inputs(),
            existing.outputs(),
            existing.assumptions(),
            existing.steps(),
            existing.acceptanceCriteria(),
            existing.executionEvidence(),
            existing.validationFeedback(),
            existing.prePlanningModel(),
            existing.executionModel(),
            existing.pendingQuestions(),
            existing.pendingQuestionIndex(),
            existing.planStartMessageOrder(),
            existing.finalMessage(),
            existing.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan setPlanningTask(String conversationId, String planningTask) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_set_task");
        String normalizedTask = normalize(planningTask);
        if (normalizedTask == null) {
            throw new IllegalArgumentException("plan_set_task requires a current planning task");
        }
        return saveWithPlanningTask(existing, normalizedTask);
    }

    public ExecutionPlan putItem(String conversationId, String sectionName, Integer key, String text) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_put_item");
        PlanSection section = PlanSection.fromToolName(sectionName);
        int itemKey = requirePositiveKey(key, "plan_put_item");
        String normalizedText = normalize(text);
        if (normalizedText == null) {
            throw new IllegalArgumentException("plan_put_item requires text");
        }
        return saveWithSection(
            existing,
            section,
            itemKey,
            normalizedText,
            false
        );
    }

    public ExecutionPlan deleteItem(String conversationId, String sectionName, Integer key) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_delete_item");
        PlanSection section = PlanSection.fromToolName(sectionName);
        int itemKey = requirePositiveKey(key, "plan_delete_item");
        return saveWithSection(existing, section, itemKey, null, true);
    }

    public ExecutionPlan askQuestions(String conversationId, List<String> questions) {
        ExecutionPlan existing = requirePlanMode(conversationId, "plan_ask_questions");
        List<String> cleanQuestions = cleanList(questions);
        if (cleanQuestions.isEmpty()) {
            throw new IllegalArgumentException("plan_ask_questions requires at least one question");
        }
        if (cleanQuestions.size() > MAX_QUEUED_QUESTIONS) {
            throw new IllegalArgumentException("plan_ask_questions accepts at most five questions");
        }
        return planRepository.save(new ExecutionPlan(
            existing.conversationId(),
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            "clarification_questions",
            existing.goal(),
            existing.title(),
            existing.summary(),
            existing.notes(),
            existing.deliverables(),
            existing.inputs(),
            existing.outputs(),
            existing.assumptions(),
            existing.steps(),
            existing.acceptanceCriteria(),
            existing.executionEvidence(),
            existing.validationFeedback(),
            existing.prePlanningModel(),
            existing.executionModel(),
            cleanQuestions,
            0,
            existing.planStartMessageOrder(),
            existing.finalMessage(),
            existing.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan recordPromptAnswer(String conversationId, String answer, String notes) {
        return recordPromptAnswer(conversationId, answer, notes, null);
    }

    public ExecutionPlan recordPromptAnswer(String conversationId, String answer, String notes, Integer expectedQuestionIndex) {
        ExecutionPlan plan = requirePlanMode(conversationId, "planning answer");
        if (!plan.hasPendingQuestion()) {
            throw new IllegalStateException("No active planning question exists for this conversation");
        }
        int currentQuestionIndex = plan.pendingQuestionIndex() + 1;
        if (expectedQuestionIndex != null && expectedQuestionIndex != currentQuestionIndex) {
            throw new IllegalStateException(
                "Stale planning answer. Expected question " + currentQuestionIndex + " but received " + expectedQuestionIndex
            );
        }
        if (!StringUtils.hasText(answer) && !StringUtils.hasText(notes)) {
            throw new IllegalArgumentException("Planning answer requires an answer");
        }
        String question = plan.currentQuestion();
        appendPlanningAnswer(conversationId, question, answer, notes);
        int nextIndex = plan.pendingQuestionIndex() + 1;
        List<String> pendingQuestions = nextIndex >= plan.pendingQuestions().size()
            ? List.of()
            : plan.pendingQuestions();
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            pendingQuestions,
            pendingQuestions.isEmpty() ? 0 : nextIndex,
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan markReadyForApproval(String conversationId) {
        ExecutionPlan plan = requirePlanMode(conversationId, "plan_ready_for_approval");
        if (plan.hasPendingQuestion()) {
            throw new IllegalStateException("plan_ready_for_approval requires all queued planning questions to be answered");
        }
        validateComplete(plan, "plan_ready_for_approval");
        return planRepository.save(copyWith(plan, PlanMode.PLAN, PlanStatus.READY_FOR_APPROVAL, List.of(), 0));
    }

    public ExecutionPlan approvePlan(String conversationId) {
        ExecutionPlan plan = requirePlanMode(conversationId, "approve plan");
        validateComplete(plan, "approve plan");
        return planRepository.save(copyWith(plan, PlanMode.PLAN, PlanStatus.APPROVED, List.of(), 0));
    }

    public ExecutionPlan saveAsTask(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        validateComplete(plan, "save as task");
        return planRepository.save(copyWith(plan, PlanMode.NORMAL, PlanStatus.SAVED_TASK, List.of(), 0));
    }

    public ExecutionPlan markExecuting(String conversationId) {
        ExecutionPlan plan = requireSavedPlan(conversationId);
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            PlanMode.EXECUTE_PLAN,
            PlanStatus.EXECUTING,
            null,
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            List.of(),
            List.of(),
            plan.prePlanningModel(),
            plan.executionModel(),
            List.of(),
            0,
            plan.planStartMessageOrder(),
            null,
            plan.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan markCompleted(String conversationId) {
        return markCompleted(conversationId, null);
    }

    public ExecutionPlan markCompleted(String conversationId, String finalMessage) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            PlanMode.NORMAL,
            PlanStatus.COMPLETED,
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            List.of(),
            0,
            plan.planStartMessageOrder(),
            finalMessage,
            plan.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan markNeedsReview(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        return planRepository.save(copyWith(plan, PlanMode.NORMAL, PlanStatus.NEEDS_REVIEW, List.of(), 0));
    }

    public ExecutionPlan recordExecutionReport(
        String conversationId,
        String summary,
        List<String> evidence,
        List<String> deviations,
        List<String> unmetCriteria,
        List<String> artifactPaths
    ) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        if (plan.mode() != PlanMode.EXECUTE_PLAN) {
            throw new IllegalStateException("plan_report is available only while executing a saved plan");
        }
        List<String> entries = new ArrayList<>();
        addLabeled(entries, "Summary", normalize(summary));
        addLabeled(entries, "Evidence", cleanList(evidence));
        addLabeled(entries, "Deviation", cleanList(deviations));
        addLabeled(entries, "Unmet criterion", cleanList(unmetCriteria));
        addLabeled(entries, "Artifact", cleanList(artifactPaths));
        if (entries.isEmpty()) {
            entries.add("Summary: execution reported no details.");
        }
        List<String> updatedEvidence = new ArrayList<>(plan.executionEvidence());
        updatedEvidence.addAll(entries);
        return planRepository.save(withExecutionEvidence(plan, updatedEvidence));
    }

    public ExecutionPlan recordFallbackExecutionEvidence(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        if (!plan.executionEvidence().isEmpty()) {
            return plan;
        }
        return planRepository.save(withExecutionEvidence(
            plan,
            List.of("Deviation: execution returned without a structured completion ledger.")
        ));
    }

    public ExecutionPlan recordValidationFeedback(String conversationId, List<String> feedback) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            plan.mode(),
            plan.status(),
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            cleanList(feedback),
            plan.prePlanningModel(),
            plan.executionModel(),
            plan.pendingQuestions(),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        ));
    }

    public void exitPlan(String conversationId) {
        planRepository.find(conversationId).ifPresent(plan -> {
            trimConversation(conversationId, plan.planStartMessageOrder());
            planRepository.delete(conversationId);
        });
    }

    public void clearConversationForExecution(String conversationId) {
        chatMemoryRepository.saveAll(conversationId, List.of());
    }

    public ChatPlanState view(String conversationId) {
        return planRepository.find(conversationId)
            .map(this::view)
            .orElseGet(ChatPlanState::normal);
    }

    public String runtimeInstructions(String conversationId) {
        return planRepository.find(conversationId)
            .map(this::runtimeInstructions)
            .orElse("");
    }

    public String approvalMarkdown(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        return plan.hasSavedPlan() ? approvalMarkdown(plan) : null;
    }

    public String approvalMarkdown(ExecutionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(planTitle(plan)).append("\n\n");
        appendMarkdownValue(builder, "Goal", plan.goal());
        appendMarkdownValue(builder, "Summary", plan.summary());
        appendMarkdownList(builder, "Deliverables", effectiveDeliverables(plan));
        appendMarkdownList(builder, "Inputs", plan.inputs());
        appendMarkdownList(builder, "Assumptions", plan.assumptions());
        appendMarkdownValue(builder, "Notes", plan.notes());
        if (!plan.steps().isEmpty()) {
            builder.append("## Execution Steps\n\n");
            for (PlanStep step : plan.steps()) {
                builder.append(step.order()).append(". ").append(step.text()).append("\n");
            }
            builder.append("\n");
        }
        appendMarkdownList(builder, "Validation Criteria", plan.acceptanceCriteria());
        return builder.toString().trim();
    }

    private String runtimeInstructions(ExecutionPlan plan) {
        if (plan.mode() == PlanMode.PLAN) {
            return planningInstructions(plan);
        }
        if (plan.mode() == PlanMode.EXECUTE_PLAN && plan.hasSavedPlan()) {
            return executionInstructions(plan);
        }
        return "";
    }

    private String planningInstructions(ExecutionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
You are Magenta in PLAN mode.

Your job is to turn the user's intent into a clear, approved execution plan. Do not perform implementation work in PLAN mode.

Required workflow:
1. Ask the user to describe their goal via plan_ask_questions. Do NOT call plan_set_goal until the user has told you what they want.
2. After the user responds, set the goal with plan_set_goal and define concrete deliverables with plan_put_item using integer keys.
3. Ask the user to describe the task and any relevant information: preferred approaches, workflow expectations, constraints, gotchas, and known details via plan_ask_questions. Iteratively explore the problem space — start with broad domain questions, then use follow-up questions to drill into specifics as the plan takes shape.
4. After the user responds, build a structured approach: use plan_put_item with integer keys to add steps, assumptions, notes, and validation criteria. Iteratively move through the plan's problem space, using plan_ask_questions to ask domain-specific questions that clarify ambiguities, surface information needs, or narrow approach choices. Formulate each step with associated assumptions, notes, and validation criteria.
5. When the draft is complete, call plan_ready_for_approval. Do not send a normal message asking for approval, and do not claim approval until the user approves through the planning UI.

Turn contract:
- Stay self-iterating while useful planning work remains available: call as many read-only tools, research tools, and keyed planning edit tools as needed before relinquishing control to the user.
- Every PLAN-mode assistant turn that relinquishes control to the user must move planning forward by ending in one of these states:
  - one specific queued planning question through plan_ask_questions,
  - a group of individual questions through plan_ask_questions (each question string must be a single, atomic question — do not pack multiple numbered questions into one string),
  - a complete draft marked with plan_ready_for_approval.
- Each user-visible message must be the result of queued questions or approval-ready state. Do not end with free-form planning discussion.
- Do not end a PLAN-mode turn with only a conversational summary, analysis, or draft text.
- If the draft is not ready for approval, ask the next concrete planning question instead of inventing preferences or silently locking assumptions.
- A plan is not ready for approval until user-facing intent and tradeoffs have either been answered by the user or are explicitly confirmed as assumptions for approval.
- Once the draft is ready, call plan_ready_for_approval instead of messaging the user directly.

Tool rules:
- Use plan_set_goal for the goal only AFTER the user has described their goal. Use plan_set_task to update the current planning task when moving between workflow phases.
- Use plan_put_item with section and integer key to add or replace exactly one item in any section: deliverable, input, output, assumption, note, step, or validation_criterion. All plan fields use this same integer-key API.
- Use plan_delete_item with section and integer key to remove one keyed item.
- Use assumptions for explicit defaults or choices being locked into the plan.
- Inputs are optional and only for values a future reusable task would require at execution time.
- Outputs are expected model/work products; they are rendered as deliverables for users.
- Research gate: before keyed edits set or revise fact-dependent deliverables, steps, notes, or validation criteria, use available research tools first.
- Use plan_ask_questions with 1 to 5 free-response questions. Each question must be a single, distinct question. Do not bundle multiple numbered sub-questions into one question string — split them into separate questions so the UI can show them one at a time. Prefer one focused question when that is enough.
- Use plan_ready_for_approval only after goal, deliverables/outputs, steps, assumptions, and validation criteria are complete enough to execute without guessing.
- Shell and file tools are allowed for planning research only.
- Strive for clarity, detailed specification, and robust implementation/execution steps.

Runtime planning state:
Mode: PLAN
Status: """).append(" ").append(plan.status().name()).append("\n");
        appendValue(builder, "Current planning task", plan.planningTask());
        appendPlanState(builder, plan);
        if (plan.hasPendingQuestion()) {
            appendValue(builder, "Pending question", plan.currentQuestion());
            builder.append("Pending question progress: ")
                .append(plan.pendingQuestionIndex() + 1)
                .append("/")
                .append(plan.pendingQuestions().size())
                .append("\n");
        }
        return builder.toString().trim();
    }

    private String executionInstructions(ExecutionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("""
You are Magenta executing an approved plan in a fresh chat context.

Work through the plan directly. Track each validation criterion explicitly as you work — verify it is met with specific, verifiable evidence (counts, file contents read back, query results, checks performed).

During execution, call plan_report periodically to save evidence incrementally. This protects evidence from context compaction.

When ready to complete, call plan_complete with:
- One evidence entry per validation criterion from the approved plan above, formatted as 'Criterion: <exact criterion text> | Evidence: <specific proof>'
- Artifact paths for any files created — the validator will auto-read these files, so you don't need to duplicate their contents in the evidence entries
- Deviations from the plan, if any
- Unmet criteria, if any
- finalMessage: your intended user-facing completion message, delivered verbatim to the user after validation passes. Include a concise work summary, the outcome for each deliverable, and any notable results. If the deliverable IS a chat message (a written report, drafted content, etc.), this IS the deliverable — write the complete user-facing text here.

Magenta will run a validator pass; if validation fails, use the returned remediation details and continue work before trying plan_complete again.

Approved plan:

""").append(approvalMarkdown(plan)).append("\n\n");
        appendList(builder, "Prior validation feedback to address", plan.validationFeedback());
        return builder.toString().trim();
    }

    private void appendPlanState(StringBuilder builder, ExecutionPlan plan) {
        appendValue(builder, "Goal", plan.goal());
        appendValue(builder, "Title", plan.title());
        appendValue(builder, "Summary", plan.summary());
        appendList(builder, "Deliverables", plan.deliverables());
        appendList(builder, "Inputs", plan.inputs());
        appendList(builder, "Outputs", plan.outputs());
        appendList(builder, "Assumptions", plan.assumptions());
        appendValue(builder, "Notes", plan.notes());
        appendSteps(builder, plan.steps());
        appendList(builder, "Validation criteria", plan.acceptanceCriteria());
        appendList(builder, "Validation feedback", plan.validationFeedback());
    }

    private ChatPlanState view(ExecutionPlan plan) {
        String approvalMarkdown = plan.status() == PlanStatus.READY_FOR_APPROVAL ? approvalMarkdown(plan) : null;
        return new ChatPlanState(
            plan.mode().name(),
            plan.status().name(),
            plan.planningTask(),
            plan.title(),
            plan.summary(),
            plan.goal(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps().stream().map(PlanStep::text).toList(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.hasPendingQuestion() ? "questions" : null,
            plan.currentQuestion(),
            List.of(),
            plan.hasPendingQuestion() ? plan.pendingQuestionIndex() + 1 : 0,
            plan.hasPendingQuestion() ? plan.pendingQuestions().size() : 0,
            approvalMarkdown,
            approvalMarkdown == null ? null : markdownRenderer.render(approvalMarkdown)
        );
    }

    private ExecutionPlan requireSavedPlan(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        if (!plan.hasSavedPlan()) {
            throw new IllegalStateException("No saved plan exists for this conversation");
        }
        return plan;
    }

    private ExecutionPlan requirePlanMode(String conversationId, String action) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        if (plan.mode() != PlanMode.PLAN) {
            throw new IllegalStateException(action + " is available only in plan mode");
        }
        return plan;
    }

    private ExecutionPlan requirePlanConversation(String conversationId) {
        return planRepository.find(conversationId)
            .orElseThrow(() -> new IllegalStateException("No plan exists for this conversation"));
    }

    private ExecutionPlan copyWith(
        ExecutionPlan plan,
        PlanMode mode,
        PlanStatus status,
        List<String> pendingQuestions,
        int pendingQuestionIndex
    ) {
        return new ExecutionPlan(
            plan.conversationId(),
            mode,
            status,
            status == PlanStatus.READY_FOR_APPROVAL ? "approval" : plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            pendingQuestions,
            pendingQuestionIndex,
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        );
    }

    private ExecutionPlan withExecutionEvidence(ExecutionPlan plan, List<String> executionEvidence) {
        return new ExecutionPlan(
            plan.conversationId(),
            plan.mode(),
            plan.status(),
            plan.planningTask(),
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            executionEvidence == null ? List.of() : List.copyOf(executionEvidence),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            plan.pendingQuestions(),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        );
    }

    private ExecutionPlan saveWithPlanningTask(ExecutionPlan plan, String planningTask) {
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            plan.mode(),
            plan.status(),
            planningTask,
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.deliverables(),
            plan.inputs(),
            plan.outputs(),
            plan.assumptions(),
            plan.steps(),
            plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            plan.pendingQuestions(),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        ));
    }

    private ExecutionPlan saveWithSection(
        ExecutionPlan plan,
        PlanSection section,
        int key,
        String text,
        boolean delete
    ) {
        return planRepository.save(new ExecutionPlan(
            plan.conversationId(),
            plan.mode(),
            plan.status(),
            nextTaskAfterSection(plan, section),
            plan.goal(),
            plan.title(),
            plan.summary(),
            section == PlanSection.NOTE ? keyedNoteText(plan.notes(), key, text, delete) : plan.notes(),
            section == PlanSection.DELIVERABLE ? keyedList(plan.deliverables(), key, text, delete) : plan.deliverables(),
            section == PlanSection.INPUT ? keyedList(plan.inputs(), key, text, delete) : plan.inputs(),
            section == PlanSection.OUTPUT ? keyedList(plan.outputs(), key, text, delete) : plan.outputs(),
            section == PlanSection.ASSUMPTION ? keyedList(plan.assumptions(), key, text, delete) : plan.assumptions(),
            section == PlanSection.STEP ? keyedSteps(plan.steps(), key, text, delete) : plan.steps(),
            section == PlanSection.VALIDATION_CRITERION
                ? keyedList(plan.acceptanceCriteria(), key, text, delete)
                : plan.acceptanceCriteria(),
            plan.executionEvidence(),
            plan.validationFeedback(),
            plan.prePlanningModel(),
            plan.executionModel(),
            plan.pendingQuestions(),
            plan.pendingQuestionIndex(),
            plan.planStartMessageOrder(),
            plan.finalMessage(),
            plan.createdAt(),
            Instant.now()
        ));
    }

    private int requirePositiveKey(Integer key, String toolName) {
        if (key == null || key < 1) {
            throw new IllegalArgumentException(toolName + " requires a positive integer key");
        }
        return key;
    }

    private List<String> keyedList(List<String> values, int key, String text, boolean delete) {
        List<String> updated = new ArrayList<>(values == null ? List.of() : values);
        int index = key - 1;
        if (delete) {
            if (index < updated.size()) {
                updated.remove(index);
            }
            return List.copyOf(updated);
        }
        while (updated.size() < index) {
            updated.add("");
        }
        if (index < updated.size()) {
            updated.set(index, text);
        } else {
            updated.add(text);
        }
        return updated.stream()
            .map(this::normalize)
            .filter(value -> value != null)
            .toList();
    }

    private List<PlanStep> keyedSteps(List<PlanStep> steps, int key, String text, boolean delete) {
        List<PlanStep> updated = new ArrayList<>(steps == null ? List.of() : steps);
        updated.removeIf(step -> step.order() == key);
        if (!delete) {
            updated.add(new PlanStep(key, text));
        }
        return updated.stream()
            .sorted(java.util.Comparator.comparingInt(PlanStep::order))
            .toList();
    }

    private String keyedNoteText(String notes, int key, String text, boolean delete) {
        List<String> updated = keyedList(noteLines(notes), key, text, delete);
        return updated.isEmpty() ? null : String.join("\n", updated);
    }

    private List<String> noteLines(String notes) {
        if (!StringUtils.hasText(notes)) {
            return List.of();
        }
        return notes.lines()
            .map(this::normalize)
            .filter(value -> value != null)
            .toList();
    }

    private String nextTaskAfterGoal(ExecutionPlan plan) {
        return plan.deliverables().isEmpty() && plan.outputs().isEmpty()
            ? "define_deliverables"
            : "collect_user_guidance";
    }

    private String nextTaskAfterSection(ExecutionPlan plan, PlanSection section) {
        if (section == PlanSection.DELIVERABLE || section == PlanSection.OUTPUT) {
            return "collect_user_guidance";
        }
        if (section == PlanSection.ASSUMPTION || section == PlanSection.NOTE || section == PlanSection.INPUT) {
            return "clarify_and_elaborate";
        }
        if (section == PlanSection.STEP) {
            return "build_plan_steps";
        }
        if (section == PlanSection.VALIDATION_CRITERION) {
            return "approval_readiness";
        }
        return plan.planningTask();
    }

    private String titleFromGoal(String goal) {
        String compact = goal.trim().replaceAll("\\s+", " ");
        if (compact.length() <= 64) {
            return "Plan for " + compact;
        }
        return "Plan for " + compact.substring(0, 64).replaceAll("\\s+\\S*$", "").trim();
    }

    private String planTitle(ExecutionPlan plan) {
        if (StringUtils.hasText(plan.title())) {
            return plan.title();
        }
        if (StringUtils.hasText(plan.goal())) {
            return titleFromGoal(plan.goal());
        }
        return "New Plan";
    }

    private void validateComplete(ExecutionPlan plan, String action) {
        if (!StringUtils.hasText(plan.goal())) {
            throw new IllegalArgumentException(action + " requires a goal");
        }
        if (effectiveDeliverables(plan).isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one deliverable or output");
        }
        if (plan.steps().isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one step");
        }
        if (plan.acceptanceCriteria().isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one validation criterion");
        }
    }

    private void appendPlanningAnswer(String conversationId, String question, String answer, String notes) {
        StringBuilder message = new StringBuilder();
        message.append("Planning answer\n\n");
        message.append("Question: ").append(question).append("\n\n");
        if (StringUtils.hasText(answer)) {
            message.append("Answer: ").append(answer.trim()).append("\n");
        }
        if (StringUtils.hasText(notes)) {
            message.append("Notes: ").append(notes.trim()).append("\n");
        }
        List<Message> messages = new ArrayList<>(chatMemoryRepository.findByConversationId(conversationId));
        messages.add(new UserMessage(message.toString().trim()));
        chatMemoryRepository.saveAll(conversationId, messages);
    }

    private List<String> effectiveDeliverables(ExecutionPlan plan) {
        List<String> values = new ArrayList<>();
        values.addAll(plan.deliverables());
        for (String output : plan.outputs()) {
            if (!values.contains(output)) {
                values.add(output);
            }
        }
        return List.copyOf(values);
    }

    private List<PlanStep> orderedSteps(List<String> steps) {
        List<PlanStep> orderedSteps = new ArrayList<>();
        for (String rawStep : steps == null ? List.<String>of() : steps) {
            String step = normalize(rawStep);
            if (step != null) {
                orderedSteps.add(new PlanStep(orderedSteps.size() + 1, step));
            }
        }
        return List.copyOf(orderedSteps);
    }

    private String choose(String value, String fallback) {
        String normalized = normalize(value);
        return normalized == null ? fallback : normalized;
    }

    private String mergeNotes(String existing, String addition) {
        String cleanExisting = normalize(existing);
        String cleanAddition = normalize(addition);
        if (cleanExisting == null) {
            return cleanAddition;
        }
        if (cleanAddition == null || cleanExisting.contains(cleanAddition)) {
            return cleanExisting;
        }
        return cleanExisting + "\n" + cleanAddition;
    }

    private void addLabeled(List<String> entries, String label, String value) {
        if (StringUtils.hasText(value)) {
            entries.add(label + ": " + value);
        }
    }

    private void addLabeled(List<String> entries, String label, List<String> values) {
        for (String value : values) {
            entries.add(label + ": " + value);
        }
    }

    private void trimConversation(String conversationId, int messageCount) {
        List<Message> messages = chatMemoryRepository.findByConversationId(conversationId);
        int end = Math.max(0, Math.min(messageCount, messages.size()));
        chatMemoryRepository.saveAll(conversationId, new ArrayList<>(messages.subList(0, end)));
    }

    private List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .map(this::normalize)
            .filter(value -> value != null)
            .toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void appendValue(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        for (String value : values) {
            builder.append("- ").append(value).append("\n");
        }
    }

    private void appendSteps(StringBuilder builder, List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return;
        }
        builder.append("Steps:\n");
        for (PlanStep step : steps) {
            builder.append(step.order()).append(". ").append(step.text()).append("\n");
        }
    }

    private void appendMarkdownValue(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append("## ").append(label).append("\n\n").append(value.trim()).append("\n\n");
        }
    }

    private void appendMarkdownList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append("## ").append(label).append("\n\n");
        for (String value : values) {
            builder.append("- ").append(value).append("\n");
        }
        builder.append("\n");
    }
}
