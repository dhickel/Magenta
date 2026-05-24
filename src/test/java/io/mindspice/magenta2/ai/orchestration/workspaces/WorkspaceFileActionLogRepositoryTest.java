package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFileActionLogRepositoryTest {

    @Test
    void recordsWorkspaceFileActionsWithoutAbsolutePathsOrContent() {
        JdbcTemplate jdbc = jdbc();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        saveWorkspace(workspaceRepository);
        WorkspaceFileActionLogRepository repository = new WorkspaceFileActionLogRepository(jdbc);
        WorkArea workArea = workArea();

        WorkspaceFileActionRecord record = repository.record(
            workArea,
            "web",
            "user-1",
            WorkspaceFileActionType.RENAME,
            "home/a.txt",
            "home/b.txt",
            "SUCCEEDED",
            "{\"kind\":\"file\"}"
        );

        assertThat(record.workspaceId()).isEqualTo("workspace-1");
        assertThat(record.ownerType()).isEqualTo(WorkspaceOwnerType.AGENT);
        assertThat(record.actionType()).isEqualTo(WorkspaceFileActionType.RENAME);
        assertThat(record.sourceRelativePath()).isEqualTo("home/a.txt");
        assertThat(record.targetRelativePath()).isEqualTo("home/b.txt");
        assertThat(record.sourceRelativePath()).doesNotStartWith("/");
        assertThat(record.payloadJson()).doesNotContain("contents");
        assertThat(repository.recentForWorkspace("workspace-1", 10)).hasSize(1);
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
