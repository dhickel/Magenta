package io.mindspice.magenta2.ai.chat.service.turn;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.service.ResolvedChatRequest;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes the system prompt and turn instructions by merging the default agent
 * prompt with mode-specific runtime instructions from PlanService/TaskService.
 */
public class PromptContextAssembler {

    private final AiConfig aiConfig;
    private final RuntimeSettingsService runtimeSettingsService;
    private final PlanService planService;
    private final TaskService taskService;

    public PromptContextAssembler(
        AiConfig aiConfig,
        RuntimeSettingsService runtimeSettingsService,
        PlanService planService,
        TaskService taskService
    ) {
        this.aiConfig = aiConfig;
        this.runtimeSettingsService = runtimeSettingsService;
        this.planService = planService;
        this.taskService = taskService;
    }

    /**
     * Assembles the full turn context: resolves interaction mode, builds system prompt,
     * and constructs the ordered instruction list (system message + user message).
     */
    public void assemble(TurnContext ctx) {
        ResolvedChatRequest request = ctx.resolvedRequest();
        PlanMode mode = ctx.interactionMode();
        String systemPrompt = mergeModePrompt(mode, request.conversationId());
        ctx.systemPrompt(systemPrompt);
        ctx.turnInstructions(assembleTurnInstructions(request, systemPrompt));
    }

    /**
     * Composes the effective system prompt by merging the default agent prompt
     * with mode-specific runtime instructions from plan/task services.
     * Key invariant: PLAN and TASK mode prompts completely replace the default system prompt.
     */
    public String mergeModePrompt(PlanMode mode, String conversationId) {
        String systemPrompt = defaultSystemPrompt();
        String runtimePrompt = planService == null ? "" : planService.runtimeInstructions(conversationId);
        if (mode == PlanMode.PLAN) {
            return runtimePrompt;
        }
        if (mode == PlanMode.TASK) {
            return taskService == null ? "" : taskService.runtimeInstructions(conversationId);
        }
        if (mode == PlanMode.EXECUTE_TASK) {
            String taskRuntimePrompt = taskService == null ? "" : taskService.runtimeInstructions(conversationId);
            if (!StringUtils.hasText(systemPrompt)) {
                return taskRuntimePrompt;
            }
            return systemPrompt + "\n\n" + taskRuntimePrompt;
        }
        if (!StringUtils.hasText(runtimePrompt)) {
            return systemPrompt;
        }
        if (!StringUtils.hasText(systemPrompt)) {
            return runtimePrompt;
        }
        return systemPrompt + "\n\n" + runtimePrompt;
    }

    /**
     * Builds the ordered instruction list: system message (if present) + user message.
     */
    public List<Message> assembleTurnInstructions(ResolvedChatRequest request, String systemPrompt) {
        List<Message> messages = new ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }
        messages.add(new UserMessage(request.message()));
        return messages;
    }

    public String defaultSystemPrompt() {
        if (runtimeSettingsService != null) {
            return runtimeSettingsService.defaultSystemPrompt();
        }
        if (aiConfig == null || !StringUtils.hasText(aiConfig.defaultAgent()) || aiConfig.agents() == null) {
            return null;
        }
        AgentConfig defaultAgent = aiConfig.agents().get(aiConfig.defaultAgent());
        return defaultAgent == null ? null : defaultAgent.systemPrompt();
    }
}
