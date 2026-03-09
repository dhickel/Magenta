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

        assertThat(call.title()).isEqualTo("[Tool] Read File");
        assertThat(call.lines()).containsExactly("Path: src/Main.java");
        assertThat(result.title()).isEqualTo("[Tool] Read File OK");
        assertThat(result.lines()).containsExactly("Path: src/Main.java", "Lines: 5/99");
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

        assertThat(success.title()).isEqualTo("[Tool] SQL Exec OK");
        assertThat(success.lines()).containsExactly("Database: data/app.db", "Rows Affected: 7");
        assertThat(failed.title()).isEqualTo("[Tool] Write File FAILED");
        assertThat(failed.lines()).contains("Code: snapshot_mismatch", "Message: Snapshot mismatch");
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

        assertThat(call.title()).isEqualTo("[Tool] Delete File");
        assertThat(call.lines()).containsExactly("Path: tmp/remove.txt");
        assertThat(result.title()).isEqualTo("[Tool] Delete File OK");
        assertThat(result.lines()).containsExactly("Path: tmp/remove.txt", "Bytes Deleted: 64");
    }

    @Test
    void shellCallUsesCmdArgumentAndFailureShowsCommandContext() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolCall call = formatter.formatCall(
                "shell_command",
                "{\"cmd\":\"python generate_random_number.py\"}"
        );
        ToolOutputFormatter.FormattedToolResult failed = formatter.formatResult(
                "shell_command",
                "{\"status\":\"failed\",\"code\":\"command_failed\",\"message\":\"Command failed\",\"data\":{\"command\":\"python generate_random_number.py\",\"exitCode\":1}}"
        );

        assertThat(call.title()).isEqualTo("[Tool] Shell");
        assertThat(call.lines()).containsExactly("Command: python generate_random_number.py");
        assertThat(failed.title()).isEqualTo("[Tool] Shell FAILED");
        assertThat(failed.lines()).contains("Code: command_failed", "Command: python generate_random_number.py");
    }

    @Test
    void searchReplaceFailureIncludesConflictReason() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolResult failed = formatter.formatResult(
                "search_replace",
                "{\"status\":\"failed\",\"code\":\"anchor_mismatch\",\"message\":\"Anchor format must be line:hh\",\"data\":{\"path\":\"generate_random_number.py\",\"conflicts\":[{\"reason\":\"invalid_anchor\"}]}}"
        );

        assertThat(failed.title()).isEqualTo("[Tool] Search Replace FAILED");
        assertThat(failed.lines()).contains(
                "Code: anchor_mismatch",
                "Path: generate_random_number.py",
                "Conflict: invalid_anchor",
                "Message: Anchor format must be line:hh"
        );
    }

    @Test
    void grepResultIncludesScannedAndMatchCounts() {
        ToolOutputFormatter formatter = new ToolOutputFormatter();

        ToolOutputFormatter.FormattedToolResult result = formatter.formatResult(
                "grep_files",
                "{\"status\":\"ok\",\"code\":\"ok\",\"message\":\"done\",\"data\":{\"rootPath\":\".\",\"scannedFiles\":8,\"matchCount\":0}}"
        );

        assertThat(result.title()).isEqualTo("[Tool] Grep Files OK");
        assertThat(result.lines()).containsExactly("Root: .", "Scanned: 8", "Matches: 0");
    }
}
