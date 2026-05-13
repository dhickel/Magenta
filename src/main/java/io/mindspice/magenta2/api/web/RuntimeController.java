package io.mindspice.magenta2.api.web;

import java.time.Instant;

import io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeClient;
import io.mindspice.magenta2.ai.orchestration.docker.DockerRuntimeConfig;
import io.mindspice.magenta2.ai.orchestration.docker.DockerStatusResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeController {
    private final ObjectProvider<DockerRuntimeClient> dockerClient;
    private final ObjectProvider<DockerRuntimeConfig> dockerConfig;

    public RuntimeController(
        ObjectProvider<DockerRuntimeClient> dockerClient,
        ObjectProvider<DockerRuntimeConfig> dockerConfig
    ) {
        this.dockerClient = dockerClient;
        this.dockerConfig = dockerConfig;
    }

    @GetMapping("/docker/status")
    public DockerStatusResponse dockerStatus() {
        DockerRuntimeClient client = dockerClient.getIfAvailable();
        DockerRuntimeConfig config = dockerConfig.getIfAvailable();

        if (client == null) {
            return new DockerStatusResponse(
                false, false, config != null ? config.getDockerHost() : "n/a",
                config != null ? config.getAgentImage() : "n/a",
                "Docker runtime is disabled (magenta.docker.enabled=false).",
                Instant.now()
            );
        }

        // Live check: ping updates the daemonAvailable flag.
        boolean reachable = client.ping();
        String message;
        if (!reachable) {
            message = client.getDaemonError() != null
                ? client.getDaemonError()
                : "Docker daemon unreachable at " + client.getDockerHost();
        } else {
            message = client.healthCheck();
        }
        return new DockerStatusResponse(
            true, reachable, client.getDockerHost(), client.getAgentImage(),
            message, Instant.now()
        );
    }
}
