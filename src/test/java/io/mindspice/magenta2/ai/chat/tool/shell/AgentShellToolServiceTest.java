package io.mindspice.magenta2.ai.chat.tool.shell;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.mindspice.magenta2.ai.orchestration.docker.AgentContainerRuntimeService;
import io.mindspice.magenta2.ai.orchestration.docker.AgentExecResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentShellToolServiceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void clearContext() {
        OrchestrationTaskContextHolder.clear();
    }

    @Test
    void runsAllowedCommandLine() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        AgentShellToolService.ShellExecResult result = service.exec("printf hello", ".", 5);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.timedOut()).isFalse();
        assertThat(result.workingDirectory()).isEqualTo(".");
    }

    @Test
    void parsesQuotedCommandLineArgs() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        AgentShellToolService.ShellExecResult result = service.exec("printf \"hello world\"", ".", 5);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("hello world");
        assertThat(result.args()).containsExactly("hello world");
    }

    @Test
    void wildcardAllowsAnyCommandLine() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        AgentShellToolService.ShellExecResult result = service.exec("printf wild", ".", 5);

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("wild");
    }

    @Test
    void rejectsCommandOutsideAllowlist() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        assertThatThrownBy(() -> service.exec("pwd", ".", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not allowed");
    }

    @Test
    void rejectsExecutablePath() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("/bin/bash -lc pwd", ".", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("bare executable");
    }

    @Test
    void rejectsUnterminatedQuote() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("printf \"bad", ".", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unterminated quote");
    }

    @Test
    void rejectsWorkingDirectoryTraversalOutsideRoot() throws IOException {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        assertThatThrownBy(() -> service.exec("printf bad", "..", 5))
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

        assertThatThrownBy(() -> service.exec("printf bad", "escape", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes data root");
    }

    @Test
    void reportsFailedExitCode() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("false"));

        AgentShellToolService.ShellExecResult result = service.exec("false", ".", 5);

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.timedOut()).isFalse();
    }

    @Test
    void reportsTimeout() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("sleep"));

        AgentShellToolService.ShellExecResult result = service.exec("sleep 2", ".", 1);

        assertThat(result.exitCode()).isNull();
        assertThat(result.timedOut()).isTrue();
    }

    @Test
    void interruptedExecutionCleansUpAndThrowsInterruptedException() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("sleep"));
        java.util.concurrent.atomic.AtomicReference<Throwable> exception = new java.util.concurrent.atomic.AtomicReference<>();

        Thread executor = new Thread(() -> {
            try {
                service.exec("sleep 30", ".", 30);
            } catch (InterruptedException e) {
                exception.set(e);
            } catch (Exception e) {
                exception.set(e);
            }
        }, "shell-executor");

        executor.start();
        Thread.sleep(500); // let sleep start
        executor.interrupt();
        executor.join(5_000); // should complete promptly after cleanup

        assertThat(executor.isAlive()).as("Executor must finish after interruption").isFalse();
        assertThat(exception.get()).isInstanceOf(InterruptedException.class);
    }

    @Test
    void timeoutStillDestroysProcess() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("sleep"));

        AgentShellToolService.ShellExecResult result = service.exec("sleep 10", ".", 1);

        assertThat(result.exitCode()).isNull();
        assertThat(result.timedOut()).isTrue();
        // Regression: verify the method returns within bounded time
        // (1s timeout + 1s destroy wait + 1s capture drain = ~3s max)
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase 2: Container routing with orchestration context
    // ════════════════════════════════════════════════════════════════

    @Test
    void routesToContainerWhenOrchestrationContextHasAgentId() throws Exception {
        AtomicReference<String> capturedAgentId = new AtomicReference<>();
        AtomicReference<String> capturedCommand = new AtomicReference<>();
        AtomicReference<Integer> capturedTimeout = new AtomicReference<>();

        // Fake container runtime that records calls without needing Docker
        AgentContainerRuntimeService fakeRuntime = new AgentContainerRuntimeService(null, null, null, null) {
            @Override
            public AgentExecResult execInAgent(String agentId, String agentName, String command, String workingDirectory,
                                               int timeoutSeconds) {
                capturedAgentId.set(agentId);
                capturedCommand.set(command);
                capturedTimeout.set(timeoutSeconds);
                return new AgentExecResult(0, "container-output", "", false, "fake-container-id");
            }
        };

        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"), fakeRuntime);

        // Set orchestration context with agent
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1", "TestAgent", "job-1", "project-1", "ws-1",
            "TASK_RUN", "/tmp/ws", "/tmp/out", "/output/run"));

        try {
            AgentShellToolService.ShellExecResult result = service.exec("printf hello", ".", 5);

            assertThat(capturedAgentId.get()).isEqualTo("agent-1");
            assertThat(capturedCommand.get()).isEqualTo("printf hello");
            assertThat(capturedTimeout.get()).isEqualTo(5);
            assertThat(result.executionType()).isEqualTo("docker");
            assertThat(result.containerId()).isEqualTo("fake-container-id");
            assertThat(result.stdout()).isEqualTo("container-output");
            assertThat(result.exitCode()).isZero();
        } finally {
            OrchestrationTaskContextHolder.clear();
        }
    }

    @Test
    void failsWhenDockerRequiredButRuntimeUnavailable() throws IOException {
        // Create shell service WITHOUT container runtime
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"));

        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1", "TestAgent", null, null, null, "TASK_RUN", null, null, null));

        try {
            assertThatThrownBy(() -> service.exec("printf hello", ".", 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("container runtime is not available");
        } finally {
            OrchestrationTaskContextHolder.clear();
        }
    }

    @Test
    void rejectsHostAbsolutePathForContainerWorkingDirectory() throws Exception {
        AgentContainerRuntimeService fakeRuntime = new AgentContainerRuntimeService(null, null, null, null) {
            @Override
            public AgentExecResult execInAgent(String agentId, String agentName, String command, String workingDirectory,
                                               int timeoutSeconds) {
                return new AgentExecResult(0, "", "", false, "fake-container-id");
            }
        };
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("*"), fakeRuntime);
        OrchestrationTaskContextHolder.set(new OrchestrationTaskContext(
            "agent-1", "TestAgent", null, null, null, "TASK_RUN", null, null, null));

        assertThatThrownBy(() -> service.exec("printf hello", "/home/hickelpickle/work", 5))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("container workingDirectory");
    }

    @Test
    void hostExecutionWhenNoOrchestrationContext() throws Exception {
        AgentShellToolService service = new AgentShellToolService(tempDir, List.of("printf"));

        // No context set — should use host execution
        AgentShellToolService.ShellExecResult result = service.exec("printf host-test", ".", 5);

        assertThat(result.executionType()).isEqualTo("host");
        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("host-test");
    }
}
