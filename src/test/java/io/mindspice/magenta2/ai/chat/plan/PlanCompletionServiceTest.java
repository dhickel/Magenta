package io.mindspice.magenta2.ai.chat.plan;

import java.util.List;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class PlanCompletionServiceTest {

    @Test
    void missingCriterionResultsFailBeforeValidatorCall() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        PlanService planService = new PlanService(
            new ChatPlanRepository(jdbcTemplate, objectMapper),
            new ChatMemoryRepository(jdbcTemplate, objectMapper)
        );
        planService.beginPlan("conversation-1", "qwen3", "qwen3");
        planService.saveDraftPlan(
            "conversation-1",
            "Goal",
            "Plan",
            "Summary",
            null,
            List.of("Do the work."),
            List.of(),
            List.of("Validate alpha", "Validate beta")
        );
        planService.markExecuting("conversation-1");
        PlanCompletionService completionService = new PlanCompletionService(
            planService,
            null,
            null,
            objectMapper
        );

        String response = completionService.complete(
            "conversation-1",
            "Done",
            List.of("Criterion: Validate alpha | Evidence: checked alpha"),
            List.of(),
            List.of(),
            List.of(),
            null
        );

        ExecutionPlan plan = planService.activePlan("conversation-1").orElseThrow();
        assertThat(response)
            .contains("Plan validation failed")
            .contains("Missing evidence for: Validate beta");
        assertThat(plan.mode()).isEqualTo(PlanMode.EXECUTE_PLAN);
        assertThat(plan.status()).isEqualTo(PlanStatus.EXECUTING);
        assertThat(plan.validationFeedback())
            .contains("Finding: Missing evidence for: Validate beta");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
