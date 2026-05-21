package io.mindspice.magenta2.ai.chat.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.mindspice.magenta2.ai.chat.model.ChatFileSummary;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatFileServiceTest {

    private static final String CONVERSATION_ID = "00000000-0000-0000-0000-000000000001";

    @TempDir
    Path tempDir;

    @Test
    void countsZeroWhenDirectoryIsEmpty() throws Exception {
        ChatFileService service = service();

        assertThat(service.countFiles(CONVERSATION_ID)).isZero();
        assertThat(service.listFiles(CONVERSATION_ID).files()).isEmpty();
    }

    @Test
    void countsAndListsNestedRegularFilesInStableOrder() throws Exception {
        ChatFileService service = service();
        Path root = chatFilesRoot();
        Files.writeString(root.resolve("summary.md"), "# Summary");
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/data.json"), "{\"ok\":true}");
        Files.createDirectories(root.resolve("empty-dir"));

        var listing = service.listFiles(CONVERSATION_ID);

        assertThat(service.countFiles(CONVERSATION_ID)).isEqualTo(2);
        assertThat(listing.count()).isEqualTo(2);
        assertThat(listing.truncated()).isFalse();
        assertThat(listing.files())
            .extracting(ChatFileSummary::relativePath)
            .containsExactly("nested/data.json", "summary.md");
        assertThat(listing.files())
            .extracting(ChatFileSummary::formatLabel)
            .containsExactly("JSON", "Markdown");
    }

    @Test
    void listsCountsAndDownloadsSeededCopiedChatFile() throws Exception {
        Path newDataRoot = Files.createDirectories(tempDir.resolve("new-magenta-root/root"));
        ChatFileService service = service(newDataRoot);
        Path seeded = Files.createDirectories(newDataRoot.resolve("chats/" + CONVERSATION_ID + "/files/nested"))
            .resolve("seeded.md");
        Files.writeString(seeded, "# Seeded");

        var listing = service.listFiles(CONVERSATION_ID);

        assertThat(service.countFiles(CONVERSATION_ID)).isEqualTo(1);
        assertThat(listing.count()).isEqualTo(1);
        assertThat(listing.files())
            .singleElement()
            .satisfies(file -> {
                assertThat(file.relativePath()).isEqualTo("nested/seeded.md");
                assertThat(file.relativePath()).doesNotContain(newDataRoot.toString());
            });
        Path download = service.resolveDownload(CONVERSATION_ID, "nested/seeded.md");
        assertThat(download).isEqualTo(seeded.toRealPath());
        assertThat(Files.readString(download)).isEqualTo("# Seeded");
    }

    @Test
    void labelsExtensionlessFilesAsFile() throws Exception {
        ChatFileService service = service();
        Files.writeString(chatFilesRoot().resolve("README"), "hello");

        List<ChatFileSummary> files = service.listFiles(CONVERSATION_ID).files();

        assertThat(files).hasSize(1);
        assertThat(files.get(0).extension()).isEmpty();
        assertThat(files.get(0).formatLabel()).isEqualTo("file");
    }

    @Test
    void rejectsTraversalForDownloadResolution() throws Exception {
        ChatFileService service = service();
        Files.writeString(tempDir.resolve("outside.txt"), "outside");

        assertThatThrownBy(() -> service.resolveDownload(CONVERSATION_ID, "../outside.txt"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes");
    }

    @Test
    void resolvesNestedDownloadInsideChatFiles() throws Exception {
        ChatFileService service = service();
        Path root = chatFilesRoot();
        Files.createDirectories(root.resolve("nested"));
        Path expected = root.resolve("nested/data.csv");
        Files.writeString(expected, "a,b");

        assertThat(service.resolveDownload(CONVERSATION_ID, "nested/data.csv")).isEqualTo(expected.toRealPath());
    }

    private ChatFileService service() throws Exception {
        return service(Files.createDirectories(tempDir.resolve("data")));
    }

    private ChatFileService service(Path dataRoot) throws Exception {
        return new ChatFileService(new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null)
        ));
    }

    private Path chatFilesRoot() throws Exception {
        Path root = tempDir.resolve("data/chats/" + CONVERSATION_ID + "/files");
        return Files.createDirectories(root);
    }
}
