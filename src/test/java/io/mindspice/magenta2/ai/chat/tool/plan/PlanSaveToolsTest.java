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
import io.mindspice.magenta2.ai.chat.tool.InteractionQuestionTools;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSaveToolsTest {

    @Test
    void planningToolDescriptionsPreferQuestionsBeforeApproval() throws Exception {
        Tool askQuestions = InteractionQuestionTools.class
            .getMethod("askQuestions", List.class)
            .getAnnotation(Tool.class);
        Tool readyForApproval = PlanSaveTools.class
            .getMethod("readyForApproval")
            .getAnnotation(Tool.class);

        assertThat(askQuestions.description())
            .contains("active plan or task interaction")
            .contains("one at a time with progress");
        assertThat(readyForApproval.description())
            .contains("only after goal, deliverables/outputs, assumptions, detailed steps, and validation criteria")
            .contains("without guessing");
    }

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
            assertThat(tools.update(
                "Clarified goal",
                "Plan",
                "Summary",
                "Important note",
                List.of("Deliverable"),
                List.of("Step"),
                List.of("Show evidence")
            ))
                .isEqualTo("Updated plan draft: Plan");
        } finally {
            PlanToolExecutionContext.clear();
        }

        assertThat(service.activePlan("conversation-1").orElseThrow().title()).isEqualTo("Plan");
        assertThat(service.activePlan("conversation-1").orElseThrow().goal()).isEqualTo("Clarified goal");
        assertThat(service.activePlan("conversation-1").orElseThrow().notes()).isEqualTo("Important note");
        assertThat(service.activePlan("conversation-1").orElseThrow().deliverables()).containsExactly("Deliverable");
        assertThat(service.activePlan("conversation-1").orElseThrow().acceptanceCriteria()).containsExactly("Show evidence");
        assertThatThrownBy(() -> tools.update("Clarified goal", "Plan", null, null, List.of("Deliverable"), List.of("Step"), List.of()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("plan mode");
    }

    @Test
    void keyedPlanningToolsMutateSingleItems() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService service = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        service.beginPlan("conversation-1");
        PlanSaveTools tools = new PlanSaveTools(service);

        PlanToolExecutionContext.set(new PlanToolContext("conversation-1", PlanMode.PLAN));
        try {
            tools.setGoal("Build a robust planner");
            tools.setTask("collect_user_guidance");
            tools.putItem("deliverable", 1, "Implementation plan");
            tools.putItem("step", 2, "Run focused tests and inspect failures before changing code.");
            tools.putItem("step", 1, "Review current planning state and recent changelogs.");
            tools.deleteItem("step", 2);
            tools.putItem("validation_criterion", 1, "Planning ends with a question or approval.");
        } finally {
            PlanToolExecutionContext.clear();
        }

        var plan = service.activePlan("conversation-1").orElseThrow();
        assertThat(plan.goal()).isEqualTo("Build a robust planner");
        assertThat(plan.planningTask()).isEqualTo("approval_readiness");
        assertThat(plan.deliverables()).containsExactly("Implementation plan");
        assertThat(plan.steps()).containsExactly(new io.mindspice.magenta2.ai.chat.plan.PlanStep(
            1,
            "Review current planning state and recent changelogs."
        ));
        assertThat(plan.acceptanceCriteria()).containsExactly("Planning ends with a question or approval.");
    }

    @Test
    void reportsOnlyWhileExecutingPlan() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        PlanService service = new PlanService(
            new ChatPlanRepository(jdbcTemplate, new ObjectMapper()),
            new ChatMemoryRepository(jdbcTemplate, new ObjectMapper())
        );
        service.beginPlan("conversation-1");
        service.saveDraftPlan("conversation-1", "Goal", "Plan", null, null, List.of("Step"), List.of(), List.of("Validate"));
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
            .hasMessageContaining("execute_plan mode");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
