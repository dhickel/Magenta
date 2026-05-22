package io.mindspice.magenta2.ai.chat.service.turn;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * Resolves which tool callbacks are available for a turn, filtered by
 * interaction mode and approved-tool configuration.
 */
public class ToolAccessPolicy {

    public static final List<String> PLAN_MODE_TOOLS = List.of(
        "file_list", "file_read", "file_search", "shell_exec", "web_search", "web_fetch",
        "plan_set_goal", "plan_set_task", "plan_put_item", "plan_delete_item",
        "ask_user_questions", "plan_ready_for_approval"
    );

    public static final List<String> TASK_MODE_TOOLS = List.of(
        "file_list", "file_read", "file_search", "shell_exec", "web_search", "web_fetch",
        "ask_user_questions", "task_set_goal", "task_set_task", "task_put_item", "task_delete_item",
        "task_ready_for_approval"
    );

    private static final List<String> NORMAL_BLOCKED_TOOLS = List.of(
        "plan_update", "plan_set_goal", "plan_set_task", "plan_put_item", "plan_delete_item",
        "ask_user_questions", "plan_ready_for_approval", "plan_report", "plan_complete",
        "task_set_goal", "task_set_task", "task_put_item", "task_delete_item", "task_ready_for_approval",
        "task_report", "task_complete"
    );

    private static final List<String> EXECUTION_BLOCKED_TOOLS = List.of(
        "plan_update", "plan_set_goal", "plan_set_task", "plan_put_item", "plan_delete_item",
        "ask_user_questions", "plan_ready_for_approval",
        "task_set_goal", "task_set_task", "task_put_item", "task_delete_item", "task_ready_for_approval"
    );

    private final ChatToolRegistry chatToolRegistry;
    private final PlanService planService;
    private final TaskService taskService;

    public ToolAccessPolicy(ChatToolRegistry chatToolRegistry, PlanService planService, TaskService taskService) {
        this.chatToolRegistry = chatToolRegistry;
        this.planService = planService;
        this.taskService = taskService;
    }

    /**
     * Resolves the interaction mode for a conversation.
     * PLAN/TASK modes are checked first; fallback is NORMAL.
     */
    public PlanMode interactionMode(String conversationId) {
        PlanMode planMode = planService == null ? PlanMode.NORMAL : planService.mode(conversationId);
        if (planMode != PlanMode.NORMAL) {
            return planMode;
        }
        return taskService == null ? PlanMode.NORMAL : taskService.mode(conversationId);
    }

    /**
     * Resolves approved tools from the given tool names filtered by the current interaction mode.
     * Preserves the exact branch order: PLAN → TASK → EXECUTE_PLAN → EXECUTE_TASK → NORMAL.
     */
    public List<ToolCallback> filterToolsByMode(List<String> approvedToolNames, PlanMode mode) {
        if (chatToolRegistry == null) {
            return List.of();
        }
        if (mode == PlanMode.PLAN) {
            return withoutOperationalTools(chatToolRegistry.resolveApprovedTools(approvedToolNames, PLAN_MODE_TOOLS));
        }
        if (mode == PlanMode.TASK) {
            return withoutOperationalTools(chatToolRegistry.resolveApprovedTools(approvedToolNames, TASK_MODE_TOOLS));
        }
        if (mode == PlanMode.EXECUTE_PLAN) {
            return chatToolRegistry.resolveApprovedTools(approvedToolNames).stream()
                .filter(callback -> !EXECUTION_BLOCKED_TOOLS.contains(callback.getToolDefinition().name()))
                .toList();
        }
        if (mode == PlanMode.EXECUTE_TASK) {
            return chatToolRegistry.resolveApprovedTools(approvedToolNames).stream()
                .filter(callback -> !EXECUTION_BLOCKED_TOOLS.contains(callback.getToolDefinition().name()))
                .toList();
        }
        return chatToolRegistry.resolveApprovedTools(approvedToolNames).stream()
            .filter(callback -> !NORMAL_BLOCKED_TOOLS.contains(callback.getToolDefinition().name()))
            .toList();
    }

    private List<ToolCallback> withoutOperationalTools(List<ToolCallback> callbacks) {
        return callbacks.stream()
            .filter(callback -> !isOperationalTool(callback.getToolDefinition().name()))
            .toList();
    }

    private boolean isOperationalTool(String toolName) {
        return toolName != null && (toolName.startsWith("agent_") || toolName.startsWith("avatar_"));
    }
}
