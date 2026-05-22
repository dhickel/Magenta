package io.mindspice.magenta2.ai.chat.tool.avatar;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AvatarAssistantToolAuthorizationService {
    private final AgentProfileService agentProfileService;
    private final String supervisorAgentId;

    public AvatarAssistantToolAuthorizationService(
        AgentProfileService agentProfileService,
        @Value("${magenta.avatar.supervisor-agent-id:avatar}") String supervisorAgentId
    ) {
        this.agentProfileService = agentProfileService;
        this.supervisorAgentId = StringUtils.hasText(supervisorAgentId) ? supervisorAgentId.trim() : "avatar";
    }

    public AgentProfile requireAvatarSupervisor(String toolName) {
        OrchestrationTaskContext context = OrchestrationTaskContextHolder.current();
        if (context == null || !context.hasAgentContext()) {
            throw new IllegalStateException("Avatar assistant tools require an active Avatar agent context");
        }
        AgentProfile profile = agentProfileService.get(context.agentId());
        if (profile.status() == AgentProfileStatus.DISABLED) {
            throw new IllegalStateException("Avatar profile is disabled and cannot use assistant tools: " + profile.id());
        }
        if (!supervisorAgentId.equals(profile.id())) {
            throw new IllegalStateException("Avatar assistant tool requires agent id: " + supervisorAgentId);
        }
        List<String> approvedTools = profile.approvedTools() == null ? List.of() : profile.approvedTools();
        if (!approvedTools.contains(toolName)) {
            throw new IllegalStateException("Avatar assistant tool requires explicit profile approval: " + toolName);
        }
        return profile;
    }
}
