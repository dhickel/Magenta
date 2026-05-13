package io.mindspice.magenta2.ai.chat.tool;

import java.util.List;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanToolContext;
import io.mindspice.magenta2.ai.chat.plan.PlanToolExecutionContext;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InteractionQuestionTools {
    private final PlanService planService;
    private final TaskService taskService;

    @Autowired
    public InteractionQuestionTools(
        @Autowired(required = false) PlanService planService,
        @Autowired(required = false) TaskService taskService
    ) {
        this.planService = planService;
        this.taskService = taskService;
    }

    @Tool(
        name = "ask_user_questions",
        description = "Ask the user one to five free-response questions for the active plan or task interaction. The UI displays them one at a time with progress."
    )
    public String askQuestions(
        @ToolParam(description = "One to five concrete questions for the user.")
        List<String> questions
    ) {
        PlanToolContext context = PlanToolExecutionContext.current();
        if (context == null) {
            throw new IllegalStateException("ask_user_questions requires an active interaction context");
        }
        if (context.mode() == PlanMode.PLAN) {
            if (planService == null) {
                throw new IllegalStateException("Plan service is not available");
            }
            PlanDefinition plan = planService.askQuestions(context.conversationId(), questions);
            return "Queued " + plan.pendingQuestions().size() + " planning question(s) for the user.";
        }
        if (context.mode() == PlanMode.TASK) {
            if (taskService == null) {
                throw new IllegalStateException("Task service is not available");
            }
            planService.askTaskQuestions(context.conversationId(), questions);
            return "Queued task question(s) for the user.";
        }
        throw new IllegalStateException("ask_user_questions is available only in plan or task mode");
    }
}
