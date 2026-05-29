package io.mindspice.magenta2.ai.orchestration.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.workspaces.RootRelativePathService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import io.mindspice.magenta2.avatar.dashboard.DashboardProjectContextView;
import io.mindspice.magenta2.avatar.dashboard.ProjectArtifactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProjectServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    private ProjectService projectService;
    private ProjectRepository projectRepository;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(tempDir.resolve("projects"));
        projectRepository = repository();
        projectService = new ProjectService(projectRepository, null);
    }

    @Test
    void projectCanBeCreatedWithoutOwnerAgent() {
        Project project = projectService.createProject("Test", "desc", null, null);

        assertThat(project.ownerAgentId()).isNull();
        assertThat(projectService.listMembers(project.id())).isEmpty();
    }

    @Test
    void legacyOwnerAgentIsAutoAddedAsMember() {
        Project project = projectService.createProject("My Project", "desc", "agent-1", null);
        List<ProjectAgentMembership> members = projectService.listMembers(project.id());
        assertThat(members).hasSize(1);
        assertThat(members.get(0).agentId()).isEqualTo("agent-1");
        assertThat(members.get(0).role()).isEqualTo("owner");
    }

    @Test
    void agentCanBeAddedToMultipleProjects() {
        Project p1 = projectService.createProject("P1", "desc", "agent-a", null);
        Project p2 = projectService.createProject("P2", "desc", "agent-b", null);

        projectService.addAgent(p1.id(), "agent-x", "member");
        projectService.addAgent(p2.id(), "agent-x", "member");

        List<String> projects = projectService.listAgentProjects("agent-x");
        assertThat(projects).contains(p1.id(), p2.id());
    }

    @Test
    void legacyOwnerMembershipCanBeRemoved() {
        Project project = projectService.createProject("Test", "desc", "agent-owner", null);

        projectService.removeAgent(project.id(), "agent-owner");

        assertThat(projectService.listMembers(project.id())).isEmpty();
        assertThat(projectService.getProject(project.id()).ownerAgentId()).isEqualTo("agent-owner");
    }

    @Test
    void activeProjectAssignmentBlocksDeleteAndMembershipRemoval() {
        JdbcTemplate jdbc = jdbcTemplate();
        ProjectRepository projects = new ProjectRepository(jdbc, new ObjectMapper());
        OrchestrationRuntimeRepository runtime = new OrchestrationRuntimeRepository(jdbc, new ObjectMapper());
        ProjectService service = new ProjectService(projects, null, null, null, runtime);
        Project project = service.createProject("Active", "desc", "agent-active", null);
        runtime.saveAssignment(new WorkAssignment(
            "assignment-active", "agent-active", null, null, AssignmentType.REPORT, 1,
            OrchestrationStatus.RUNNING, null, null, project.id(), null, null,
            0, Map.of(), Map.of("projectId", project.id()), Map.of(), Map.of(),
            null, "lease-owner", Instant.now().plusSeconds(60), null, null, Instant.now(), null
        ));

        assertThatThrownBy(() -> service.deleteProject(project.id()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active assignments");
        assertThatThrownBy(() -> service.removeAgent(project.id(), "agent-active"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("active assignments");
    }

    @Test
    void projectNetworkGatesMessaging() {
        Project project = projectService.createProject("Net", "desc", "agent-a", null);
        projectService.addAgent(project.id(), "agent-b", "member");

        // Same project network
        assertThat(projectService.agentsShareProject("agent-a", "agent-b")).isTrue();

        // Outside the project
        assertThat(projectService.agentsShareProject("agent-a", "agent-x")).isFalse();
    }

    @Test
    void requireProjectNetworkRejectsOutsiders() {
        Project project = projectService.createProject("Net2", "desc", "agent-1", null);
        projectService.addAgent(project.id(), "agent-2", "member");

        // Members can communicate
        projectService.requireProjectNetwork(project.id(), "agent-1", "agent-2");

        // Non-member rejected
        assertThatThrownBy(() ->
            projectService.requireProjectNetwork(project.id(), "agent-1", "agent-x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a member");
    }

    @Test
    void listEventsReturnsRecordedEvents() {
        Project project = projectService.createProject("Events", "desc", "agent-1", null);
        projectService.recordEvent(project.id(), "test", "{\"key\":\"val\"}");
        projectService.recordEvent(project.id(), "update", null);

        List<ProjectEvent> events = projectService.listEvents(project.id());
        assertThat(events).hasSize(2);
        assertThat(events).extracting(ProjectEvent::type).contains("test", "update");
    }

    @Test
    void projectArtifactAdapterCreatesAndValidatesTypedHouseholdFilesUnderProjectRoot() throws Exception {
        ProjectArtifactFixture fixture = projectArtifactFixture();
        ProjectService service = fixture.projectService();
        WorkAreaService workAreas = fixture.workAreaService();
        Project project = service.createProject("Kitchen Remodel", "Household work", null, null);
        ProjectArtifactService artifacts = fixture.artifacts();

        DashboardProjectContextView context = artifacts.context(project.id());

        assertThat(context.missingBinding()).isFalse();
        assertThat(context.codeProject()).isFalse();
        assertThat(context.artifacts()).extracting("type")
            .contains("goals", "materials", "contacts", "blockers", "next-actions", "progress");
        java.nio.file.Path root = workAreas.ownerRoot(
            io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType.PROJECT,
            project.id()
        );
        assertThat(root.resolve(".magenta/project/goals.json")).exists();

        artifacts.updateArtifact(project.id(), "goals", "{\"goals\":[{\"title\":\"Demo cabinets\",\"status\":\"active\"}]}");
        assertThat(artifacts.context(project.id()).artifacts())
            .filteredOn(artifact -> "goals".equals(artifact.type()))
            .singleElement()
            .satisfies(artifact -> assertThat(artifact.items()).contains("Demo cabinets"));

        assertThatThrownBy(() -> artifacts.updateArtifact(project.id(), "materials", "{\"wrong\":[]}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("materials");
    }

    @Test
    void projectFileNotesRejectNormalizedTraversalOutsideProjectNamespace() throws Exception {
        ProjectArtifactFixture fixture = projectArtifactFixture();
        Project project = fixture.projectService().createProject("Notes", "Household notes", null, null);

        assertThatThrownBy(() -> fixture.artifacts().readProjectFile(project.id(), ".magenta/project/../outside.md"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".magenta/project");
        assertThatThrownBy(() -> fixture.artifacts().saveProjectFile(project.id(), ".magenta/project/../outside.md", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(".magenta/project");
        assertThatThrownBy(() -> fixture.artifacts().readProjectFile(project.id(), tempDir.resolve("note.md").toString()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absolute");
    }

    @Test
    void projectArtifactsRejectSymlinkedArtifactDirectory() throws Exception {
        ProjectArtifactFixture fixture = projectArtifactFixture();
        Project project = fixture.projectService().createProject("Linked", "Linked project artifacts", null, null);
        Path root = fixture.workAreaService().ownerRoot(
            io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType.PROJECT,
            project.id()
        );
        Files.createDirectories(root.resolve(".magenta"));
        Path outside = Files.createDirectories(tempDir.resolve("outside-artifacts"));
        createSymlinkOrSkip(root.resolve(".magenta/project"), outside);

        assertThatThrownBy(() -> fixture.artifacts().updateArtifact(project.id(), "goals", "{\"goals\":[]}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("symbolic links");
    }

    @Test
    void projectArtifactsRejectSymlinkedArtifactFile() throws Exception {
        ProjectArtifactFixture fixture = projectArtifactFixture();
        Project project = fixture.projectService().createProject("Linked File", "Linked project artifact file", null, null);
        Path root = fixture.workAreaService().ownerRoot(
            io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType.PROJECT,
            project.id()
        );
        Files.createDirectories(root.resolve(".magenta/project"));
        Path outside = tempDir.resolve("outside-goals.json");
        Files.writeString(outside, "{\"goals\":[]}");
        createSymlinkOrSkip(root.resolve(".magenta/project/goals.json"), outside);

        assertThatThrownBy(() -> fixture.artifacts().updateArtifact(project.id(), "goals", "{\"goals\":[]}"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("symbolic links");
    }

    private ProjectRepository repository() {
        return new ProjectRepository(jdbcTemplate(), new ObjectMapper());
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new JdbcTemplate(ds);
    }

    private ProjectArtifactFixture projectArtifactFixture() throws IOException {
        JdbcTemplate jdbc = jdbcTemplate();
        ProjectRepository projects = new ProjectRepository(jdbc, new ObjectMapper());
        AiConfig config = new AiConfig(null, null, null, 10, tempDir.resolve("data"), Map.of(), Map.of());
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(config);
        WorkspaceService workspaceService = new WorkspaceService(
            new WorkspaceRepository(jdbc),
            config,
            new RootRelativePathService(directoryService)
        );
        WorkAreaService workAreas = new WorkAreaService(new WorkAreaRepository(jdbc), workspaceService, directoryService);
        ProjectService service = new ProjectService(projects, directoryService, workspaceService, null, null);
        ProjectArtifactService artifacts = new ProjectArtifactService(
            service,
            workAreas,
            new WorkAreaExplorerService(workAreas),
            null,
            new ObjectMapper()
        );
        return new ProjectArtifactFixture(service, workAreas, artifacts);
    }

    private void createSymlinkOrSkip(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            assumeTrue(Files.isSymbolicLink(link), "symlinks are unavailable");
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            assumeTrue(false, "symlinks are unavailable: " + exception.getMessage());
        }
    }

    private record ProjectArtifactFixture(
        ProjectService projectService,
        WorkAreaService workAreaService,
        ProjectArtifactService artifacts
    ) {
    }
}
