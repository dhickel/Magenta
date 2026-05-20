package io.mindspice.magenta2.ai.chat.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SavedPlanChatServiceTest {
    @Test
    void createSeedsNamedTaskTemplateDraft() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        SavedPlanChatService service = new SavedPlanChatService(planService, new PlanChatRepository(jdbcTemplate));

        SavedPlanChatService.SavedPlanChatState state = service.create("Named chat draft");

        assertThat(state.plan().kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(state.plan().title()).isEqualTo("Named chat draft");
        assertThat(state.plan().planningTask()).isEqualTo("saved_plan_opening_questions");
        assertThat(state.promptQuestion()).isEqualTo("What is the goal?");
    }

    @Test
    void savedPlanChatSeedsQuestionsAndWritesTypedInputsOutputs() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        SavedPlanChatService service = new SavedPlanChatService(planService, new PlanChatRepository(jdbcTemplate));

        SavedPlanChatService.SavedPlanChatState state = service.create();
        String planId = state.plan().id();

        assertThat(state.promptQuestion()).isEqualTo("What is the goal?");
        state = service.answer(planId, "Prepare a weekly operations report.");
        assertThat(state.promptQuestion()).contains("runtime inputs");
        state = service.answer(planId, "week_start: string required\nsource_file: file_path optional");
        assertThat(state.promptQuestion()).contains("high-level deliverables");
        state = service.answer(planId, "Markdown report\nUpdated management summary");
        assertThat(state.promptQuestion()).contains("structured outputs");
        state = service.answer(planId, "report_markdown: string required\nmetrics_json: json required");

        PlanDefinition plan = planService.getTask(planId);
        assertThat(plan.kind()).isEqualTo(PlanKind.TASK_TEMPLATE);
        assertThat(plan.title()).isEqualTo("New Plan Chat");
        assertThat(plan.goal()).isEqualTo("Prepare a weekly operations report.");
        assertThat(plan.inputs()).extracting(PlanFieldDefinition::name)
            .containsExactly("week_start", "source_file");
        assertThat(plan.outputs()).extracting(PlanFieldDefinition::name)
            .containsExactly("report_markdown", "metrics_json");
        assertThat(plan.deliverables()).containsExactly("Markdown report", "Updated management summary");
        assertThat(service.state(planId).messages()).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void startWithExistingDraftHistoryAppendsInstructionAndResumeQuestion() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        SavedPlanChatService service = new SavedPlanChatService(planService, new PlanChatRepository(jdbcTemplate));

        SavedPlanChatService.SavedPlanChatState state = service.create();
        String planId = state.plan().id();
        service.answer(planId, "Prepare weekly report");

        SavedPlanChatService.SavedPlanChatState resumed = service.start(
            planId,
            "goal updated; outputs updated"
        );

        List<PlanChatMessage> messages = resumed.messages();
        assertThat(messages.get(messages.size() - 2).role()).isEqualTo("user");
        assertThat(messages.get(messages.size() - 2).text())
            .isEqualTo("goal updated; outputs updated");
        assertThat(messages.get(messages.size() - 1).role()).isEqualTo("assistant");
        assertThat(messages.get(messages.size() - 1).text())
            .isEqualTo("Any details you want to provide before continuing?");
        assertThat(resumed.promptQuestion()).isEqualTo("Any details you want to provide before continuing?");
        assertThat(resumed.plan().planningTask()).isEqualTo("saved_plan_resume_chat");
    }

    @Test
    void startWithApprovedHistoryUsesChangeRequestQuestion() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        SavedPlanChatService service = new SavedPlanChatService(planService, new PlanChatRepository(jdbcTemplate));

        PlanDefinition approved = planService.saveTask(new PlanDefinition(
            "approved-plan", PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Approved", null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), WorkTypeProfile.CODING_CENTRIC.name(),
            null, null, null, null,
            List.of(), 0, 0, null, null, null, null
        ));
        service.start(approved.id(), null);
        planService.saveTask(planService.getTask(approved.id()).withStatus(PlanStatus.APPROVED));

        SavedPlanChatService.SavedPlanChatState state = service.start(approved.id(), null);

        assertThat(state.promptQuestion()).isEqualTo("What do you need to change in this plan?");
        assertThat(state.messages().getLast().text()).isEqualTo("What do you need to change in this plan?");
    }

    @Test
    void appendEditorSaveContextWritesConciseDiffWhenHistoryExists() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        PlanChatRepository chatRepository = new PlanChatRepository(jdbcTemplate);
        SavedPlanChatService service = new SavedPlanChatService(planService, chatRepository);

        SavedPlanChatService.SavedPlanChatState state = service.create("Context plan");
        PlanDefinition before = state.plan();
        PlanDefinition after = before.withGoal("Updated goal").withSummary("Updated summary");

        service.appendEditorSaveContext(before, after);

        List<PlanChatMessage> messages = chatRepository.findByPlanId(before.id());
        assertThat(messages.getLast().role()).isEqualTo("user");
        assertThat(messages.getLast().text())
            .contains("Saved editor updates:")
            .contains("goal changed")
            .contains("summary changed");
    }

    @Test
    void startDraftWithoutHistorySeedsDraftResumeQuestionDeterministically() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanRepository planRepository = new PlanRepository(jdbcTemplate, new ObjectMapper());
        PlanService planService = new PlanService(
            planRepository,
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        SavedPlanChatService service = new SavedPlanChatService(planService, new PlanChatRepository(jdbcTemplate));

        PlanDefinition draft = planService.saveTask(new PlanDefinition(
            "existing-plan", PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Draft", null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), WorkTypeProfile.CODING_CENTRIC.name(),
            null, null, null, null,
            List.of(), 0, 0, null, null, null, null
        ));

        SavedPlanChatService.SavedPlanChatState state = service.start(draft.id(), null);

        assertThat(state.promptQuestion()).isEqualTo("Any details you want to provide before continuing?");
        assertThat(state.messages()).hasSize(1);
        assertThat(state.messages().getFirst().text()).isEqualTo("Any details you want to provide before continuing?");
    }

    private JdbcTemplate jdbcTemplate() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
