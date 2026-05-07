package io.mindspice.magenta2.ai.orchestration.agents;

import java.util.List;
import java.util.UUID;

import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentProfileService {
    private final AgentProfileRepository repository;
    private final AiConfig aiConfig;
    private final ObjectProvider<ChatToolRegistry> chatToolRegistry;

    public AgentProfileService(
        AgentProfileRepository repository,
        AiConfig aiConfig,
        @Autowired(required = false) ObjectProvider<ChatToolRegistry> chatToolRegistry
    ) {
        this.repository = repository;
        this.aiConfig = aiConfig;
        this.chatToolRegistry = chatToolRegistry;
    }

    public List<AgentProfile> list() {
        return repository.findAll();
    }

    public AgentProfile get(String id) {
        return repository.findById(id).orElseThrow(() -> new IllegalStateException("Agent profile not found: " + id));
    }

    public AgentProfile defaultAgent(String defaultAgentId, String defaultAgentName) {
        if (StringUtils.hasText(defaultAgentId)) {
            return repository.findById(defaultAgentId)
                .orElseGet(() -> findNamedDefault(defaultAgentName));
        }
        return findNamedDefault(defaultAgentName);
    }

    public AgentProfile create(AgentProfile profile) {
        String id = StringUtils.hasText(profile.id()) ? profile.id() : UUID.randomUUID().toString();
        return save(new AgentProfile(
            id,
            profile.name(),
            profile.status() == null ? AgentProfileStatus.ACTIVE : profile.status(),
            profile.defaultModel(),
            profile.systemPrompt(),
            profile.approvedTools(),
            profile.allowedShellCommands(),
            profile.directLineEnabled(),
            null,
            null
        ));
    }

    public AgentProfile update(String id, AgentProfile profile) {
        AgentProfile current = get(id);
        return save(new AgentProfile(
            id,
            profile.name(),
            profile.status() == null ? current.status() : profile.status(),
            profile.defaultModel(),
            profile.systemPrompt(),
            profile.approvedTools(),
            profile.allowedShellCommands(),
            profile.directLineEnabled(),
            current.createdAt(),
            current.updatedAt()
        ));
    }

    public AgentProfile clone(String id) {
        AgentProfile source = get(id);
        String cloneName = uniqueCloneName(source.name());
        return save(new AgentProfile(
            UUID.randomUUID().toString(),
            cloneName,
            AgentProfileStatus.ACTIVE,
            source.defaultModel(),
            source.systemPrompt(),
            source.approvedTools(),
            source.allowedShellCommands(),
            source.directLineEnabled(),
            null,
            null
        ));
    }

    public void deleteOrDisable(String id) {
        AgentProfile profile = get(id);
        repository.save(new AgentProfile(
            profile.id(),
            profile.name(),
            AgentProfileStatus.DISABLED,
            profile.defaultModel(),
            profile.systemPrompt(),
            profile.approvedTools(),
            profile.allowedShellCommands(),
            profile.directLineEnabled(),
            profile.createdAt(),
            profile.updatedAt()
        ));
    }

    public List<String> allowedShellCommands(String defaultAgentId, String defaultAgentName) {
        return defaultAgent(defaultAgentId, defaultAgentName).allowedShellCommands();
    }

    public String systemPrompt(String defaultAgentId, String defaultAgentName) {
        return defaultAgent(defaultAgentId, defaultAgentName).systemPrompt();
    }

    public List<String> approvedTools(String defaultAgentId, String defaultAgentName) {
        return defaultAgent(defaultAgentId, defaultAgentName).approvedTools();
    }

    AgentProfile save(AgentProfile profile) {
        validate(profile);
        return repository.save(profile);
    }

    private AgentProfile findNamedDefault(String defaultAgentName) {
        if (StringUtils.hasText(defaultAgentName)) {
            return repository.findByName(defaultAgentName)
                .orElseThrow(() -> new IllegalStateException("Default agent profile not found: " + defaultAgentName));
        }
        return repository.findByName("magenta")
            .orElseGet(() -> repository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("No agent profiles exist")));
    }

    private void validate(AgentProfile profile) {
        if (!StringUtils.hasText(profile.id())) {
            throw new IllegalArgumentException("agent id is required");
        }
        if (!StringUtils.hasText(profile.name())) {
            throw new IllegalArgumentException("agent name is required");
        }
        if (StringUtils.hasText(profile.defaultModel()) && !aiConfig.models().containsKey(profile.defaultModel())) {
            throw new IllegalArgumentException("unknown model key: " + profile.defaultModel());
        }
        ChatToolRegistry registry = chatToolRegistry == null ? null : chatToolRegistry.getIfAvailable();
        if (registry != null) {
            registry.validateToolNames(profile.approvedTools());
        }
        validateShellCommands(profile.allowedShellCommands());
    }

    private void validateShellCommands(List<String> commands) {
        if (commands == null) {
            return;
        }
        for (String command : commands) {
            if (!StringUtils.hasText(command)) {
                continue;
            }
            if ("*".equals(command)) {
                continue;
            }
            if (command.contains("/") || command.contains("\\") || command.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("shell command allowlist entries must be bare executable names");
            }
        }
    }

    private String uniqueCloneName(String baseName) {
        String candidate = baseName + " copy";
        int suffix = 2;
        while (repository.findByName(candidate).isPresent()) {
            candidate = baseName + " copy " + suffix++;
        }
        return candidate;
    }
}
