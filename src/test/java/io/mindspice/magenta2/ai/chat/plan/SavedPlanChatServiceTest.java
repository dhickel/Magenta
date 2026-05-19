package io.mindspice.magenta2.ai.chat.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SavedPlanChatServiceTest {
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
        assertThat(plan.goal()).isEqualTo("Prepare a weekly operations report.");
        assertThat(plan.inputs()).extracting(PlanFieldDefinition::name)
            .containsExactly("week_start", "source_file");
        assertThat(plan.outputs()).extracting(PlanFieldDefinition::name)
            .containsExactly("report_markdown", "metrics_json");
        assertThat(plan.deliverables()).containsExactly("Markdown report", "Updated management summary");
        assertThat(service.state(planId).messages()).hasSizeGreaterThanOrEqualTo(8);
    }

    private JdbcTemplate jdbcTemplate() {
        var dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
