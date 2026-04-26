package io.mindspice.magenta2.ai.chat.tool.shell;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentShellToolServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void runsAllowedCommandWithStructuredArgs() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        AgentShellToolService.ShellExecResult result = service.exec("printf", List.of("hello"), ".", 5);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.workingDirectory()).isEqualTo(".");
    }

    @Test
    void wildcardAllowsAnyBareCommand() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        AgentShellToolService.ShellExecResult result = service.exec("printf", List.of("wild"), ".", 5);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("wild");
    }

    @Test
    void rejectsCommandOutsideAllowlist() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        assertThatThrownBy(() -> service.exec("pwd", List.of(), ".", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsRawShellCommandText() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("bash -lc", List.of("pwd"), ".", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bare executable");
    }

    @Test
    void rejectsWorkingDirectoryTraversalOutsideRoot() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("printf", List.of("bad"), "..", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes data root");
    }

    @Test
    void rejectsSymlinkWorkingDirectoryEscape() throws IOException {
        Path outside = Files.createDirectories(tempDir.resolveSibling(tempDir.getFileName() + "-outside"));
        try {
            Files.createSymbolicLink(tempDir.resolve("escape"), outside);
        } catch (UnsupportedOperationException | FileSystemException exception) {
            return;
        }

        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("printf", List.of("bad"), "escape", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes data root");
    }

    @Test
    void reportsFailedExitCode() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("false"));

        AgentShellToolService.ShellExecResult result = service.exec("false", List.of(), ".", 5);

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void reportsTimeout() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("sleep"));

        AgentShellToolService.ShellExecResult result = service.exec("sleep", List.of("2"), ".", 1);

        assertThat(result.exitCode()).isNull();
        assertThat(result.timedOut()).isTrue();
    }
}
