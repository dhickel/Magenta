package io.mindspice.magenta2.ai.chat.plan;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanServiceTest {

    @Test
    void exitPlanTrimsMessagesCreatedAfterPlanStarted() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before")));
        service.beginPlan("conversation-1");
        memoryRepository.saveAll("conversation-1", List.of(new UserMessage("before"), new UserMessage("during")));

        service.exitPlan("conversation-1");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .containsExactly("before");
        assertThat(service.activePlan("conversation-1")).isEmpty();
    }

    @Test
    void runtimeInstructionsExposeCompactPlanState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.saveDraftPlan(
            "conversation-1",
            "Add plan mode",
            "Plan Mode",
            "Add streamlined planning.",
            "Do not alter existing command names.",
            List.of("Add state", "Inject prompt"),
            List.of("Use slash commands"),
            List.of("Expose evidence")
        );
        service.markExecuting("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("fresh chat context")
            .contains("# Plan Mode")
            .contains("## Deliverables")
            .contains("Do not alter existing command names.")
            .contains("1. Add state")
            .contains("Use slash commands")
            .contains("Validation Criteria")
            .contains("Expose evidence")
            .contains("call plan_complete");
    }

    @Test
    void planModeInstructionsAreStandalonePlanningPrompt() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("You are Magenta in PLAN mode")
            .contains("Ask the user to describe their goal")
            .contains("define concrete deliverables")
            .contains("Stay self-iterating while useful planning work remains available")
            .contains("Every PLAN-mode assistant turn that relinquishes control to the user")
            .contains("plan_set_goal")
            .contains("plan_put_item")
            .contains("ask_user_questions")
            .contains("plan_ready_for_approval")
            .contains("validation criteria");
    }

    @Test
    void executionReportPersistsEvidenceAndNeedsReviewState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.saveDraftPlan(
            "conversation-1",
            "Sample posts",
            "Research Plan",
            "Research.",
            null,
            List.of("Sample"),
            List.of(),
            List.of("Sample at least 50 posts")
        );
        service.markExecuting("conversation-1");
        service.recordExecutionReport(
            "conversation-1",
            "Sampled fewer posts than requested.",
            List.of("Actual posts: 40"),
            List.of("Minimum count missed"),
            List.of("Sample at least 50 posts"),
            List.of("summaries.md")
        );
        service.markNeedsReview("conversation-1");

        PlanDefinition plan = service.activePlan("conversation-1").orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.NEEDS_REVIEW);
        assertThat(plan.executionEvidence())
            .contains("Evidence: Actual posts: 40")
            .contains("Unmet criterion: Sample at least 50 posts");
    }

    @Test
    void readyForApprovalExposesTransientMarkdownPlan() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.updateDraftPlan(
            "conversation-1",
            "Compare GPUs",
            "GPU Plan",
            "Compare options.",
            "Use current benchmarks.",
            List.of("Markdown report"),
            List.of("Research specs", "Write report"),
            List.of("Includes deliverables")
        );
        service.markReadyForApproval("conversation-1");

        assertThat(service.view("conversation-1").approvalMarkdown())
            .contains("# GPU Plan")
            .contains("## Goal")
            .contains("- Markdown report")
            .contains("1. Research specs")
            .contains("## Validation Criteria");
    }

    @Test
    void queuedPlanningQuestionsAdvanceAndPersistAnswersAsChatMessages() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.askQuestions("conversation-1", List.of("What should we build?", "Any constraints?"));

        assertThat(service.view("conversation-1").promptQuestion()).isEqualTo("What should we build?");
        assertThat(service.view("conversation-1").promptQuestionIndex()).isEqualTo(1);
        assertThat(service.view("conversation-1").promptQuestionCount()).isEqualTo(2);

        service.recordPromptAnswer("conversation-1", "A robust planner.", "Keep it small.");

        assertThat(memoryRepository.findByConversationId("conversation-1"))
            .extracting(message -> message.getText())
            .last()
            .satisfies(text -> assertThat(text)
                .contains("Question: What should we build?")
                .contains("Answer: A robust planner.")
                .contains("Notes: Keep it small."));
        assertThat(service.view("conversation-1").promptQuestion()).isEqualTo("Any constraints?");
        assertThat(service.view("conversation-1").promptQuestionIndex()).isEqualTo(2);

        service.recordPromptAnswer("conversation-1", "No extra orchestration.", null);

        assertThat(service.view("conversation-1").promptQuestion()).isNull();
        assertThat(service.view("conversation-1").promptQuestionCount()).isZero();
    }

    @Test
    void queuedPlanningQuestionsRejectMoreThanFive() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");

        assertThatThrownBy(() -> service.askQuestions(
            "conversation-1",
            List.of("One?", "Two?", "Three?", "Four?", "Five?", "Six?")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most five");
    }

    @Test
    void taskTemplateCrudAndDraftWorkflow() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // Begin draft
        PlanDefinition draft = service.beginDraft("draft-conv", "model-a", "model-b");
        assertThat(draft.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(draft.status()).isEqualTo(PlanStatus.DRAFT);
        assertThat(draft.conversationId()).isEqualTo("draft-conv");

        // Set goal
        PlanDefinition withGoal = service.setTaskGoal("draft-conv", "Research a topic.");
        assertThat(withGoal.goal()).isEqualTo("Research a topic.");
        assertThat(withGoal.planningTask()).isEqualTo("define_outputs");

        // Add input
        PlanDefinition withInput = service.putFieldItem("draft-conv", "input", 1,
            new PlanFieldDefinition("topic", PlanFieldType.STRING, false, "Topic to research", true, null, "test"));
        assertThat(withInput.inputs()).extracting(PlanFieldDefinition::name).containsExactly("topic");

        // Add output
        PlanDefinition withOutput = service.putFieldItem("draft-conv", "output", 1,
            new PlanFieldDefinition("notes", PlanFieldType.USER_MESSAGE, false, "Research notes", true, null, null));
        assertThat(withOutput.outputs()).extracting(PlanFieldDefinition::name).containsExactly("notes");

        // Add step
        PlanDefinition withStep = service.putTextItem("draft-conv", "step", 1, "Collect research notes for <topic>.");
        assertThat(withStep.steps()).hasSize(1);

        // Add validation
        service.putTextItem("draft-conv", "validation_criterion", 1, "Notes contain research findings.");

        // Answer question
        service.recordTaskPromptAnswer("draft-conv", "A topic input is fine.", null, 1);

        // Ready for approval - Need title first
        PlanDefinition titled = planRepository.saveDefinition(
            service.activeDraft("draft-conv").orElseThrow()
                .withTitle("Research Task")
                .withSummary("Research a given topic.")
        );

        // Mark ready
        PlanDefinition ready = service.markTaskReadyForApproval("draft-conv");
        assertThat(ready.status()).isEqualTo(PlanStatus.READY_FOR_APPROVAL);

        // Approve
        PlanDefinition task = service.approveDraft("draft-conv");
        assertThat(task.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(task.title()).isEqualTo("Research Task");

        // List tasks
        assertThat(service.listTasks()).hasSize(1);
    }

    @Test
    void taskRunLifecycle() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // Create a finalized task
        PlanDefinition task = service.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Quick Task", "Do it.", "Goal.", null,
            List.of(), List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Result present."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null
        ));

        // No required inputs, start directly
        PlanRun run = service.startRun(task.id(), Map.of("dummy", "val"));
        assertThat(run.status()).isEqualTo(PlanRunStatus.RUNNING);

        // Record report
        PlanRun reported = service.recordRunReport(run.id(), "Working", List.of("evidence"));
        assertThat(reported.executionEvidence()).anyMatch(e -> e.contains("Working"));

        // Complete with missing output
        assertThatThrownBy(() -> service.completeRun(run.id(), Map.of(), "done", List.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("result");

        // Complete
        PlanRun completed = service.completeRun(run.id(), Map.of("result", "ok"), "done", List.of("Evidence: proof"));
        assertThat(completed.status()).isEqualTo(PlanRunStatus.COMPLETED);
        assertThat(completed.outputValues()).containsEntry("result", "ok");
    }

    @Test
    void modeResolutionHandlesAllStates() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        // No plan -> NORMAL
        assertThat(service.mode("no-plan")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.NORMAL);

        // DRAFT plan -> PLAN
        service.beginPlan("conv-1");
        assertThat(service.mode("conv-1")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.PLAN);

        // Task draft -> TASK
        service.beginDraft("task-draft", null, null);
        assertThat(service.mode("task-draft")).isEqualTo(io.mindspice.magenta2.ai.chat.model.PlanMode.TASK);
    }

    @Test
    void approvalMarkdownRendersOptionalInputsOnlyWhenPresent() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(planRepository, memoryRepository);

        service.beginPlan("conversation-1");
        service.updateDraftPlan(
            "conversation-1",
            "Create reusable task",
            "Task Plan",
            "Plan reusable work.",
            null,
            List.of(),
            List.of("target_file"),
            List.of("Updated file"),
            List.of("Input names are placeholders."),
            List.of("Inspect target_file", "Apply scoped edit"),
            List.of("The updated file exists")
        );
        service.markReadyForApproval("conversation-1");

        assertThat(service.view("conversation-1").approvalMarkdown())
            .contains("Task Plan")
            .contains("## Inputs")
            .contains("- target_file")
            .contains("## Outputs")
            .contains("- Updated file");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
