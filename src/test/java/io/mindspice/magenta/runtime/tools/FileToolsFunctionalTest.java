package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FileToolsFunctionalTest {

    @TempDir
    Path tempDir;

    @Test
    void grepFilesSupportsCaseInsensitivePatternAndFileGlob() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "Hello world\n");
        Files.writeString(tempDir.resolve("b.md"), "hello markdown\n");

        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "pattern", "hello",
                "rootPath", ".",
                "filePattern", "*.txt",
                "caseSensitive", false
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("grep_files", args)));
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("matchCount").asInt()).isEqualTo(1);
        assertThat(payload.path("data").path("matches").get(0).path("path").asText()).isEqualTo("a.txt");
    }

    @Test
    void grepFilesRejectsInvalidRegex() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "pattern", "[abc",
                "regex", true
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("grep_files", args)));
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void grepFilesDefaultsRootPathToWorkspaceDot() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "hello world\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "pattern", "hello"
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("grep_files", args)));
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("rootPath").asText()).isEqualTo(".");
        assertThat(payload.path("data").path("matchCount").asInt()).isEqualTo(1);
    }

    @Test
    void grepFilesBasenameFilePatternMatchesNestedFiles() throws Exception {
        Files.createDirectories(tempDir.resolve("nested"));
        Files.writeString(tempDir.resolve("nested/fractal.lisp"), "(defun draw-fractal ())\n");

        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "pattern", "draw-fractal",
                "rootPath", ".",
                "filePattern", "fractal.lisp"
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("grep_files", args)));
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("matchCount").asInt()).isEqualTo(1);
        assertThat(payload.path("data").path("matches").get(0).path("path").asText()).isEqualTo("nested/fractal.lisp");
    }

    @Test
    void writeFileEnforcesOverwriteGuardAndSnapshotMatching() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode firstWrite = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "write_file",
                "{\"path\":\"note.txt\",\"content\":\"alpha\"}"
        )));
        assertThat(firstWrite.path("status").asText()).isEqualTo("ok");
        String snapshotId = firstWrite.path("data").path("snapshotId").asText();

        JsonNode overwriteGuard = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "write_file",
                "{\"path\":\"note.txt\",\"content\":\"beta\"}"
        )));
        assertThat(overwriteGuard.path("code").asText()).isEqualTo("overwrite_guard");

        JsonNode snapshotMismatch = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "write_file",
                "{\"path\":\"note.txt\",\"content\":\"beta\",\"overwrite\":true,\"expectedSnapshotId\":\"bad\"}"
        )));
        assertThat(snapshotMismatch.path("code").asText()).isEqualTo("snapshot_mismatch");

        JsonNode overwrite = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "write_file",
                ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                        "path", "note.txt",
                        "content", "gamma",
                        "overwrite", true,
                        "expectedSnapshotId", snapshotId
                ))
        )));
        assertThat(overwrite.path("status").asText()).isEqualTo("ok");
        assertThat(Files.readString(tempDir.resolve("note.txt"))).isEqualTo("gamma");
    }

    @Test
    void deleteFileDeletesWithSnapshotGuard() throws Exception {
        Files.writeString(tempDir.resolve("delete-me.txt"), "goodbye");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "read_file",
                "{\"path\":\"delete-me.txt\"}"
        )));
        String snapshotId = read.path("data").path("snapshotId").asText();

        JsonNode deleted = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "delete_file",
                ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                        "path", "delete-me.txt",
                        "expectedSnapshotId", snapshotId
                ))
        )));

        assertThat(deleted.path("status").asText()).isEqualTo("ok");
        assertThat(deleted.path("data").path("bytesDeleted").asLong()).isGreaterThan(0L);
        assertThat(Files.exists(tempDir.resolve("delete-me.txt"))).isFalse();
    }

    @Test
    void deleteFileFailsWhenSnapshotMismatches() throws Exception {
        Files.writeString(tempDir.resolve("guarded.txt"), "v1");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "delete_file",
                "{\"path\":\"guarded.txt\",\"expectedSnapshotId\":\"bad\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("snapshot_mismatch");
        assertThat(payload.path("data").path("currentSnapshotId").asText()).isNotBlank();
        assertThat(Files.exists(tempDir.resolve("guarded.txt"))).isTrue();
    }

    @Test
    void deleteFileRequiresPathArgument() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "delete_file",
                "{}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void readFileRejectsInvalidLineRange() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo\n");

        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "read_file",
                "{\"path\":\"sample.txt\",\"startLine\":3,\"endLine\":2}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void readFileReturnsBytesReadMetric() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "one\ntwo\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "read_file",
                "{\"path\":\"sample.txt\",\"startLine\":1,\"endLine\":2}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("bytesRead").asInt()).isEqualTo("one\ntwo".getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void searchReplaceReturnsConflictForExpectedTextMismatch() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("read_file", "{\"path\":\"sample.txt\"}")));
        String snapshotId = read.path("data").path("snapshotId").asText();
        String anchor = read.path("data").path("lines").get(0).path("anchor").asText();

        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", anchor,
                        "endAnchor", anchor,
                        "expected", "wrong",
                        "replacement", "omega"
                ))
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("search_replace", args)));
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("anchor_mismatch");
        assertThat(payload.path("data").path("requiredAction").asText()).isEqualTo("read_file_refresh");
        assertThat(payload.path("data").path("recoveryHint").asText()).contains("Run read_file");
        JsonNode conflict = payload.path("data").path("conflicts").get(0);
        assertThat(conflict.path("reason").asText()).isEqualTo("expected_text_mismatch");
        assertThat(conflict.path("requiredAction").asText()).isEqualTo("read_file_refresh");
        assertThat(conflict.path("recoveryHint").asText()).contains("inclusive anchors");
        assertThat(conflict.path("actualSlicePreview").asText()).isEqualTo("alpha");
    }

    @Test
    void searchReplaceAcceptsMinimalEditAliases() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("read_file", "{\"path\":\"sample.txt\"}")));
        String snapshotId = read.path("data").path("snapshotId").asText();
        String startAnchor = read.path("data").path("lines").get(0).path("anchor").asText();
        String endAnchor = read.path("data").path("lines").get(1).path("anchor").asText();

        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "start", startAnchor,
                        "end", endAnchor,
                        "text", "omega"
                ))
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("search_replace", args)));
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("appliedEdits").asInt()).isEqualTo(1);
        assertThat(Files.readString(tempDir.resolve("sample.txt"))).isEqualTo("omega");
    }

    @Test
    void searchReplaceRejectsInvalidAnchorFormat() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("read_file", "{\"path\":\"sample.txt\"}")));
        String snapshotId = read.path("data").path("snapshotId").asText();

        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", "1:abc",
                        "endAnchor", "1:abc",
                        "replacement", "omega"
                ))
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("search_replace", args)));
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("anchor_mismatch");
        assertThat(payload.path("message").asText()).isEqualTo("Anchor format must be line:hh");
        assertThat(payload.path("data").path("conflicts").get(0).path("reason").asText()).isEqualTo("invalid_anchor");
    }

    @Test
    void searchReplaceReturnsHashDetailsForStartAnchorMismatch() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("read_file", "{\"path\":\"sample.txt\"}")));
        String snapshotId = read.path("data").path("snapshotId").asText();
        String startAnchor = read.path("data").path("lines").get(0).path("anchor").asText();
        String endAnchor = read.path("data").path("lines").get(1).path("anchor").asText();
        String badStartAnchor = mutateAnchorHash(startAnchor);

        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", badStartAnchor,
                        "endAnchor", endAnchor,
                        "replacement", "omega\nbeta"
                ))
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("search_replace", args)));
        JsonNode conflict = payload.path("data").path("conflicts").get(0);
        String expectedHash = badStartAnchor.substring(badStartAnchor.indexOf(':') + 1);
        String actualHash = startAnchor.substring(startAnchor.indexOf(':') + 1);

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("anchor_mismatch");
        assertThat(payload.path("message").asText()).contains("startAnchor hash mismatch at line 1")
                .contains("expected " + expectedHash)
                .contains("actual " + actualHash);
        assertThat(conflict.path("reason").asText()).isEqualTo("start_anchor_mismatch");
        assertThat(conflict.path("lineNumber").asInt()).isEqualTo(1);
        assertThat(conflict.path("expectedHash").asText()).isEqualTo(expectedHash);
        assertThat(conflict.path("actualHash").asText()).isEqualTo(actualHash);
    }

    @Test
    void searchReplaceReturnsHashDetailsForEndAnchorMismatch() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode read = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("read_file", "{\"path\":\"sample.txt\"}")));
        String snapshotId = read.path("data").path("snapshotId").asText();
        String startAnchor = read.path("data").path("lines").get(0).path("anchor").asText();
        String endAnchor = read.path("data").path("lines").get(1).path("anchor").asText();
        String badEndAnchor = mutateAnchorHash(endAnchor);

        String args = ToolTestSupport.MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", startAnchor,
                        "endAnchor", badEndAnchor,
                        "replacement", "alpha\nomega"
                ))
        ));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request("search_replace", args)));
        JsonNode conflict = payload.path("data").path("conflicts").get(0);
        String expectedHash = badEndAnchor.substring(badEndAnchor.indexOf(':') + 1);
        String actualHash = endAnchor.substring(endAnchor.indexOf(':') + 1);

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("anchor_mismatch");
        assertThat(payload.path("message").asText()).contains("endAnchor hash mismatch at line 2")
                .contains("expected " + expectedHash)
                .contains("actual " + actualHash);
        assertThat(conflict.path("reason").asText()).isEqualTo("end_anchor_mismatch");
        assertThat(conflict.path("lineNumber").asInt()).isEqualTo(2);
        assertThat(conflict.path("expectedHash").asText()).isEqualTo(expectedHash);
        assertThat(conflict.path("actualHash").asText()).isEqualTo(actualHash);
    }

    private static String mutateAnchorHash(String anchor) {
        int separator = anchor.indexOf(':');
        if (separator <= 0 || separator >= anchor.length() - 1) {
            throw new IllegalArgumentException("Invalid anchor: " + anchor);
        }
        String prefix = anchor.substring(0, separator + 1);
        String hash = anchor.substring(separator + 1);
        char last = hash.charAt(hash.length() - 1);
        char replacement = last == '0' ? '1' : '0';
        return prefix + hash.substring(0, hash.length() - 1) + replacement;
    }

    @Test
    void listDirectoryReturnsBoundedEntries() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "a");
        Files.writeString(tempDir.resolve("b.txt"), "b");
        Files.writeString(tempDir.resolve(".hidden"), "h");

        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "list_directory",
                "{\"path\":\".\",\"maxEntries\":1,\"includeHidden\":false}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("entryCount").asInt()).isEqualTo(1);
        assertThat(payload.path("data").path("truncated").asBoolean()).isTrue();
        assertThat(payload.path("data").path("entries").get(0).path("name").asText()).doesNotStartWith(".");
    }

    @Test
    void listDirectoryRejectsNonPositiveMaxEntries() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "list_directory",
                "{\"path\":\".\",\"maxEntries\":0}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void listDirectoryRejectsFilePath() throws Exception {
        Files.writeString(tempDir.resolve("not-a-dir.txt"), "x");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "list_directory",
                "{\"path\":\"not-a-dir.txt\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void fileMetadataReturnsStatFields() throws Exception {
        Files.writeString(tempDir.resolve("meta.txt"), "metadata");
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));

        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "file_metadata",
                "{\"path\":\"meta.txt\"}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("regularFile").asBoolean()).isTrue();
        assertThat(payload.path("data").path("sizeBytes").asLong()).isGreaterThan(0L);
        assertThat(payload.path("data").path("readable").asBoolean()).isTrue();
    }

    @Test
    void fileMetadataRequiresPathArgument() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        JsonNode payload = ToolTestSupport.payload(manager.execute(ToolTestSupport.request(
                "file_metadata",
                "{}"
        )));

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }
}
