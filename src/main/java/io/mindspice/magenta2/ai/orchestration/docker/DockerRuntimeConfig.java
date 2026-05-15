package io.mindspice.magenta2.ai.orchestration.docker;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Docker runtime configuration. Reads the Docker host from the
 * {@code DOCKER_HOST} environment variable, defaulting to the local
 * rootless Podman socket when unset.
 */
@Component
@ConfigurationProperties(prefix = "magenta.docker")
public class DockerRuntimeConfig {
    private String host;
    private String agentImage = "python:3.11";
    private long execTimeoutSeconds = 600;
    private long agentIdleTtlSeconds = 1800;
    private boolean keepContainersOnShutdown = false;
    private boolean selinuxRelabel = true;

    public String getDockerHost() {
        // Env var takes precedence over property
        String envHost = System.getenv("DOCKER_HOST");
        if (StringUtils.hasText(envHost)) {
            return envHost;
        }
        if (StringUtils.hasText(host)) {
            return host;
        }
        // Default to rootless Podman socket
        String xdgRuntime = System.getenv("XDG_RUNTIME_DIR");
        if (StringUtils.hasText(xdgRuntime)) {
            return "unix://" + xdgRuntime + "/podman/podman.sock";
        }
        String userHome = System.getProperty("user.home", "/home");
        String userName = System.getProperty("user.name", "");
        if (StringUtils.hasText(userName)) {
            return "unix:///run/user/" + userName + "/podman/podman.sock";
        }
        return "unix:///var/run/docker.sock";
    }

    public String getAgentImage() {
        return agentImage;
    }

    public long getExecTimeoutSeconds() {
        return execTimeoutSeconds;
    }

    public long getAgentIdleTtlSeconds() {
        return agentIdleTtlSeconds;
    }

    public boolean isKeepContainersOnShutdown() {
        return keepContainersOnShutdown;
    }

    // ── Setters for @ConfigurationProperties ──

    public void setHost(String host) {
        this.host = host;
    }

    public void setAgentImage(String agentImage) {
        this.agentImage = agentImage;
    }

    public void setExecTimeoutSeconds(long execTimeoutSeconds) {
        this.execTimeoutSeconds = execTimeoutSeconds;
    }

    public void setAgentIdleTtlSeconds(long agentIdleTtlSeconds) {
        this.agentIdleTtlSeconds = agentIdleTtlSeconds;
    }

    public void setKeepContainersOnShutdown(boolean keepContainersOnShutdown) {
        this.keepContainersOnShutdown = keepContainersOnShutdown;
    }

    public boolean isSelinuxRelabel() {
        return selinuxRelabel;
    }

    public void setSelinuxRelabel(boolean selinuxRelabel) {
        this.selinuxRelabel = selinuxRelabel;
    }
}
