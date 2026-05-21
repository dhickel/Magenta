package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkspacePathSegmentValidationTest {
    @TempDir
    Path tempDir;

    @Test
    void workspaceServiceAcceptsValidAgentIdAndCreatesExpectedSubtree() throws Exception {
        WorkspaceService service = workspaceService();

        Workspace workspace = service.agentWorkspace("agent-1", "Agent One");

        assertThat(workspace.rootRelativePath()).isEqualTo("agents/agent-1/workspace");
        assertThat(Files.isDirectory(tempDir.resolve("agents/agent-1/workspace"))).isTrue();
    }

    @Test
    void workspaceServiceRejectsInvalidAgentIdsBeforePathComposition() throws Exception {
        WorkspaceService service = workspaceService();

        for (String invalid : invalidSegments()) {
            assertThatThrownBy(() -> service.agentWorkspace(invalid, "Bad Agent"))
                .as("invalid agent workspace id %s", invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
            assertThatThrownBy(() -> service.archiveAgentWorkspaceData(invalid))
                .as("invalid archive agent id %s", invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
            assertThatThrownBy(() -> service.deleteAgentWorkspaceData(invalid))
                .as("invalid delete agent id %s", invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
            assertThatThrownBy(() -> service.assignmentPath(invalid, "assignment-1"))
                .as("invalid assignment agent id %s", invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
        }
    }

    @Test
    void workspaceServiceRejectsInvalidNonAgentPathIds() throws Exception {
        WorkspaceService service = workspaceService();

        assertThatThrownBy(() -> service.jobWorkspace("../jobs", "Bad Job"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jobId");
        assertThatThrownBy(() -> service.projectWorkspace("a/b", "Bad Project"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("projectId");
        assertThatThrownBy(() -> service.assignmentPath("agent-1", "%2e%2e"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("assignmentId");
    }

    @Test
    void workspaceDirectoryServiceRejectsInvalidIdsBeforeCreatingDirectories() throws Exception {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(aiConfig());

        assertThat(service.agentWorkspace("agent-1"))
            .isEqualTo(tempDir.resolve("agents/agent-1/workspace").toRealPath());
        for (String invalid : invalidSegments()) {
            assertThatThrownBy(() -> service.agentWorkspace(invalid))
                .as("invalid directory agent id %s", invalid)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("agentId");
        }
        assertThatThrownBy(() -> service.jobWorkspace("a/b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jobId");
        assertThatThrownBy(() -> service.projectWorkspace("%2e%2e"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("projectId");
        assertThatThrownBy(() -> service.agentOutput("agent-1", "plan", "a\\b"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runId");
    }

    @Test
    void projectLinkMaterializationCreatesUsableAssignmentPathAndCleanupRemovesLink() throws Exception {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(aiConfig());
        Path assignmentWorkspace = service.taskTemp("run-1");
        Path projectWorkspace = service.projectWorkspace("project-1");
        Files.writeString(projectWorkspace.resolve("shared.txt"), "project data");

        Path link;
        try {
            link = service.materializeAssignmentProjectLink(assignmentWorkspace.toString(), "project-1");
        } catch (IllegalStateException e) {
            if (e.getMessage().contains("symlink support")) {
                return;
            }
            throw e;
        }

        assertThat(link).isEqualTo(assignmentWorkspace.resolve("projects/project-1"));
        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(service.requireAssignmentProjectLinkTarget(assignmentWorkspace.toString(), "project-1"))
            .isEqualTo(projectWorkspace.toRealPath());
        assertThat(Files.readString(link.resolve("shared.txt"))).isEqualTo("project data");

        service.removeAssignmentProjectLink(assignmentWorkspace.toString(), "project-1");

        assertThat(Files.exists(link)).isFalse();
        assertThat(Files.exists(projectWorkspace.resolve("shared.txt"))).isTrue();
    }

    @Test
    void effectiveWorkspaceResolverPrefersProjectWorkspaceWhenProjectIdIsPresent() throws Exception {
        WorkspaceService workspaceService = workspaceService();
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(aiConfig());
        EffectiveWorkspaceResolver resolver = new EffectiveWorkspaceResolver(directoryService, workspaceService);

        EffectiveWorkspace effective = resolver.resolve("agent-1", "project-1");

        assertThat(effective.ownerType()).isEqualTo(WorkspaceOwnerType.PROJECT);
        assertThat(effective.ownerId()).isEqualTo("project-1");
        assertThat(effective.agentId()).isEqualTo("agent-1");
        assertThat(effective.projectId()).isEqualTo("project-1");
        assertThat(effective.workspaceId()).isNotBlank();
        assertThat(effective.root()).isEqualTo(tempDir.resolve("projects/project-1/workspace").toRealPath());
        assertThat(effective.workDir()).isEqualTo(effective.root().resolve("work"));
        assertThat(effective.outputsDir()).isEqualTo(effective.root().resolve("outputs"));
        assertThat(effective.runsDir()).isEqualTo(effective.root().resolve("runs"));
        assertThat(effective.scratchDir()).isEqualTo(effective.root().resolve("scratch"));

        Workspace workspace = workspaceService.get(effective.workspaceId());
        assertThat(workspace.ownerType()).isEqualTo(WorkspaceOwnerType.PROJECT);
        assertThat(workspace.ownerId()).isEqualTo("project-1");
    }

    @Test
    void effectiveWorkspaceResolverUsesAgentWorkspaceWhenProjectIdIsAbsent() throws Exception {
        WorkspaceService workspaceService = workspaceService();
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(aiConfig());
        EffectiveWorkspaceResolver resolver = new EffectiveWorkspaceResolver(directoryService, workspaceService);

        EffectiveWorkspace effective = resolver.resolve("agent-1", null);

        assertThat(effective.ownerType()).isEqualTo(WorkspaceOwnerType.AGENT);
        assertThat(effective.ownerId()).isEqualTo("agent-1");
        assertThat(effective.projectId()).isNull();
        assertThat(effective.root()).isEqualTo(tempDir.resolve("agents/agent-1/workspace").toRealPath());
        assertThat(workspaceService.get(effective.workspaceId()).ownerType()).isEqualTo(WorkspaceOwnerType.AGENT);
    }

    @Test
    void durableWorkspaceLayoutHelpersCreateExpectedConfinedDirectories() throws Exception {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(aiConfig());
        Path root = service.agentWorkspaceRoot("agent-1");

        assertThat(root).isEqualTo(tempDir.resolve("agents/agent-1/workspace").toRealPath());
        assertThat(service.workDir(root)).isEqualTo(root.resolve("work"));
        assertThat(service.outputsDir(root)).isEqualTo(root.resolve("outputs"));
        assertThat(service.runsDir(root)).isEqualTo(root.resolve("runs"));
        assertThat(service.scratchDir(root)).isEqualTo(root.resolve("scratch"));
        assertThat(Files.isDirectory(root.resolve("work"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("outputs"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("runs"))).isTrue();
        assertThat(Files.isDirectory(root.resolve("scratch"))).isTrue();

        assertThatThrownBy(() -> service.workDir(tempDir.resolve("../escape").normalize()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("workspaceRoot");
    }

    @Test
    void workUnitOutputHelpersCreateTraceablePathsAndValidateSegments() throws Exception {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(aiConfig());
        Path root = service.projectWorkspaceRoot("project-1");

        assertThat(service.taskOutput(root, "task-1", "run-1"))
            .isEqualTo(root.resolve("outputs/tasks/task-1/run-1"));
        assertThat(service.workflowOutput(root, "workflow-1", "run-2"))
            .isEqualTo(root.resolve("outputs/workflows/workflow-1/run-2"));
        assertThat(service.jobAssignmentOutput(root, "assignment-1", "run-3"))
            .isEqualTo(root.resolve("outputs/jobs/assignment-1/run-3"));

        assertThatThrownBy(() -> service.taskOutput(root, "tasks/escape", "run-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("taskId");
        assertThatThrownBy(() -> service.workflowOutput(root, "workflow-1", "../run"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runId");
        assertThatThrownBy(() -> service.jobAssignmentOutput(root, "assignment-1", "run\\bad"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runId");
    }

    private WorkspaceService workspaceService() throws Exception {
        return new WorkspaceService(
            new WorkspaceRepository(new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true))),
            aiConfig()
        );
    }

    private AiConfig aiConfig() {
        return new AiConfig(
            null, null, null, null, null, 10, tempDir, null, Map.of(), Map.of()
        );
    }

    private static String[] invalidSegments() {
        return new String[] {
            "",
            " ",
            ".",
            "..",
            "...",
            "a/b",
            "a\\b",
            "/abs",
            "%2e%2e",
            "a%2fb",
            "a%5cb"
        };
    }
}
