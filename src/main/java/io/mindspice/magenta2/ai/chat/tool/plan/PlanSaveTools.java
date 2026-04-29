package io.mindspice.magenta2.ai.chat.tool.plan;

import java.util.List;

import io.mindspice.magenta2.ai.chat.plan.ExecutionPlan;
import io.mindspice.magenta2.ai.chat.plan.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
public class PlanSaveTools {
    private final PlanService planService;

    public PlanSaveTools(PlanService planService) {
        this.planService = planService;
    }

    @Tool(
        name = "plan_save",
        description = "Save or replace the current draft execution plan. Use this only when plan mode has a complete plan ready."
    )
    public String save(
        @ToolParam(description = "The clarified user goal this plan is intended to accomplish.")
        String goal,
        @ToolParam(description = "Short title for the plan.")
        String title,
        @ToolParam(required = false, description = "One or two sentence summary of the intended work.")
        String summary,
        @ToolParam(required = false, description = "Extra planning notes or vital details that are not execution steps.")
        String notes,
        @ToolParam(description = "Ordered execution steps. Each step should be concise and actionable.")
        List<String> steps,
        @ToolParam(required = false, description = "Important assumptions or defaults chosen for the plan.")
        List<String> assumptions,
        @ToolParam(required = false, description = "Measurable criteria for judging whether execution succeeded, such as required counts, files, validation checks, or output quality bars.")
        List<String> acceptanceCriteria
    ) {
        PlanToolContext context = PlanToolExecutionContext.current();
        if (context == null || context.mode() != PlanMode.PLAN) {
            throw new IllegalStateException("plan_save is available only in plan mode");
        }
        ExecutionPlan plan = planService.saveDraftPlan(
            context.conversationId(),
            goal,
            title,
            summary,
            notes,
            steps,
            assumptions,
            acceptanceCriteria
        );
        return "Saved plan: " + plan.title();
    }

    @Tool(
        name = "plan_report",
        description = "Record compact execution evidence for the active saved plan. Use this before the final answer while executing a plan."
    )
    public String report(
        @ToolParam(required = false, description = "Brief execution result summary.")
        String summary,
        @ToolParam(required = false, description = "Evidence that supports the result, including actual counts, source queries, source ids, or files read back.")
        List<String> evidence,
        @ToolParam(required = false, description = "Any deviations from the saved plan or acceptance criteria.")
        List<String> deviations,
        @ToolParam(required = false, description = "Acceptance criteria that were not met.")
        List<String> unmetCriteria,
        @ToolParam(required = false, description = "Artifacts created or used during execution, such as reports, scripts, or evidence files.")
        List<String> artifactPaths
    ) {
        PlanToolContext context = PlanToolExecutionContext.current();
        if (context == null || context.mode() != PlanMode.EXECUTE_PLAN) {
            throw new IllegalStateException("plan_report is available only while executing a saved plan");
        }
        ExecutionPlan plan = planService.recordExecutionReport(
            context.conversationId(),
            summary,
            evidence,
            deviations,
            unmetCriteria,
            artifactPaths
        );
        return "Recorded execution evidence for plan: " + plan.title();
    }
}
