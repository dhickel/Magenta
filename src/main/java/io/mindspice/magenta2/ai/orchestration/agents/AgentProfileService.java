package io.mindspice.magenta2.ai.orchestration.agents;

import java.util.List;
import java.util.UUID;

import io.mindspice.magenta2.ai.chat.tool.ChatToolRegistry;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.docker.AgentContainerRuntimeService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRuntimeRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentProfileService {
    private final AgentProfileRepository repository;
    private final AiConfig aiConfig;
    private final ObjectProvider<ChatToolRegistry> chatToolRegistry;
    private final ObjectProvider<AgentContainerRuntimeService> containerRuntimeService;
    private final ObjectProvider<WorkspaceService> workspaceService;
    private final ObjectProvider<WorkspaceDirectoryService> workspaceDirectoryService;
    private final ObjectProvider<WorkspaceRepository> workspaceRepository;
    private final ObjectProvider<OrchestrationRuntimeRepository> orchestrationRuntimeRepository;
    private final ObjectProvider<JobRepository> jobRepository;
    private final ObjectProvider<ProjectRepository> projectRepository;

    // Compatibility constructor for tests that instantiate the service directly.
    public AgentProfileService(
        AgentProfileRepository repository,
        AiConfig aiConfig,
        ObjectProvider<ChatToolRegistry> chatToolRegistry
    ) {
        this(
            repository,
            aiConfig,
            chatToolRegistry,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Autowired
    public AgentProfileService(
        AgentProfileRepository repository,
        AiConfig aiConfig,
        @Autowired(required = false) ObjectProvider<ChatToolRegistry> chatToolRegistry,
        @Autowired(required = false) ObjectProvider<AgentContainerRuntimeService> containerRuntimeService,
        @Autowired(required = false) ObjectProvider<WorkspaceService> workspaceService,
        @Autowired(required = false) ObjectProvider<WorkspaceDirectoryService> workspaceDirectoryService,
        @Autowired(required = false) ObjectProvider<WorkspaceRepository> workspaceRepository,
        @Autowired(required = false) ObjectProvider<OrchestrationRuntimeRepository> orchestrationRuntimeRepository,
        @Autowired(required = false) ObjectProvider<JobRepository> jobRepository,
        @Autowired(required = false) ObjectProvider<ProjectRepository> projectRepository
    ) {
        this.repository = repository;
        this.aiConfig = aiConfig;
        this.chatToolRegistry = chatToolRegistry;
        this.containerRuntimeService = containerRuntimeService;
        this.workspaceService = workspaceService;
        this.workspaceDirectoryService = workspaceDirectoryService;
        this.workspaceRepository = workspaceRepository;
        this.orchestrationRuntimeRepository = orchestrationRuntimeRepository;
        this.jobRepository = jobRepository;
        this.projectRepository = projectRepository;
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
        AgentProfile created = save(new AgentProfile(
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
        ensureAgentDurableStorage(created.id(), created.name());
        return created;
    }

    public AgentProfile update(String id, AgentProfile profile) {
        AgentProfile current = get(id);
        AgentProfile updated = save(new AgentProfile(
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
        ensureAgentDurableStorage(updated.id(), updated.name());
        if (updated.status() == AgentProfileStatus.DISABLED) {
            stopContainer(updated.id());
        }
        return updated;
    }

    public AgentProfile enable(String id, boolean wakeContainer) {
        AgentProfile profile = get(id);
        AgentProfile enabled = save(new AgentProfile(
            profile.id(),
            profile.name(),
            AgentProfileStatus.ACTIVE,
            profile.defaultModel(),
            profile.systemPrompt(),
            profile.approvedTools(),
            profile.allowedShellCommands(),
            profile.directLineEnabled(),
            profile.createdAt(),
            profile.updatedAt()
        ));
        ensureAgentDurableStorage(enabled.id(), enabled.name());
        if (wakeContainer) {
            AgentContainerRuntimeService runtime = containerRuntime();
            if (runtime != null) {
                runtime.ensureAgentContainer(enabled.id(), enabled.name());
            }
        }
        return enabled;
    }

    public AgentProfile disable(String id) {
        AgentProfile profile = get(id);
        AgentProfile disabled = save(new AgentProfile(
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
        stopContainer(id);
        return disabled;
    }

    public AgentProfile archiveAndDisable(String id) {
        AgentProfile disabled = disable(id);
        AgentContainerRuntimeService runtime = containerRuntime();
        if (runtime != null) {
            runtime.removeAgentContainer(id);
        }
        WorkspaceService ws = workspaceService();
        if (ws != null) {
            ws.archiveAgentWorkspaceData(id);
        }
        return disabled;
    }

    public void hardDelete(String id, String confirmationText) {
        if (!("DELETE " + id).equals(confirmationText)) {
            throw new IllegalArgumentException("confirmation text must exactly match: DELETE " + id);
        }
        get(id);
        OrchestrationRuntimeRepository runtimeRepository = orchestrationRuntimeRepository();
        if (runtimeRepository != null) {
            runtimeRepository.purgeAgentOwnedReferences(id);
        }
        JobRepository jobRepository = jobRepository();
        if (jobRepository != null) {
            jobRepository.purgeDefinitionsOwnedByAgent(id);
        }
        ProjectRepository projectRepository = projectRepository();
        if (projectRepository != null) {
            projectRepository.purgeAgentReferences(id);
        }
        AgentContainerRuntimeService runtime = containerRuntime();
        if (runtime != null) {
            runtime.removeAgentContainer(id);
        }
        WorkspaceRepository wsRepository = workspaceRepository();
        if (wsRepository != null) {
            wsRepository.findByOwner(WorkspaceOwnerType.AGENT, id)
                .ifPresent(workspace -> wsRepository.releaseLeasesByWorkspaceId(workspace.id()));
            wsRepository.deleteByOwner(WorkspaceOwnerType.AGENT, id);
        }
        WorkspaceService ws = workspaceService();
        if (ws != null) {
            ws.deleteAgentWorkspaceData(id);
        }
        repository.delete(id);
    }

    public void deleteOrDisable(String id) {
        disable(id);
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

    private void ensureAgentDurableStorage(String agentId, String agentName) {
        WorkspaceService ws = workspaceService();
        if (ws != null) {
            ws.agentWorkspace(agentId, agentName);
        }
        WorkspaceDirectoryService dir = workspaceDirectoryService();
        if (dir != null) {
            dir.agentHome(agentId);
            dir.agentWorkspaceRoot(agentId);
            dir.agentOutputRoot(agentId);
        }
    }

    private void stopContainer(String agentId) {
        AgentContainerRuntimeService runtime = containerRuntime();
        if (runtime != null) {
            runtime.stopAgentContainer(agentId, false);
        }
    }

    private AgentContainerRuntimeService containerRuntime() {
        return containerRuntimeService == null ? null : containerRuntimeService.getIfAvailable();
    }

    private WorkspaceService workspaceService() {
        return workspaceService == null ? null : workspaceService.getIfAvailable();
    }

    private WorkspaceDirectoryService workspaceDirectoryService() {
        return workspaceDirectoryService == null ? null : workspaceDirectoryService.getIfAvailable();
    }

    private WorkspaceRepository workspaceRepository() {
        return workspaceRepository == null ? null : workspaceRepository.getIfAvailable();
    }

    private OrchestrationRuntimeRepository orchestrationRuntimeRepository() {
        return orchestrationRuntimeRepository == null ? null : orchestrationRuntimeRepository.getIfAvailable();
    }

    private JobRepository jobRepository() {
        return jobRepository == null ? null : jobRepository.getIfAvailable();
    }

    private ProjectRepository projectRepository() {
        return projectRepository == null ? null : projectRepository.getIfAvailable();
    }
}
