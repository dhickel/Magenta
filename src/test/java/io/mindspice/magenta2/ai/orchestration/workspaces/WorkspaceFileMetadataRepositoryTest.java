package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceFileMetadataRepositoryTest {

    @Test
    void createsSchemaAndSeedsSystemLabelsIdempotently() {
        JdbcTemplate jdbc = jdbc();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        saveWorkspace(workspaceRepository);

        WorkspaceFileMetadataRepository repository = new WorkspaceFileMetadataRepository(jdbc);
        new WorkspaceFileMetadataRepository(jdbc);

        assertThat(columns(jdbc, "workspace_file_labels"))
            .contains("slug", "display_name", "system_flag");
        assertThat(columns(jdbc, "workspace_file_label_assignments"))
            .contains("workspace_id", "root_relative_path", "file_relative_path", "label_id");
        assertThat(repository.ensureLabel("note", "Note", true).system()).isTrue();
        assertThat(repository.ensureLabel("work-area", "Work Area", true).system()).isTrue();
    }

    @Test
    void addsListsMovesCopiesAndDeletesLabels() {
        JdbcTemplate jdbc = jdbc();
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        saveWorkspace(workspaceRepository);
        WorkspaceFileMetadataRepository repository = new WorkspaceFileMetadataRepository(jdbc);
        WorkArea workArea = workArea();

        WorkspaceFileLabel custom = repository.ensureLabel("Project Alpha", "Project Alpha", false);
        assertThat(custom.slug()).isEqualTo("project-alpha");
        assertThat(custom.displayName()).isEqualTo("Project Alpha");
        assertThat(custom.system()).isFalse();
        WorkspaceFileLabel typed = repository.ensureLabel(
            "file-review",
            "File Review",
            false,
            WorkspaceFileLabelTargetType.FILE,
            "Use for files that need LLM review."
        );
        assertThat(typed.metadataJson()).contains("\"targetType\":\"file\"");
        assertThat(typed.metadataJson()).contains("\"description\":\"Use for files that need LLM review.\"");
        assertThat(repository.listLabels("review", 10))
            .extracting(WorkspaceFileLabel::slug)
            .contains("file-review");

        repository.addLabel(workArea, "home/notes", "home/notes", "project-alpha");
        assertThat(repository.labelsForPath("workspace-1", "home/notes"))
            .extracting(a -> a.label().slug())
            .containsExactly("project-alpha");

        repository.addLabel(workArea, "home/notes/a.md", "home/notes/a.md", "note");
        assertThat(repository.labelsForPath("workspace-1", "home/notes/a.md"))
            .extracting(a -> a.label().slug())
            .containsExactly("note");

        repository.moveSubtree(workArea, "home/notes", "home/archive");
        assertThat(repository.labelsForPath("workspace-1", "home/archive"))
            .extracting(a -> a.label().slug())
            .containsExactly("project-alpha");
        assertThat(repository.labelsForPath("workspace-1", "home/archive/a.md"))
            .extracting(a -> a.label().slug())
            .containsExactly("note");

        repository.copySubtree(workArea, "home/archive", "home/copy");
        assertThat(repository.labelsForPath("workspace-1", "home/copy"))
            .extracting(a -> a.label().slug())
            .containsExactly("project-alpha");
        assertThat(repository.labelsForPath("workspace-1", "home/copy/a.md"))
            .extracting(a -> a.label().slug())
            .containsExactly("note");

        assertThat(repository.deleteSubtree("workspace-1", "home/archive")).isEqualTo(2);
        assertThat(repository.labelsForPath("workspace-1", "home/archive")).isEmpty();
        assertThat(repository.labelsForPath("workspace-1", "home/archive/a.md")).isEmpty();
        assertThat(repository.labelsForPath("workspace-1", "home/copy")).hasSize(1);
        assertThat(repository.labelsForPath("workspace-1", "home/copy/a.md")).hasSize(1);
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

    private java.util.List<String> columns(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("select name from pragma_table_info('" + table + "')", String.class);
    }
}
