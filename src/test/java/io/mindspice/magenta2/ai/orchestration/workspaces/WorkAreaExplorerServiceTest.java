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

class WorkAreaExplorerServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void listsPreviewsSavesAndCreatesDirectoriesInsideWorkArea() throws Exception {
        TestContext context = context();
        WorkArea home = context.workAreaService().ensureHome(WorkspaceOwnerType.AGENT, "agent-1", null);
        context.explorer().createDirectory(home.id(), "notes");
        context.explorer().saveText(home.id(), "notes/todo.md", "hello\n");

        WorkAreaExplorerService.DirectoryListing listing = context.explorer().list(home.id(), "notes");
        WorkAreaExplorerService.FilePreview preview = context.explorer().preview(home.id(), "notes/todo.md");

        assertThat(listing.entries()).extracting(WorkAreaExplorerService.Entry::path).contains("notes/todo.md");
        assertThat(preview.text()).isTrue();
        assertThat(preview.content()).isEqualTo("hello\n");
        assertThat(Files.readString(tempDir.resolve("data/agents/agent-1/workspace/home/notes/todo.md")))
            .isEqualTo("hello\n");
    }

    @Test
    void rejectsTraversalBinaryTextSaveAndHomeDelete() throws Exception {
        TestContext context = context();
        WorkArea home = context.workAreaService().ensureHome(WorkspaceOwnerType.AGENT, "agent-1", null);

        assertThatThrownBy(() -> context.explorer().list(home.id(), "../outside"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes Work Area");
        assertThatThrownBy(() -> context.explorer().saveText(home.id(), "image.png", "not image"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not safe for text editing");
        assertThatThrownBy(() -> context.explorer().deleteRecursive(home.id(), ".", "home"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Work Area root is protected");
    }

    @Test
    void recursiveDeleteRequiresConfirmationAndRejectsSymlinkEscape() throws Exception {
        TestContext context = context();
        WorkArea home = context.workAreaService().ensureHome(WorkspaceOwnerType.AGENT, "agent-1", null);
        Path deleteMe = Files.createDirectories(tempDir.resolve("data/agents/agent-1/workspace/home/delete-me"));
        Files.writeString(deleteMe.resolve("note.txt"), "x");

        assertThatThrownBy(() -> context.explorer().deleteRecursive(home.id(), "delete-me", "wrong"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("confirmation");
        WorkAreaExplorerService.DeleteResult result = context.explorer()
            .deleteRecursive(home.id(), "delete-me", "delete-me");
        assertThat(result.deletedCount()).isEqualTo(2);
        assertThat(Files.exists(deleteMe)).isFalse();

        Path outside = Files.createDirectories(tempDir.resolve("outside"));
        Path link = tempDir.resolve("data/agents/agent-1/workspace/home/link");
        try {
            Files.createSymbolicLink(link, outside);
        } catch (UnsupportedOperationException exception) {
            return;
        }
        assertThatThrownBy(() -> context.explorer().preview(home.id(), "link"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recursiveDeleteProtectsActiveWorkAreaTargets() throws Exception {
        TestContext context = context();
        WorkArea home = context.workAreaService().ensureHome(WorkspaceOwnerType.AGENT, "agent-1", null);
        context.explorer().createDirectory(home.id(), "parent/active");
        WorkArea active = context.explorer().mark(home.id(), "parent/active", "Active");
        context.jdbc().execute("""
            create table work_assignments (
                selected_work_area_id text,
                output_work_area_id text,
                status text
            )
            """);
        context.jdbc().update(
            "insert into work_assignments(selected_work_area_id, status) values (?, 'QUEUED')",
            active.id()
        );

        assertThatThrownBy(() -> context.explorer().deleteRecursive(home.id(), "parent", "parent"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active Work Area paths are protected");
        assertThatThrownBy(() -> context.explorer().deleteRecursive(active.id(), ".", "active"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Work Area root is protected");
    }

    private TestContext context() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true));
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbc);
        WorkAreaRepository workAreaRepository = new WorkAreaRepository(jdbc);
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
        AiConfig aiConfig = new AiConfig(null, null, null, null, null, 10, dataRoot, null, Map.of(), Map.of());
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(aiConfig);
        WorkspaceService workspaceService = new WorkspaceService(
            workspaceRepository,
            aiConfig,
            new RootRelativePathService(directoryService)
        );
        WorkAreaService workAreaService = new WorkAreaService(workAreaRepository, workspaceService, directoryService);
        return new TestContext(jdbc, workAreaService, new WorkAreaExplorerService(workAreaService));
    }

    private record TestContext(JdbcTemplate jdbc, WorkAreaService workAreaService, WorkAreaExplorerService explorer) {
    }
}
