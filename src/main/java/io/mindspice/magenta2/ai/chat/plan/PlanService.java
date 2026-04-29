package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlanService {
    private final ChatPlanRepository planRepository;
    private final ChatMemoryRepository chatMemoryRepository;

    public PlanService(ChatPlanRepository planRepository, ChatMemoryRepository chatMemoryRepository) {
        this.planRepository = planRepository;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public ExecutionPlan beginPlan(String conversationId) {
        int startOrder = chatMemoryRepository.findByConversationId(conversationId).size();
        Instant now = Instant.now();
        ExecutionPlan existing = planRepository.find(conversationId).orElse(null);
        return planRepository.save(new ExecutionPlan(
            conversationId,
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            startOrder,
            existing == null ? now : existing.createdAt(),
            now
        ));
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

    public ExecutionPlan saveDraftPlan(
        String conversationId,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> steps,
        List<String> assumptions
    ) {
        ExecutionPlan existing = requirePlanConversation(conversationId);
        if (existing.mode() != PlanMode.PLAN) {
            throw new IllegalStateException("plan_save is available only in plan mode");
        }
        List<PlanStep> orderedSteps = new ArrayList<>();
        List<String> safeSteps = steps == null ? List.of() : steps;
        for (int i = 0; i < safeSteps.size(); i++) {
            String step = normalize(safeSteps.get(i));
            if (step != null) {
                orderedSteps.add(new PlanStep(i + 1, step));
            }
        }
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("plan_save requires a title");
        }
        if (!StringUtils.hasText(goal)) {
            throw new IllegalArgumentException("plan_save requires a goal");
        }
        if (orderedSteps.isEmpty()) {
            throw new IllegalArgumentException("plan_save requires at least one step");
        }
        return planRepository.save(new ExecutionPlan(
            conversationId,
            PlanMode.PLAN,
            PlanStatus.DRAFT,
            goal.trim(),
            title.trim(),
            normalize(summary),
            normalize(notes),
            cleanList(assumptions),
            orderedSteps,
            existing.planStartMessageOrder(),
            existing.createdAt(),
            Instant.now()
        ));
    }

    public ExecutionPlan markExecuting(String conversationId) {
        ExecutionPlan plan = requireSavedPlan(conversationId);
        return planRepository.save(copyWith(plan, PlanMode.EXECUTE_PLAN, PlanStatus.EXECUTING));
    }

    public ExecutionPlan markCompleted(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        return planRepository.save(copyWith(plan, PlanMode.NORMAL, PlanStatus.COMPLETED));
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

    private String runtimeInstructions(ExecutionPlan plan) {
        if (plan.mode() == PlanMode.PLAN) {
            StringBuilder builder = new StringBuilder();
            builder.append("""
You are Magenta in PLAN mode.

Your only job is to help the user turn an intent into a clear execution plan. Do not perform the saved plan's implementation work, do not create final artifacts, and do not claim that implementation is complete.

Tool rules:
- You may use file tools and shell commands to inspect files, query local databases, search the codebase, or gather facts needed for planning.
- Shell access is available in plan mode for planning research, schema inspection, environment checks, and other context-gathering work.
- Keep tool use focused on clarifying the plan. Avoid irreversible or broad side effects unless the user explicitly asks for them during planning.
- Use plan_save only when the plan is complete enough for execution.

Conversation flow:
- Begin by asking the user what goal they want to plan.
- After the user gives the goal, ask whether they have a preferred approach, constraints, or anything they explicitly do or do not want.
- Keep the planning conversation progressive and natural. Restate your understanding, ask targeted clarifying questions, inspect read-only context when that can answer implementation questions, and explain the approach you are considering.
- Ask for corrections whenever your understanding, approach, constraints, or tradeoffs may be off.
- Continue until the goal, approach, constraints, assumptions, risks, and execution steps are clear enough that another model or engineer could execute without guessing.
- When ready, call plan_save with the clarified goal, title, summary, notes, ordered execution steps, and assumptions.
- After saving, tell the user the plan is ready for approval and they can run /exec-plan or /clr-exec-plan.

Runtime state:
Mode: PLAN
""");
            if (StringUtils.hasText(plan.goal())) {
                builder.append("Goal: ").append(plan.goal()).append("\n");
            }
            if (plan.hasSavedPlan()) {
                builder.append("Saved draft: ").append(plan.title()).append("\n");
            }
            return builder.toString().trim();
        }
        if (plan.mode() == PlanMode.EXECUTE_PLAN && plan.hasSavedPlan()) {
            StringBuilder builder = new StringBuilder();
            builder.append("Runtime state:\n")
                .append("Mode: EXECUTE_PLAN\n")
                .append("Plan: ").append(plan.title()).append("\n");
            if (StringUtils.hasText(plan.summary())) {
                builder.append("Summary: ").append(plan.summary()).append("\n");
            }
            if (StringUtils.hasText(plan.notes())) {
                builder.append("Notes: ").append(plan.notes()).append("\n");
            }
            builder.append("Steps:\n");
            for (PlanStep step : plan.steps()) {
                builder.append(step.order()).append(". ").append(step.text()).append("\n");
            }
            if (!plan.assumptions().isEmpty()) {
                builder.append("Assumptions:\n");
                for (String assumption : plan.assumptions()) {
                    builder.append("- ").append(assumption).append("\n");
                }
            }
            return builder.toString().trim();
        }
        return "";
    }

    private ChatPlanState view(ExecutionPlan plan) {
        return new ChatPlanState(
            plan.mode().name(),
            plan.status().name(),
            plan.title(),
            plan.summary(),
            plan.goal(),
            plan.notes(),
            plan.steps().stream().map(PlanStep::text).toList()
        );
    }

    private ExecutionPlan requireSavedPlan(String conversationId) {
        ExecutionPlan plan = requirePlanConversation(conversationId);
        if (!plan.hasSavedPlan()) {
            throw new IllegalStateException("No saved plan exists for this conversation");
        }
        return plan;
    }

    private ExecutionPlan requirePlanConversation(String conversationId) {
        return planRepository.find(conversationId)
            .orElseThrow(() -> new IllegalStateException("No plan exists for this conversation"));
    }

    private ExecutionPlan copyWith(ExecutionPlan plan, PlanMode mode, PlanStatus status) {
        return new ExecutionPlan(
            plan.conversationId(),
            mode,
            status,
            plan.goal(),
            plan.title(),
            plan.summary(),
            plan.notes(),
            plan.assumptions(),
            plan.steps(),
            plan.planStartMessageOrder(),
            plan.createdAt(),
            Instant.now()
        );
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
}
