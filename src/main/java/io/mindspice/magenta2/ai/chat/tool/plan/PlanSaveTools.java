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
        List<String> assumptions
    ) {
        PlanToolContext context = PlanToolExecutionContext.current();
        if (context == null || context.mode() != PlanMode.PLAN) {
            throw new IllegalStateException("plan_save is available only in plan mode");
        }
        ExecutionPlan plan = planService.saveDraftPlan(context.conversationId(), goal, title, summary, notes, steps, assumptions);
        return "Saved plan: " + plan.title();
    }
}
