package io.mindspice.magenta2.ai.orchestration.docker;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.assertThat;

class DockerRuntimeClientTest {

    @Test
    void configDefaults_matchExpectedValues() {
        DockerRuntimeConfig config = new DockerRuntimeConfig();
        // Defaults before property injection
        assertThat(config.getAgentImage()).isEqualTo("python:3.11");
        assertThat(config.getExecTimeoutSeconds()).isEqualTo(600);
        assertThat(config.getAgentIdleTtlSeconds()).isEqualTo(1800);
        assertThat(config.isKeepContainersOnShutdown()).isFalse();
        assertThat(config.isSelinuxRelabel()).isTrue();
    }

    @Test
    void configSetters_overrideDefaults() {
        DockerRuntimeConfig config = new DockerRuntimeConfig();
        config.setAgentImage("alpine:latest");
        config.setExecTimeoutSeconds(120);
        config.setAgentIdleTtlSeconds(90);
        config.setKeepContainersOnShutdown(true);
        config.setSelinuxRelabel(false);
        config.setHost("unix:///custom/socket");

        assertThat(config.getAgentImage()).isEqualTo("alpine:latest");
        assertThat(config.getExecTimeoutSeconds()).isEqualTo(120);
        assertThat(config.getAgentIdleTtlSeconds()).isEqualTo(90);
        assertThat(config.isKeepContainersOnShutdown()).isTrue();
        assertThat(config.isSelinuxRelabel()).isFalse();
        // host property is reflected when DOCKER_HOST is unset
        assertThat(config.getDockerHost()).isEqualTo("unix:///custom/socket");
    }

    @Test
    void configDockerHostEnvVarTakesPrecedence() {
        DockerRuntimeConfig config = new DockerRuntimeConfig();
        config.setHost("unix:///property/socket");

        // Without DOCKER_HOST env var, property wins
        assertThat(config.getDockerHost()).isEqualTo("unix:///property/socket");
    }

    @Test
    void mountPair_defaultModeIsRw() {
        var m = new DockerRuntimeClient.MountPair(
            java.nio.file.Path.of("/host"), "/container", "invalid");
        assertThat(m.mode()).isEqualTo("rw");
    }

    @Test
    void mountPair_readOnlyFactory() {
        var m = DockerRuntimeClient.MountPair.readOnly(
            java.nio.file.Path.of("/host"), "/container");
        assertThat(m.mode()).isEqualTo("ro");
    }

    @Test
    void mountPair_readWriteFactory() {
        var m = DockerRuntimeClient.MountPair.readWrite(
            java.nio.file.Path.of("/host"), "/container");
        assertThat(m.mode()).isEqualTo("rw");
    }

    @Test
    void execResult_successOnZeroExit() {
        var result = new DockerRuntimeClient.ExecResult(
            0, "output", "", DockerRuntimeClient.InspectContainerState.EXITED, "abc123");
        assertThat(result.success()).isTrue();
    }

    @Test
    void execResult_failureOnNonZeroExit() {
        var result = new DockerRuntimeClient.ExecResult(
            1, "", "error", DockerRuntimeClient.InspectContainerState.EXITED, "abc123");
        assertThat(result.success()).isFalse();
    }

    @Test
    void execResult_failureOnTimedOut() {
        var result = new DockerRuntimeClient.ExecResult(
            -1, "partial output", "",
            DockerRuntimeClient.InspectContainerState.TIMED_OUT, "abc123");
        assertThat(result.success()).isFalse();
        assertThat(result.state()).isEqualTo(DockerRuntimeClient.InspectContainerState.TIMED_OUT);
    }

    @Test
    void execResult_combinedOutput() {
        var result = new DockerRuntimeClient.ExecResult(
            0, "stdout", "stderr", DockerRuntimeClient.InspectContainerState.EXITED, "abc123");
        assertThat(result.combinedOutput()).contains("stdout");
        assertThat(result.combinedOutput()).contains("stderr");
    }

    @Test
    void execResult_combinedOutputBlankStderr() {
        var result = new DockerRuntimeClient.ExecResult(
            0, "stdout", "   ", DockerRuntimeClient.InspectContainerState.EXITED, "abc123");
        assertThat(result.combinedOutput()).isEqualTo("stdout");
    }

    @Test
    void execResult_combinedOutputBlankStdout() {
        var result = new DockerRuntimeClient.ExecResult(
            0, "", "stderr", DockerRuntimeClient.InspectContainerState.EXITED, "abc123");
        assertThat(result.combinedOutput()).isEqualTo("stderr");
    }

    // Full integration tests requiring a live Podman/Docker daemon.
    // These are documented here for manual execution.
    //
    // @Test
    // @DisabledIfEnvironmentVariable(named = "DOCKER_HOST", matches = "")
    // void liveDaemon_pingSucceedsWhenAvailable()
    //
    // @Test
    // @DisabledIfEnvironmentVariable(named = "DOCKER_HOST", matches = "")
    // void liveDaemon_execCommandTimeoutStopsContainer()
    //   — execute "sleep 9999" with exec-timeout-seconds=5,
    //     verify elapsed time < 25s (timeout + stop + grace),
    //     verify container is removed.
}
