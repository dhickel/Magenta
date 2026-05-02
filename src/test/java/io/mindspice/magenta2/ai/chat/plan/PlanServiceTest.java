package io.mindspice.magenta2.ai.chat.plan;

import java.util.List;

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
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

        service.beginPlan("conversation-1");

        assertThat(service.runtimeInstructions("conversation-1"))
            .contains("You are Magenta in PLAN mode")
            .contains("Ask the user to describe their goal")
            .contains("define concrete deliverables")
            .contains("Stay self-iterating while useful planning work remains available")
            .contains("Every PLAN-mode assistant turn that relinquishes control to the user")
            .contains("one specific queued planning question")
            .contains("a queued series of planning questions")
            .contains("Do not end a PLAN-mode turn with only a conversational summary")
            .contains("Do not end with free-form planning discussion")
            .contains("ask the next concrete planning question instead of inventing preferences")
            .contains("call plan_ready_for_approval instead of messaging the user directly")
            .contains("Strive for clarity, detailed specification, and robust implementation/execution steps")
            .contains("Do not perform implementation work in PLAN mode")
            .contains("Shell and file tools are allowed for planning research")
            .contains("plan_set_goal")
            .contains("plan_put_item")
            .contains("plan_ask_questions")
            .contains("plan_ready_for_approval")
            .contains("validation criteria")
            .contains("notes");
    }

    @Test
    void executionReportPersistsEvidenceAndNeedsReviewState() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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

        ExecutionPlan plan = service.activePlan("conversation-1").orElseThrow();
        assertThat(plan.status()).isEqualTo(PlanStatus.NEEDS_REVIEW);
        assertThat(plan.executionEvidence())
            .contains("Evidence: Actual posts: 40")
            .contains("Unmet criterion: Sample at least 50 posts");
    }

    @Test
    void readyForApprovalExposesTransientMarkdownPlan() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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
        assertThat(service.view("conversation-1").approvalHtml())
            .contains("<h1>GPU Plan</h1>")
            .contains("<h2>Goal</h2>")
            .contains("<li>Markdown report</li>");
    }

    @Test
    void queuedPlanningQuestionsAdvanceAndPersistAnswersAsChatMessages() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

        service.beginPlan("conversation-1");

        assertThatThrownBy(() -> service.askQuestions(
            "conversation-1",
            List.of("One?", "Two?", "Three?", "Four?", "Five?", "Six?")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at most five");
    }

    @Test
    void approvalMarkdownRendersOptionalInputsOnlyWhenPresent() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, new ObjectMapper());
        PlanService service = new PlanService(new ChatPlanRepository(jdbcTemplate, new ObjectMapper()), memoryRepository);

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
            .contains("## Deliverables")
            .contains("- Updated file")
            .contains("## Inputs")
            .contains("- target_file");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
