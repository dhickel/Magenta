package io.mindspice.magenta2.ai.orchestration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.EndpointType;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileSeeder;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRecurrence;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRun;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRunStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationEvent;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJob;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobItem;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRuntimeRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectAgentMembership;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectEvent;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
import io.mindspice.magenta2.ai.orchestration.runtime.ReactionActionType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workspaces.Workspace;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLease;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLink;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLinkType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrchestrationRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeSettingsSaveLoadAndModelResolutionPriority() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AiConfig aiConfig = aiConfig();
        AgentProfileRepository agentRepository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        AgentProfileService agentService = new AgentProfileService(agentRepository, aiConfig, null);
        AgentProfile agent = agentService.create(profile("magenta", "main"));
        RuntimeSettingsRepository settingsRepository = new RuntimeSettingsRepository(jdbcTemplate);
        RuntimeSettingsService settingsService = new RuntimeSettingsService(settingsRepository, aiConfig, agentService);

        RuntimeSettings saved = settingsService.save(new RuntimeSettings(
            agent.id(), agent.name(), "summary", "planning", "summary", "main", 20
        ));

        assertThat(settingsRepository.find()).contains(saved);
        assertThat(settingsService.resolveModel("planning", "main")).isEqualTo("planning-remote");
        assertThat(settingsService.resolveModel(null, "main")).isEqualTo("main-remote");
        assertThat(settingsService.defaultModel()).isEqualTo("main-remote");
        assertThat(settingsService.contextBufferPercent()).isEqualTo(20);
    }

    @Test
    void agentProfileCrudDisableAndJsonLists() {
        AgentProfileService service = agentService(jdbcTemplate(), aiConfig());
        AgentProfile created = service.create(profile("magenta", "main"));

        AgentProfile updated = service.update(created.id(), new AgentProfile(
            created.id(), "magenta", AgentProfileStatus.ACTIVE, "planning", "Prompt 2",
            List.of("file_read"), List.of("printf"), true, null, null
        ));
        service.deleteOrDisable(created.id());

        assertThat(updated.approvedTools()).containsExactly("file_read");
        assertThat(updated.allowedShellCommands()).containsExactly("printf");
        assertThat(service.get(created.id()).status()).isEqualTo(AgentProfileStatus.DISABLED);
    }

    @Test
    void workspaceCreatesRootsAndRejectsEscapes() throws Exception {
        WorkspaceService service = new WorkspaceService(new WorkspaceRepository(jdbcTemplate()), aiConfig());

        Workspace workspace = service.agentWorkspace("agent-1", "Agent 1");
        WorkspaceLink link = service.addLink(workspace.id(), new WorkspaceLink(
            null, workspace.id(), "notes", WorkspaceLinkType.PATH, "notes", true, false, null, null
        ));

        assertThat(Files.isDirectory(tempDir.resolve("agents/agent-1"))).isTrue();
        assertThat(link.label()).isEqualTo("notes");
        assertThatThrownBy(() -> service.addLink(workspace.id(), new WorkspaceLink(
            null, workspace.id(), "bad", WorkspaceLinkType.PATH, "../../../../escape", true, false, null, null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("escapes data root");
    }

    @Test
    void projectCreationPersistsManagedProjectWorkspace() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, aiConfig());
        ProjectService projectService = new ProjectService(
            new ProjectRepository(jdbcTemplate, new ObjectMapper()),
            new WorkspaceDirectoryService(aiConfig()),
            workspaceService,
            new io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceLeaseService(workspaceRepository),
            new OrchestrationRuntimeRepository(jdbcTemplate, new ObjectMapper())
        );

        Project project = projectService.createProject("Leaseable", "desc", "agent-1", null);
        Workspace workspace = workspaceRepository.findByOwner(WorkspaceOwnerType.PROJECT, project.id()).orElseThrow();

        assertThat(workspace.rootRelativePath()).isEqualTo("projects/" + project.id() + "/workspace");
        assertThat(Files.isDirectory(tempDir.resolve(workspace.rootRelativePath()))).isTrue();
    }

    @Test
    void workspaceListBoundsAndActiveLeaseReads() throws Exception {
        WorkspaceRepository repository = new WorkspaceRepository(jdbcTemplate());
        WorkspaceService service = new WorkspaceService(repository, aiConfig());

        Workspace a1 = service.agentWorkspace("agent-1", "Agent 1");
        service.agentWorkspace("agent-2", "Agent 2");
        service.jobWorkspace("job-1", "Job 1");
        repository.saveLease(new WorkspaceLease(
            "lease-1",
            a1.id(),
            "TASK_RUN",
            "run-1",
            io.mindspice.magenta2.ai.orchestration.workspaces.LeaseMode.READ,
            java.time.Instant.now().plusSeconds(300),
            false,
            null,
            java.time.Instant.now(),
            java.time.Instant.now()
        ));

        assertThat(service.list(io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType.AGENT, null, 500))
            .hasSize(2);
        assertThat(service.list(null, null, 1)).hasSize(1);
        assertThat(service.list(null, null, -10)).hasSize(3);
        assertThat(service.activeLeases(a1.id()))
            .extracting(WorkspaceLease::holderId)
            .containsExactly("run-1");
    }

    @Test
    void legacySeederCreatesOneDefaultAgentOnlyWhenEmpty() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        AiConfig aiConfig = aiConfig();
        AgentProfileRepository repository = new AgentProfileRepository(jdbcTemplate, new ObjectMapper());
        AgentProfileService service = new AgentProfileService(repository, aiConfig, null);
        RuntimeSettingsRepository settingsRepository = new RuntimeSettingsRepository(jdbcTemplate);
        AgentProfileSeeder seeder = new AgentProfileSeeder(repository, service, settingsRepository, aiConfig);

        seeder.run(null);
        seeder.run(null);

        assertThat(repository.findAll()).hasSize(1);
        AgentProfile agent = repository.findAll().getFirst();
        assertThat(agent.name()).isEqualTo("magenta");
        assertThat(agent.systemPrompt()).isEqualTo("Legacy prompt");
        assertThat(settingsRepository.find().orElseThrow().defaultAgentId()).isEqualTo(agent.id());
    }

    @Test
    void hardDeletePurgesRuntimeJobAndProjectAgentReferences() throws Exception {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        AiConfig aiConfig = aiConfig();

        AgentProfileRepository agentRepository = new AgentProfileRepository(jdbcTemplate, objectMapper);
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        WorkspaceService workspaceService = new WorkspaceService(workspaceRepository, aiConfig);
        OrchestrationRuntimeRepository runtimeRepository = new OrchestrationRuntimeRepository(jdbcTemplate, objectMapper);
        JobRepository jobRepository = new JobRepository(jdbcTemplate, objectMapper);
        ProjectRepository projectRepository = new ProjectRepository(jdbcTemplate, objectMapper);

        AgentProfileService service = new AgentProfileService(
            agentRepository,
            aiConfig,
            null,
            provider(WorkspaceService.class, workspaceService),
            null,
            provider(WorkspaceRepository.class, workspaceRepository),
            provider(OrchestrationRuntimeRepository.class, runtimeRepository),
            provider(JobRepository.class, jobRepository),
            provider(ProjectRepository.class, projectRepository)
        );

        service.create(new AgentProfile(
            "agent-1", "Agent 1", AgentProfileStatus.ACTIVE, "main", "Prompt",
            List.of(), List.of("printf"), true, null, null
        ));

        runtimeRepository.saveJob(new OrchestrationJob(
            "orch-job-1", "agent-1", "Owned Job", "summary", null, null, OrchestrationStatus.QUEUED, null, null
        ));
        runtimeRepository.saveJobItem(new OrchestrationJobItem(
            "orch-item-1", "orch-job-1", 0, AssignmentType.TASK_RUN, "task-1", null,
            null, 1, 0, false, Map.of(), null, null
        ));
        runtimeRepository.saveAssignment(new WorkAssignment(
            "assignment-1", "agent-1", "orch-job-1", "orch-item-1", AssignmentType.TASK_RUN, 1,
            OrchestrationStatus.QUEUED, null, null, 0,
            Map.of(), Map.of(), Map.of(), Map.of(),
            null, null, null, null, null, null, null
        ));
        runtimeRepository.saveAssignment(new WorkAssignment(
            "assignment-2", "agent-2", "orch-job-1", "orch-item-1", AssignmentType.TASK_RUN, 1,
            OrchestrationStatus.QUEUED, null, null, 0,
            Map.of(), Map.of(), Map.of(), Map.of(),
            null, null, null, null, null, null, null
        ));
        runtimeRepository.saveInboxMessage(new InboxMessage(
            "inbox-1", "agent-1", "agent-2", "INFO", "to agent", Map.of(), false, false, null, null
        ));
        runtimeRepository.saveInboxMessage(new InboxMessage(
            "inbox-2", "agent-2", "agent-1", "INFO", "from agent", Map.of(), false, false, null, null
        ));
        runtimeRepository.saveSchedule(new AgentSchedule(
            "schedule-1", "agent-1", "orch-job-1", Map.of(),
            "0 * * * *", "UTC", true, Instant.now(), null, null
        ));
        runtimeRepository.createScheduleFiring("firing-1", "schedule-1", Instant.now(), "assignment-1");
        runtimeRepository.saveReaction(new AgentEventReaction(
            "reaction-1", "agent-1", EventType.JOB_STATUS_CHANGED, Map.of(),
            ReactionActionType.ENQUEUE_ASSIGNMENT, Map.of(), true, null, null
        ));
        runtimeRepository.saveEvent(new OrchestrationEvent(
            "event-1", EventType.JOB_STATUS_CHANGED, "agent", "agent-1", Map.of("k", "v"), null, null
        ));

        jobRepository.saveDefinition(new JobDefinition(
            "job-def-1", "agent-1", null, null, "DRAFT", "Job", "summary", List.of(), null, null, null, null, null
        ));
        jobRepository.saveRun(new JobRun(
            "job-run-1", "job-def-1", JobRunStatus.RUNNING, null,
            "/tmp/ws", "/tmp/out", null, null, null, null, Instant.now(), null
        ));
        jobRepository.saveRecurrence(new JobRecurrence(
            "job-rec-1", "job-def-1", "*/5 * * * *", "UTC", Instant.now().plusSeconds(60), true, null, null
        ));

        projectRepository.save(new Project(
            "project-owned", "Owned Project", "desc", "agent-1", null, null, null, null, null, null
        ));
        projectRepository.saveEvent(new ProjectEvent("project-event-1", "project-owned", "CREATED", "{}", null));
        projectRepository.saveMembership(new ProjectAgentMembership(
            "project-membership-1", "project-owned", "agent-2", "member", null
        ));
        projectRepository.save(new Project(
            "project-shared", "Shared Project", "desc", "agent-2", null, null, null, null, null, null
        ));
        projectRepository.saveMembership(new ProjectAgentMembership(
            "project-membership-2", "project-shared", "agent-1", "member", null
        ));

        service.hardDelete("agent-1", "DELETE agent-1");

        assertThat(agentRepository.findById("agent-1")).isEmpty();
        assertThat(runtimeRepository.findJobsForAgent("agent-1")).isEmpty();
        assertThat(runtimeRepository.findAssignmentsForAgent("agent-1")).isEmpty();
        assertThat(runtimeRepository.findInboxMessages("agent-1")).isEmpty();
        assertThat(runtimeRepository.findSchedulesForAgent("agent-1")).isEmpty();
        assertThat(runtimeRepository.findReactionsForAgent("agent-1")).isEmpty();
        assertThat(runtimeRepository.findEventsForSource("agent", "agent-1")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from work_assignments where id = ?",
            Integer.class,
            "assignment-2"
        )).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from schedule_firings where schedule_id = ?",
            Integer.class,
            "schedule-1"
        )).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from agent_inbox_messages where from_id = ?",
            Integer.class,
            "agent-1"
        )).isEqualTo(0);
        assertThat(jobRepository.findDefinitions("agent-1", null, null)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from job_runs where job_id = ?",
            Integer.class,
            "job-def-1"
        )).isEqualTo(0);
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from job_recurrences where job_id = ?",
            Integer.class,
            "job-def-1"
        )).isEqualTo(0);
        assertThat(projectRepository.findByOwnerAgent("agent-1")).isEmpty();
        assertThat(projectRepository.findMembershipsByAgent("agent-1")).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
            "select count(*) from project_events where project_id = ?",
            Integer.class,
            "project-owned"
        )).isEqualTo(0);
    }

    private AgentProfileService agentService(JdbcTemplate jdbcTemplate, AiConfig aiConfig) {
        return new AgentProfileService(new AgentProfileRepository(jdbcTemplate, new ObjectMapper()), aiConfig, null);
    }

    private <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        factory.addBean(type.getName(), bean);
        return factory.getBeanProvider(type);
    }

    private AgentProfile profile(String name, String model) {
        return new AgentProfile(
            null, name, AgentProfileStatus.ACTIVE, model, "Prompt", List.of(), List.of("printf"),
            true, null, null
        );
    }

    private AiConfig aiConfig() {
        Map<String, ModelConfig> models = Map.of(
            "main", model("main-remote"),
            "planning", model("planning-remote"),
            "summary", model("summary-remote")
        );
        return new AiConfig(
            "legacy",
            "main",
            "summary",
            "planning",
            "main",
            10,
            tempDir,
            null,
            models,
            Map.of("legacy", new AgentConfig("main", "Legacy prompt", List.of(), List.of("*")))
        );
    }

    private ModelConfig model(String remoteName) {
        return new ModelConfig(remoteName, "http://localhost:11434", EndpointType.OLLAMA, 4096, 0, null);
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase 2: OrchestrationTaskContext propagation
    // ════════════════════════════════════════════════════════════════

    @Test
    void orchestrationTaskContextIsBuiltAndCleared() {
        JdbcTemplate jdbcTemplate = jdbcTemplate();
        ObjectMapper objectMapper = new ObjectMapper();
        AiConfig aiConfig = aiConfig();

        AgentProfileRepository agentRepository = new AgentProfileRepository(jdbcTemplate, objectMapper);
        AgentProfileService agentService = new AgentProfileService(agentRepository, aiConfig, null);
        AgentProfile agent = agentService.create(profile("runner-agent", "main"));

        OrchestrationRuntimeRepository runtimeRepository = new OrchestrationRuntimeRepository(jdbcTemplate, objectMapper);
        JobRepository jobRepository = new JobRepository(jdbcTemplate, objectMapper);

        // Create an assignment with agent, job, and workspace context
        WorkAssignment assignment = new WorkAssignment(
            "assign-ctx-1", agent.id(), "job-ctx-1", null, AssignmentType.REPORT, 1,
            OrchestrationStatus.QUEUED, null, "ws-ctx-1", 0,
            Map.of("message", "Test context propagation"),
            Map.of(), Map.of(), Map.of(),
            null, null, null, null, null, null, null
        );
        runtimeRepository.saveAssignment(assignment);

        // Verify OrchestrationTaskContext can be set and cleared without leaking
        OrchestrationTaskContext ctx = new OrchestrationTaskContext(
            agent.id(), agent.name(), "job-ctx-1", null, "ws-ctx-1",
            "TASK_RUN", "/tmp/ws", "/tmp/out");

        assertThat(ctx.hasAgentContext()).isTrue();
        assertThat(ctx.hasContext()).isTrue();
        assertThat(ctx.agentId()).isEqualTo(agent.id());
        assertThat(ctx.agentName()).isEqualTo("runner-agent");
        assertThat(ctx.jobId()).isEqualTo("job-ctx-1");
        assertThat(ctx.workspaceId()).isEqualTo("ws-ctx-1");
        assertThat(ctx.runType()).isEqualTo("TASK_RUN");
        assertThat(ctx.hostWorkspacePath()).isEqualTo("/tmp/ws");
        assertThat(ctx.hostOutputPath()).isEqualTo("/tmp/out");

        // Set and clear via holder
        OrchestrationTaskContextHolder.set(ctx);
        assertThat(OrchestrationTaskContextHolder.current()).isEqualTo(ctx);
        OrchestrationTaskContextHolder.clear();
        assertThat(OrchestrationTaskContextHolder.current()).isNull();
    }

    @Test
    void orchestrationTaskContextHolderIsThreadLocal() throws Exception {
        OrchestrationTaskContext mainCtx = new OrchestrationTaskContext(
            "main-agent", "Main", null, null, null, "TASK_RUN", null, null);

        OrchestrationTaskContextHolder.set(mainCtx);

        // Verify thread isolation
        java.util.concurrent.atomic.AtomicReference<OrchestrationTaskContext> otherCtx =
            new java.util.concurrent.atomic.AtomicReference<>();
        Thread other = new Thread(() -> {
            otherCtx.set(OrchestrationTaskContextHolder.current());
        });
        other.start();
        other.join();

        assertThat(otherCtx.get()).isNull(); // other thread sees no context
        assertThat(OrchestrationTaskContextHolder.current()).isEqualTo(mainCtx); // main still has it

        OrchestrationTaskContextHolder.clear();
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
