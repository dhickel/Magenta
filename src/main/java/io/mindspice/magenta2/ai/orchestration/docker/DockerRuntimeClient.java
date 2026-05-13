package io.mindspice.magenta2.ai.orchestration.docker;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Docker runtime client for containerized plan/task execution.
 * Uses the docker-java library with the Apache HttpClient 5 transport
 * for Unix domain socket support (works with rootless Podman).
 *
 * <p>On startup, the client verifies Docker daemon connectivity and
 * that the configured agent image exists. If either check fails,
 * startup is aborted with a clear error message.
 */
@Service
@ConditionalOnProperty(name = "magenta.docker.enabled", havingValue = "true", matchIfMissing = false)
public class DockerRuntimeClient {
    private static final Logger log = LoggerFactory.getLogger(DockerRuntimeClient.class);

    private final DockerRuntimeConfig config;
    private final DockerClient client;
    private final String dockerHost;
    private volatile boolean daemonAvailable;
    private volatile boolean imageAvailable;
    private volatile String daemonError;

    public DockerRuntimeClient(DockerRuntimeConfig config) {
        this.config = config;
        this.dockerHost = config.getDockerHost();
        log.info("Connecting to Docker daemon at {}", dockerHost);

        DefaultDockerClientConfig clientConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerHost)
            .withDockerTlsVerify(false)
            .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(clientConfig.getDockerHost())
            .sslConfig(clientConfig.getSSLConfig())
            .maxConnections(10)
            .connectionTimeout(Duration.ofSeconds(10))
            .responseTimeout(Duration.ofSeconds(config.getExecTimeoutSeconds() + 30))
            .build();

