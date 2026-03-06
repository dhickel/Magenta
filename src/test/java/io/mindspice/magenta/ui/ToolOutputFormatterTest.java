package io.mindspice.magenta.ui;

import io.mindspice.magenta.ui.render.UiStyle;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolOutputFormatterTest {

    @Test
    void readFileCallAndResultUseCompactMetrics() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolCall call = formatter.formatCall("read_file", "{\"path\":\"src/Main.java\"}");
        ToolOutputFormatter.FormattedToolResult result = formatter.formatResult(
                "read_file",
                "{\"status\":\"ok\",\"code\":\"ok\",\"message\":\"done\",\"data\":{\"bytesRead\":128,\"returnedLines\":5,\"totalLines\":99,\"path\":\"src/Main.java\"}}"
        );

        assertThat(call.title()).isEqualTo("tool-call> READ FILE");
        assertThat(call.lines().getFirst()).contains("path=src/Main.java");
        assertThat(result.title()).isEqualTo("tool-result> READ FILE OK");
        assertThat(result.lines().getFirst()).contains("bytes=128").contains("lines=5/99");
        assertThat(result.style()).isEqualTo(UiStyle.INFO);
    }

    @Test
    void sqliteExecAndFailureRenderingAreCompact() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolResult success = formatter.formatResult(
                "sqlite_exec",
                "{\"status\":\"ok\",\"code\":\"ok\",\"message\":\"done\",\"data\":{\"rowsAffected\":7,\"statementCount\":2,\"dbPath\":\"data/app.db\"}}"
        );
        ToolOutputFormatter.FormattedToolResult failed = formatter.formatResult(
                "write_file",
                "{\"status\":\"failed\",\"code\":\"snapshot_mismatch\",\"message\":\"Snapshot mismatch\"}"
        );

        assertThat(success.title()).isEqualTo("tool-result> SQL EXEC OK");
        assertThat(success.lines().getFirst()).contains("rowsAffected=7").contains("statements=2");
        assertThat(failed.title()).isEqualTo("tool-result> WRITE FILE FAILED");
        assertThat(failed.lines().getFirst()).contains("code=snapshot_mismatch");
        assertThat(failed.style()).isEqualTo(UiStyle.ERROR);
    }

    @Test
    void deleteFileCallAndResultUseDeleteMetrics() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolCall call = formatter.formatCall("delete_file", "{\"path\":\"tmp/remove.txt\"}");
        ToolOutputFormatter.FormattedToolResult result = formatter.formatResult(
                "delete_file",
                "{\"status\":\"ok\",\"code\":\"ok\",\"message\":\"done\",\"data\":{\"bytesDeleted\":64,\"path\":\"tmp/remove.txt\"}}"
        );

        assertThat(call.title()).isEqualTo("tool-call> DELETE FILE");
        assertThat(call.lines().getFirst()).contains("path=tmp/remove.txt");
        assertThat(result.title()).isEqualTo("tool-result> DELETE FILE OK");
        assertThat(result.lines().getFirst()).contains("bytes=64").contains("path=tmp/remove.txt");
    }
}
