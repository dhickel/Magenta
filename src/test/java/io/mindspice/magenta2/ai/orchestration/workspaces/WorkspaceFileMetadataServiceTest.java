package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFileMetadataServiceTest {

    @Test
    void addAndRemoveLabelsWriteActionLogs() {
        JdbcTemplate jdbc = jdbc();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        saveWorkspace(workspaceRepository);
        WorkspaceFileActionLogRepository actionLogRepository = new WorkspaceFileActionLogRepository(jdbc);
        WorkspaceFileMetadataRepository metadataRepository = new WorkspaceFileMetadataRepository(jdbc);
        WorkspaceFileMetadataService service =
            new WorkspaceFileMetadataService(metadataRepository, actionLogRepository);
        WorkArea workArea = workArea();

        WorkspaceFileLabel custom = service.ensureTag("Project Alpha", "Project Alpha");
        assertThat(custom.slug()).isEqualTo("project-alpha");
        assertThat(custom.displayName()).isEqualTo("Project Alpha");

        service.addLabel(workArea, "home/folder", "project-alpha");
        service.addLabel(workArea, "home/note.md", "note");
        service.removeLabel(workArea, "home/note.md", "note");

        assertThat(service.labelsForPath("workspace-1", "home/folder"))
            .extracting(a -> a.label().slug())
            .containsExactly("project-alpha");
        assertThat(actionLogRepository.recentForWorkspace("workspace-1", 10))
            .extracting(WorkspaceFileActionRecord::actionType)
            .containsExactly(
                WorkspaceFileActionType.TAG_REMOVE,
                WorkspaceFileActionType.TAG_ADD,
                WorkspaceFileActionType.TAG_ADD
            );
    }

    @Test
    void followHelpersDelegateToRepository() {
        JdbcTemplate jdbc = jdbc();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        saveWorkspace(workspaceRepository);
        WorkspaceFileActionLogRepository actionLogRepository = new WorkspaceFileActionLogRepository(jdbc);
        WorkspaceFileMetadataRepository metadataRepository = new WorkspaceFileMetadataRepository(jdbc);
        WorkspaceFileMetadataService service =
            new WorkspaceFileMetadataService(metadataRepository, actionLogRepository);
        WorkArea workArea = workArea();

        service.addLabel(workArea, "home/a.txt", "note");
        service.onMove(workArea, "home/a.txt", "home/b.txt");
        service.onCopy(workArea, "home/b.txt", "home/c.txt");
        service.onDelete(workArea, "home/b.txt");

        assertThat(service.labelsForPath("workspace-1", "home/b.txt")).isEmpty();
        assertThat(service.labelsForPath("workspace-1", "home/c.txt"))
            .extracting(a -> a.label().slug())
            .containsExactly("note");
    }

    private WorkArea workArea() {
        return new WorkArea(
            "area-1",
            WorkspaceOwnerType.AGENT,
            "agent-1",
            "workspace-1",
            "agents/agent-1/workspace",
            "home",
            "Home",
            true,
            true,
            true,
            "{}",
            Instant.now(),
            Instant.now()
        );
    }

    private void saveWorkspace(WorkspaceRepository repository) {
        repository.save(new Workspace(
            "workspace-1",
            WorkspaceOwnerType.AGENT,
            "agent-1",
            "agents/agent-1/workspace",
            "Agent 1",
            "{}",
            Instant.now(),
            Instant.now()
        ));
    }

    private JdbcTemplate jdbc() {
        return new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true));
    }
}