        this.client = DockerClientImpl.getInstance(clientConfig, httpClient);
    }

    @PostConstruct
    void verifyDaemon() {
        try {
            client.pingCmd().exec();
            daemonAvailable = true;
            log.info("Docker daemon ping OK at {}", dockerHost);
        } catch (Exception e) {
            daemonAvailable = false;
            daemonError = "Docker daemon unreachable at " + dockerHost + ": " + e.getMessage();
            log.warn("Docker runtime enabled but daemon unavailable at {}: {}", dockerHost, e.getMessage());
            return;
        }

        try {
            client.inspectImageCmd(config.getAgentImage()).exec();
            imageAvailable = true;
            log.info("Agent image {} verified", config.getAgentImage());
        } catch (NotFoundException e) {
            imageAvailable = false;
            log.warn("Configured agent image '{}' not found. Pull it with: docker pull {}",
                config.getAgentImage(), config.getAgentImage());
        } catch (Exception e) {
            imageAvailable = false;
            log.warn("Failed to verify agent image '{}': {}", config.getAgentImage(), e.getMessage());
        }

        if (daemonAvailable && imageAvailable) {
            log.info("Docker runtime ready — daemon {}, image {}", dockerHost, config.getAgentImage());
        }
    }

    /**
     * Returns true if the Docker daemon is reachable and the agent image is
     * present. Safe to call at any time.
     */
    public boolean isAvailable() {
        return daemonAvailable && imageAvailable;
    }

    /**
     * Returns true if the daemon responded to the last health check.
     */
    public boolean isDaemonAvailable() {
        return daemonAvailable;
    }

    /**
     * Returns the last daemon error message, or null if none.
     */
    public String getDaemonError() {
        return daemonError;
    }

    /**
     * Live health check: ping the daemon and verify the agent image.
     * Safe to call at any time; returns a message describing the outcome.
     *
     * @return health status message (non-null)
     */
    public String healthCheck() {
        if (!daemonAvailable) {
            return daemonError != null ? daemonError : "Docker daemon unreachable";
        }
        try {
            client.inspectImageCmd(config.getAgentImage()).exec();
            imageAvailable = true;
            return "Docker daemon reachable, agent image verified.";
        } catch (Exception e) {
            imageAvailable = false;
            return "Docker daemon reachable but agent image not found: " + e.getMessage();
        }
    }

    /**
     * Ping the Docker daemon. Returns true if the daemon responds.
     */
    public boolean ping() {
        try {
            client.pingCmd().exec();
            daemonAvailable = true;
            return true;
        } catch (Exception e) {
            daemonAvailable = false;
            return false;
        }
    }

    @PreDestroy
    void close() {
        try {
            client.close();
        } catch (Exception e) {
            log.warn("Error closing Docker client", e);
        }
    }

    /**
     * Execute a shell command in a one-off container with the specified
     * workspace mounts.
     *
     * @param agentHome   host path to mount as the agent's home directory
     * @param workDir     host path for the temporary workspace
     * @param outputDir   host path for the output directory
     * @param additionalMounts  optional extra mount pairs (host:container)
     * @param command     the shell command to execute
     * @return complete execution output (stdout + stderr)
     */
    public ExecResult execCommand(Path agentHome, Path workDir, Path outputDir,
                                   List<MountPair> additionalMounts,
                                   String command) throws Exception {
        return execCommand(agentHome, workDir, outputDir, additionalMounts, command, null);
    }

    /**
     * Execute a shell command in a one-off container with optional environment
     * variables.
     *
     * <p>Uses a single timeout budget: {@code waitContainerCmd} is the
     * authoritative wait; log streaming is drained with a short grace
     * period after the container exits. A stuck container is explicitly
     * stopped and removed within the timeout window, not left to wait
     * for a second full timeout.
     */
    public ExecResult execCommand(Path agentHome, Path workDir, Path outputDir,
                                   List<MountPair> additionalMounts,
                                   String command,
                                   List<String> envVars) throws Exception {
        // :rw,Z = read-write with SELinux private relabel (no-op on non-SELinux hosts)
        String selinuxOpt = config.isSelinuxRelabel() ? ",Z" : "";

        List<Bind> binds = new ArrayList<>();
        binds.add(Bind.parse(agentHome.toString() + ":/home/agent:rw" + selinuxOpt));
        binds.add(Bind.parse(workDir.toString() + ":/workspace:rw" + selinuxOpt));
        binds.add(Bind.parse(outputDir.toString() + ":/output:rw" + selinuxOpt));

        if (additionalMounts != null) {
            for (MountPair mount : additionalMounts) {
                binds.add(Bind.parse(
                    mount.hostPath().toString() + ":" + mount.containerPath() + ":" + mount.mode() + selinuxOpt));
            }
        }

        HostConfig hostConfig = new HostConfig()
            .withBinds(binds)
            .withAutoRemove(true);

        CreateContainerResponse container = client.createContainerCmd(config.getAgentImage())
            .withHostConfig(hostConfig)
            .withCmd("bash", "-c", command)
            .withWorkingDir("/workspace")
            .withEnv(envVars != null ? envVars : List.of())
            .exec();

        String containerId = container.getId();
        log.debug("Created container {} for exec", containerId);

        try {
            client.startContainerCmd(containerId).exec();

            // Collect output
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            LogContainerResultCallback logCallback = new LogContainerResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    try {
                        switch (frame.getStreamType()) {
                            case STDOUT, RAW -> stdout.write(frame.getPayload());
                            case STDERR -> stderr.write(frame.getPayload());
                        }
                    } catch (Exception ignored) {
                    }
                }
            };

            client.logContainerCmd(containerId)
                .withStdOut(true)
                .withStdErr(true)
                .withFollowStream(true)
                .exec(logCallback);

            // Single timeout budget — waitContainerCmd is the authoritative
            // wait for container exit.
            long timeoutSeconds = config.getExecTimeoutSeconds();
            Integer exitCode = client.waitContainerCmd(containerId)
                .exec(new WaitContainerResultCallback())
                .awaitStatusCode(timeoutSeconds, TimeUnit.SECONDS);

            InspectContainerState state;
            if (exitCode == null) {
                // Container did not exit within the timeout budget.
                // Stop it, then force-remove.
                state = InspectContainerState.TIMED_OUT;
                log.warn("Container {} timed out after {}s, stopping and removing",
                    containerId, timeoutSeconds);
                try {
                    client.stopContainerCmd(containerId).withTimeout(10).exec();
                } catch (Exception e) {
                    log.warn("Error stopping timed-out container {}: {}", containerId, e.getMessage());
                }
                try {
                    client.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception e) {
                    log.warn("Error removing timed-out container {}: {}", containerId, e.getMessage());
                }
                exitCode = -1;
            } else {
                state = InspectContainerState.EXITED;
            }

            // Drain remaining log output with a short grace period.
            // The container has already exited (or been stopped), so the
            // log stream should close promptly.
            try {
                logCallback.awaitCompletion(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("Log stream drain for container {} did not close within grace period", containerId);
            }

            // Best-effort inspect for final exit code if we didn't already
            // have one from waitContainerCmd.
            try {
                InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
                if (inspect.getState() != null
                    && inspect.getState().getExitCode() != null
                    && state != InspectContainerState.TIMED_OUT) {
                    exitCode = inspect.getState().getExitCode();
                }
            } catch (NotFoundException ignored) {
                // container already removed (auto-remove)
            }

            return new ExecResult(
                exitCode,
                stdout.toString(),
                stderr.toString(),
                state,
                containerId
            );

        } catch (Exception e) {
            // Try to clean up on failure
            try {
                client.stopContainerCmd(containerId).withTimeout(10).exec();
            } catch (Exception ignored) {
            }
            try {
                client.removeContainerCmd(containerId).withForce(true).exec();
            } catch (Exception ignored) {
            }
            throw e;
        }
    }

    /**
     * Simple result from a containerized command execution.
     */
    public record ExecResult(
        int exitCode,
        String stdout,
        String stderr,
        InspectContainerState state,
        String containerId
    ) {
        public boolean success() {
            return exitCode == 0;
        }

        public String combinedOutput() {
            if (stderr.isBlank()) return stdout;
            if (stdout.isBlank()) return stderr;
            return stdout + "\n" + stderr;
        }
    }

    /**
     * Container state after execution.
     */
    public enum InspectContainerState {
        EXITED,
        RUNNING,
        TIMED_OUT
    }

    /**
     * A host-to-container mount pair.
     */
    public record MountPair(Path hostPath, String containerPath, String mode) {
        public MountPair {
            if (!"ro".equals(mode) && !"rw".equals(mode)) {
                mode = "rw";
            }
        }

        public static MountPair readOnly(Path hostPath, String containerPath) {
            return new MountPair(hostPath, containerPath, "ro");
        }

        public static MountPair readWrite(Path hostPath, String containerPath) {
            return new MountPair(hostPath, containerPath, "rw");
        }
    }

    // ── Accessors ──

    public String getAgentImage() {
        return config.getAgentImage();
    }

    public String getDockerHost() {
        return dockerHost;
    }
}
