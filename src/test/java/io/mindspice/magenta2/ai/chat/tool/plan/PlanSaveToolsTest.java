package io.mindspice.magenta2.ai.chat.tool.plan;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import io.mindspice.magenta2.ai.chat.plan.ChatPlanRepository;
import io.mindspice.magenta2.ai.chat.plan.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;
import io.mindspice.magenta2.ai.chat.repository.SQLiteChatMemoryRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSaveToolsTest {

    @Test
    void savesPlanOnlyWhenPlanContextIsActive() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService service = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new SQLiteChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        service.beginPlan("conversation-1", "Goal");
        PlanSaveTools tools = new PlanSaveTools(service);

        PlanToolExecutionContext.set(new PlanToolContext("conversation-1", PlanMode.PLAN));
        try {
            assertThat(tools.save("Plan", "Summary", List.of("Step"), List.of()))
                .isEqualTo("Saved plan: Plan");
        } finally {
            PlanToolExecutionContext.clear();
        }

        assertThat(service.activePlan("conversation-1").orElseThrow().title()).isEqualTo("Plan");
        assertThatThrownBy(() -> tools.save("Plan", null, List.of("Step"), List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("plan mode");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
