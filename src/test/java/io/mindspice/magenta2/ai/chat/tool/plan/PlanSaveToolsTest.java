package io.mindspice.magenta2.ai.chat.tool.plan;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import io.mindspice.magenta2.ai.chat.plan.ChatPlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSaveToolsTest {

    @Test
    void savesPlanOnlyWhenPlanContextIsActive() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService service = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        service.beginPlan("conversation-1");
        PlanSaveTools tools = new PlanSaveTools(service);

        PlanToolExecutionContext.set(new PlanToolContext("conversation-1", PlanMode.PLAN));
        try {
            assertThat(tools.save(
                "Clarified goal",
                "Plan",
                "Summary",
                "Important note",
                List.of("Step"),
                List.of(),
                List.of("Show evidence")
            ))
                .isEqualTo("Saved plan: Plan");
        } finally {
            PlanToolExecutionContext.clear();
        }

        assertThat(service.activePlan("conversation-1").orElseThrow().title()).isEqualTo("Plan");
        assertThat(service.activePlan("conversation-1").orElseThrow().goal()).isEqualTo("Clarified goal");
        assertThat(service.activePlan("conversation-1").orElseThrow().notes()).isEqualTo("Important note");
        assertThat(service.activePlan("conversation-1").orElseThrow().acceptanceCriteria()).containsExactly("Show evidence");
        assertThatThrownBy(() -> tools.save("Clarified goal", "Plan", null, null, List.of("Step"), List.of(), List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("plan mode");
    }

    @Test
    void reportsOnlyWhileExecutingPlan() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService service = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        service.beginPlan("conversation-1");
        service.saveDraftPlan("conversation-1", "Goal", "Plan", null, null, List.of("Step"), List.of(), List.of());
        service.markExecuting("conversation-1");
        PlanSaveTools tools = new PlanSaveTools(service);

        PlanToolExecutionContext.set(new PlanToolContext("conversation-1", PlanMode.EXECUTE_PLAN));
        try {
            assertThat(tools.report(
                "Done",
                List.of("Actual count: 1"),
                List.of(),
                List.of(),
                List.of("report.md")
            )).isEqualTo("Recorded execution evidence for plan: Plan");
        } finally {
            PlanToolExecutionContext.clear();
        }

        assertThat(service.activePlan("conversation-1").orElseThrow().executionEvidence())
            .contains("Summary: Done")
            .contains("Artifact: report.md");
        assertThatThrownBy(() -> tools.report(null, List.of(), List.of(), List.of(), List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("executing");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
