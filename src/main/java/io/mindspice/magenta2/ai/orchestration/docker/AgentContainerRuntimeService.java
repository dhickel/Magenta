package io.mindspice.magenta2.ai.orchestration.docker;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "magenta.docker.enabled", havingValue = "true", matchIfMissing = false)
public class AgentContainerRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(AgentContainerRuntimeService.class);
    private static final String LABEL_MANAGED = "magenta.managed";
    private static final String LABEL_AGENT_ID = "magenta.agent.id";
    private static final String LABEL_RUNTIME_GENERATION = "magenta.runtime.generation";

    private final DockerRuntimeClient runtimeClient;
    private final DockerRuntimeConfig config;
    private final WorkspaceDirectoryService workspaceDirectoryService;
    private final WorkspaceService workspaceService;
    private final String runtimeGeneration = UUID.randomUUID().toString();
    private final ConcurrentMap<String, AgentContainerHandle> handles = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Object> locks = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Map<String, Path>> projectMounts = new ConcurrentHashMap<>();

    public AgentContainerRuntimeService(
        DockerRuntimeClient runtimeClient,
        DockerRuntimeConfig config,
        WorkspaceDirectoryService workspaceDirectoryService,
        WorkspaceService workspaceService
    ) {
        this.runtimeClient = runtimeClient;
        this.config = config;
        this.workspaceDirectoryService = workspaceDirectoryService;
        this.workspaceService = workspaceService;
    }

    @PostConstruct
    void reconcileManagedContainers() {
        if (!runtimeClient.ping() || !runtimeClient.isImageAvailable()) {
            return;
        }
        DockerClient client = runtimeClient.dockerClient();
        List<Container> containers = client.listContainersCmd().withShowAll(true)
            .withLabelFilter(Map.of(LABEL_MANAGED, "true")).exec();
        for (Container container : containers) {
            Map<String, String> labels = container.getLabels() == null ? Map.of() : container.getLabels();
            String agentId = labels.get(LABEL_AGENT_ID);
            if (!StringUtils.hasText(agentId)) {
                continue;
            }
            boolean running = "running".equalsIgnoreCase(container.getState());
            handles.put(agentId, new AgentContainerHandle(
                agentId,
                container.getId(),
                container.getNames() != null && container.getNames().length > 0
                    ? trimSlash(container.getNames()[0]) : deterministicContainerName(agentId),
                running ? AgentContainerStatus.RUNNING : AgentContainerStatus.STOPPED,
                runtimeClient.getDockerHost(),
                container.getImage(),
                null,
                Instant.now(),
                List.of(),
                labels,
                "adopted"
            ));
        }
    }

    public AgentContainerHandle ensureAgentContainer(String agentId, String agentName) {
        requireAgentId(agentId);
        synchronized (agentLock(agentId)) {
            assertDockerReady();
            AgentContainerHandle current = lookupHandle(agentId);
            if (current != null && isRunning(current.containerId())) {
                AgentContainerHandle running = withStatus(current, AgentContainerStatus.RUNNING, "running");
                handles.put(agentId, running);
                return running;
            }
            if (current != null && containerExists(current.containerId())) {
                startContainer(current.containerId());
                AgentContainerHandle running = withStatus(current, AgentContainerStatus.RUNNING, "started");
                handles.put(agentId, running);
                return running;
            }
            AgentContainerHandle created = createAndStartContainer(agentId, agentName);
            handles.put(agentId, created);
            return created;
        }
    }

    public AgentContainerHandle ensureProjectMount(String agentId, String agentName, String projectId, Path projectPath) {
        requireAgentId(agentId);
        if (!StringUtils.hasText(projectId) || projectPath == null) {
            throw new IllegalArgumentException("projectId and projectPath are required");
        }
        synchronized (agentLock(agentId)) {
            Map<String, Path> next = new LinkedHashMap<>(projectMounts.getOrDefault(agentId, Map.of()));
            next.put(projectId, projectPath);
            return recreateForMountSet(agentId, agentName, next);
        }
    }

    public AgentContainerHandle removeProjectMount(String agentId, String agentName, String projectId) {
        requireAgentId(agentId);
        synchronized (agentLock(agentId)) {
            Map<String, Path> next = new LinkedHashMap<>(projectMounts.getOrDefault(agentId, Map.of()));
            next.remove(projectId);
            return recreateForMountSet(agentId, agentName, next);
        }
    }

    public AgentContainerHandle startAgentContainer(String agentId, String agentName) {
        return ensureAgentContainer(agentId, agentName);
    }

    public AgentContainerHandle restartAgentContainer(String agentId, String agentName) {
        requireAgentId(agentId);
        synchronized (agentLock(agentId)) {
            AgentContainerHandle stopped = stopAgentContainer(agentId, false);
            return ensureAgentContainer(agentId, agentNameFor(agentName, stopped));
        }
    }

    public AgentContainerHandle stopAgentContainer(String agentId, boolean removeContainer) {
        requireAgentId(agentId);
        synchronized (agentLock(agentId)) {
            AgentContainerHandle current = lookupHandle(agentId);
            if (current == null) {
                return new AgentContainerHandle(
                    agentId, null, deterministicContainerName(agentId), AgentContainerStatus.STOPPED,
                    runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                    List.of(), Map.of(), "container not found"
                );
            }
            if (!runtimeClient.ping()) {
                return withStatus(current, AgentContainerStatus.UNAVAILABLE, runtimeClient.getDaemonError());
            }
            DockerClient client = runtimeClient.dockerClient();
            try {
                if (isRunning(current.containerId())) {
                    client.stopContainerCmd(current.containerId()).withTimeout(10).exec();
                }
                if (removeContainer) {
                    client.removeContainerCmd(current.containerId()).withForce(true).exec();
                    handles.remove(agentId);
                    inFlight.remove(agentId);
                    return withStatus(current, AgentContainerStatus.STOPPED, "removed");
                }
                AgentContainerHandle stopped = inspectHandle(
                    current.containerId(),
                    current.agentId(),
                    AgentContainerStatus.STOPPED,
                    isRunning(current.containerId()) ? "stop requested but container is still running" : "stopped"
                );
                handles.put(agentId, stopped);
                return stopped;
            } catch (NotFoundException notFoundException) {
                handles.remove(agentId);
                return withStatus(current, AgentContainerStatus.STOPPED, "container not found");
            } catch (Exception exception) {
                AgentContainerHandle error = withStatus(current, AgentContainerStatus.ERROR, exception.getMessage());
                handles.put(agentId, error);
                throw new IllegalStateException("Failed to stop container for agent " + agentId, exception);
            }
        }
    }

    public AgentContainerHandle statusFor(String agentId, boolean enabled) {
        requireAgentId(agentId);
        AgentContainerHandle known = lookupHandle(agentId);
        if (!enabled) {
            return known != null ? withStatus(known, AgentContainerStatus.DISABLED, "agent disabled")
                : new AgentContainerHandle(
                    agentId, null, deterministicContainerName(agentId), AgentContainerStatus.DISABLED,
                    runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                    List.of(), Map.of(), "agent disabled"
                );
        }
        if (!runtimeClient.ping()) {
            return known != null ? withStatus(known, AgentContainerStatus.UNAVAILABLE, runtimeClient.getDaemonError())
                : new AgentContainerHandle(
                    agentId, null, deterministicContainerName(agentId), AgentContainerStatus.UNAVAILABLE,
                    runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                    List.of(), Map.of(), runtimeClient.getDaemonError()
                );
        }
        if (!runtimeClient.isImageAvailable()) {
            return known != null ? withStatus(known, AgentContainerStatus.IMAGE_MISSING, "agent image missing")
                : new AgentContainerHandle(
                    agentId, null, deterministicContainerName(agentId), AgentContainerStatus.IMAGE_MISSING,
                    runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                    List.of(), Map.of(), "agent image missing"
                );
        }
        if (known == null) {
            return new AgentContainerHandle(
                agentId, null, deterministicContainerName(agentId), AgentContainerStatus.STOPPED,
                runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                List.of(), Map.of(), "not started"
            );
        }
        if (!StringUtils.hasText(known.containerId()) || !containerExists(known.containerId())) {
            handles.remove(agentId);
            return new AgentContainerHandle(
                agentId, null, deterministicContainerName(agentId), AgentContainerStatus.STOPPED,
                runtimeClient.getDockerHost(), config.getAgentImage(), null, Instant.now(),
                List.of(), Map.of(), "not started"
            );
        }
        boolean running = isRunning(known.containerId());
        AgentContainerStatus status;
        if (!running) {
            status = AgentContainerStatus.STOPPED;
        } else if (inFlightCount(agentId) > 0) {
            status = AgentContainerStatus.RUNNING;
        } else {
            status = AgentContainerStatus.IDLE;
        }
        AgentContainerHandle refreshed = inspectHandle(known.containerId(), known.agentId(), status, "ok");
        handles.put(agentId, refreshed);
        return refreshed;
    }

    public AgentExecResult execInAgent(String agentId, String agentName, String command, String workingDirectory) {
        return execInAgent(agentId, agentName, command, workingDirectory, Math.toIntExact(config.getExecTimeoutSeconds()));
    }

    public AgentExecResult execInAgent(String agentId, String agentName, String command, String workingDirectory,
                                       int timeoutSeconds) {
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("command is required");
        }
        int effectiveTimeoutSeconds = Math.max(1, timeoutSeconds);
        AgentContainerHandle handle = ensureAgentContainer(agentId, agentName);
        DockerClient client = runtimeClient.dockerClient();
        String execId = null;
        try {
            List<String> cmd = List.of("sh", "-lc", command);
            ExecCreateCmdResponse execCreate = client.execCreateCmd(handle.containerId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withWorkingDir(StringUtils.hasText(workingDirectory) ? workingDirectory : "/workspace")
                .withCmd(cmd.toArray(String[]::new))
                .exec();
            execId = execCreate.getId();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            ExecStartResultCallback callback = new ExecStartResultCallback(stdout, stderr) {
                @Override
                public void onNext(Frame frame) {
                    super.onNext(frame);
                }
            };
            boolean finished = client.execStartCmd(execId)
                .exec(callback)
                .awaitCompletion(effectiveTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                restartAgentContainer(agentId, agentName);
                return new AgentExecResult(-1, stdout.toString(), stderr.toString(), true, handle.containerId());
            }
            Long exit = client.inspectExecCmd(execId).exec().getExitCodeLong();
            touchLastUsed(agentId);
            return new AgentExecResult(exit == null ? -1 : exit.intValue(), stdout.toString(), stderr.toString(), false,
                handle.containerId());
        } catch (Exception exception) {
            throw new IllegalStateException("Agent container exec failed for " + agentId, exception);
        }
    }

    public void markAgentBusy(String agentId) {
        inFlight.computeIfAbsent(agentId, ignored -> new AtomicInteger()).incrementAndGet();
        touchLastUsed(agentId);
    }

    public void markAgentIdle(String agentId) {
        AtomicInteger count = inFlight.computeIfAbsent(agentId, ignored -> new AtomicInteger());
        count.updateAndGet(value -> Math.max(0, value - 1));
        touchLastUsed(agentId);
    }

    public void removeAgentContainer(String agentId) {
        stopAgentContainer(agentId, true);
    }

    @Scheduled(fixedDelayString = "${magenta.docker.idle-check-ms:60000}")
    void stopIdleContainers() {
        long ttlSeconds = config.getAgentIdleTtlSeconds();
        if (ttlSeconds <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofSeconds(ttlSeconds));
        for (Map.Entry<String, AgentContainerHandle> entry : handles.entrySet()) {
            String agentId = entry.getKey();
            AgentContainerHandle handle = entry.getValue();
            if (handle == null || !StringUtils.hasText(handle.containerId())) {
                continue;
            }
            if (inFlightCount(agentId) > 0) {
                continue;
            }
            Instant lastUsed = handle.lastUsedAt() == null ? handle.startedAt() : handle.lastUsedAt();
            if (lastUsed != null && lastUsed.isAfter(cutoff)) {
                continue;
            }
            try {
                stopAgentContainer(agentId, false);
            } catch (RuntimeException ignored) {
                // Keep cleanup best-effort and non-fatal.
            }
        }
    }

    @PreDestroy
    void shutdown() {
        if (config.isKeepContainersOnShutdown()) {
            return;
        }
        for (String agentId : new ArrayList<>(handles.keySet())) {
            try {
                stopAgentContainer(agentId, true);
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

    private AgentContainerHandle recreateForMountSet(String agentId, String agentName, Map<String, Path> mounts) {
        Map<String, Path> current = projectMounts.getOrDefault(agentId, Map.of());
        AgentContainerHandle known = lookupHandle(agentId);
        if (Objects.equals(current, mounts) && known != null && isRunning(known.containerId())) {
            return known;
        }
        if (known != null) {
            stopAgentContainer(agentId, true);
        }
        projectMounts.put(agentId, Map.copyOf(mounts));
        AgentContainerHandle created = createAndStartContainer(agentId, agentName);
        handles.put(agentId, created);
        return created;
    }

    private AgentContainerHandle createAndStartContainer(String agentId, String agentName) {
        Path home = workspaceDirectoryService.agentHome(agentId);
        Path output = workspaceDirectoryService.agentOutputRoot(agentId);
        workspaceService.agentWorkspace(agentId, agentNameFor(agentName, null));
        ensureDirectory(home);
        ensureDirectory(output);

        String selinuxOpt = config.isSelinuxRelabel() ? ",Z" : "";
        List<Bind> binds = new ArrayList<>();
        binds.add(Bind.parse(home + ":/home/agent:rw" + selinuxOpt));
        binds.add(Bind.parse(output + ":/output:rw" + selinuxOpt));
        for (Map.Entry<String, Path> mount : projectMounts.getOrDefault(agentId, Map.of()).entrySet()) {
            ensureDirectory(mount.getValue());
            binds.add(Bind.parse(mount.getValue() + ":/projects/" + mount.getKey() + ":rw" + selinuxOpt));
        }
        String containerName = deterministicContainerName(agentId);
        Map<String, String> labels = Map.of(
            LABEL_MANAGED, "true",
            LABEL_AGENT_ID, agentId,
            LABEL_RUNTIME_GENERATION, runtimeGeneration
        );
        DockerClient client = runtimeClient.dockerClient();
        removeConflictingContainerByName(client, containerName);
        HostConfig hostConfig = new HostConfig().withBinds(binds);
        CreateContainerResponse created = client.createContainerCmd(config.getAgentImage())
            .withName(containerName)
            .withWorkingDir("/home/agent")
            .withLabels(labels)
            .withHostConfig(hostConfig)
            .withCmd("sh", "-lc", "while true; do sleep 3600; done")
            .exec();
        client.startContainerCmd(created.getId()).exec();
        return inspectHandle(created.getId(), agentId, AgentContainerStatus.RUNNING, "started");
    }

    private void removeConflictingContainerByName(DockerClient client, String containerName) {
        List<Container> existing = client.listContainersCmd().withShowAll(true).exec();
        for (Container container : existing) {
            if (container.getNames() == null) {
                continue;
            }
            for (String rawName : container.getNames()) {
                if (containerName.equals(trimSlash(rawName))) {
                    try {
                        if ("running".equalsIgnoreCase(container.getState())) {
                            client.stopContainerCmd(container.getId()).withTimeout(10).exec();
                        }
                    } catch (Exception ignored) {
                        // best effort before remove
                    }
                    client.removeContainerCmd(container.getId()).withForce(true).exec();
                }
            }
        }
    }

    private AgentContainerHandle lookupHandle(String agentId) {
        AgentContainerHandle known = handles.get(agentId);
        if (known != null) {
            return known;
        }
        Container existing = findManagedContainer(agentId);
        if (existing == null) {
            return null;
        }
        AgentContainerHandle adopted = inspectHandle(
            existing.getId(), agentId,
            "running".equalsIgnoreCase(existing.getState()) ? AgentContainerStatus.RUNNING : AgentContainerStatus.STOPPED,
            "adopted"
        );
        handles.put(agentId, adopted);
        return adopted;
    }

    private Container findManagedContainer(String agentId) {
        if (!runtimeClient.ping()) {
            return null;
        }
        List<Container> containers = runtimeClient.dockerClient().listContainersCmd().withShowAll(true)
            .withLabelFilter(Map.of(LABEL_MANAGED, "true", LABEL_AGENT_ID, agentId))
            .exec();
        if (containers.isEmpty()) {
            return null;
        }
        return containers.getFirst();
    }

    private AgentContainerHandle inspectHandle(String containerId, String agentId, AgentContainerStatus status, String message) {
        InspectContainerResponse inspect = runtimeClient.dockerClient().inspectContainerCmd(containerId).exec();
        List<String> mounts = new ArrayList<>();
        if (inspect.getMounts() != null) {
            for (var mount : inspect.getMounts()) {
                mounts.add(nn(mount.getSource()) + " -> " + nn(mount.getDestination().getPath()));
            }
        }
        Instant startedAt = parseInstant(inspect.getState() == null ? null : inspect.getState().getStartedAt());
        return new AgentContainerHandle(
            agentId,
            containerId,
            trimSlash(nn(inspect.getName())),
            status,
            runtimeClient.getDockerHost(),
            config.getAgentImage(),
            startedAt,
            Instant.now(),
            mounts,
            inspect.getConfig() == null || inspect.getConfig().getLabels() == null
                ? Map.of() : inspect.getConfig().getLabels(),
            message
        );
    }

    private boolean isRunning(String containerId) {
        try {
            InspectContainerResponse inspect = runtimeClient.dockerClient().inspectContainerCmd(containerId).exec();
            return inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (NotFoundException notFoundException) {
            return false;
        }
    }

    private boolean containerExists(String containerId) {
        try {
            runtimeClient.dockerClient().inspectContainerCmd(containerId).exec();
            return true;
        } catch (NotFoundException notFoundException) {
            return false;
        }
    }

    private void startContainer(String containerId) {
        runtimeClient.dockerClient().startContainerCmd(containerId).exec();
    }

    private void assertDockerReady() {
        if (!runtimeClient.ping()) {
            throw new IllegalStateException(runtimeClient.getDaemonError());
        }
        if (!runtimeClient.isImageAvailable()) {
            runtimeClient.healthCheck();
            if (!runtimeClient.isImageAvailable()) {
                throw new IllegalStateException("Docker daemon reachable but agent image is unavailable: "
                    + config.getAgentImage());
            }
        }
    }

    private void touchLastUsed(String agentId) {
        handles.computeIfPresent(agentId, (id, current) -> withStatus(
            current,
            inFlightCount(agentId) > 0 ? AgentContainerStatus.RUNNING : AgentContainerStatus.IDLE,
            current.message()
        ));
    }

    private AgentContainerHandle withStatus(AgentContainerHandle handle, AgentContainerStatus status, String message) {
        return new AgentContainerHandle(
            handle.agentId(),
            handle.containerId(),
            handle.containerName(),
            status,
            handle.dockerHost(),
            handle.image(),
            handle.startedAt(),
            Instant.now(),
            handle.mounts(),
            handle.labels(),
            message
        );
    }

    private int inFlightCount(String agentId) {
        return inFlight.computeIfAbsent(agentId, ignored -> new AtomicInteger()).get();
    }

    private Object agentLock(String agentId) {
        return locks.computeIfAbsent(agentId, ignored -> new Object());
    }

    private void ensureDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create directory: " + path, exception);
        }
    }

    private void requireAgentId(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            throw new IllegalArgumentException("agentId is required");
        }
    }

    private String deterministicContainerName(String agentId) {
        String cleaned = agentId.replaceAll("[^a-zA-Z0-9_.-]", "").toLowerCase();
        if (!StringUtils.hasText(cleaned)) {
            cleaned = UUID.randomUUID().toString().substring(0, 8);
        }
        if (cleaned.length() > 12) {
            cleaned = cleaned.substring(0, 12);
        }
        return "magenta-agent-" + cleaned;
    }

    private String trimSlash(String value) {
        if (value == null) {
            return null;
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private String nn(String value) {
        return value == null ? "" : value;
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value));
        } catch (Exception ignored) {
            try {
                return Instant.parse(value);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private String agentNameFor(String preferred, AgentContainerHandle fallbackHandle) {
        if (StringUtils.hasText(preferred)) {
            return preferred;
        }
        if (fallbackHandle != null && StringUtils.hasText(fallbackHandle.agentId())) {
            return fallbackHandle.agentId();
        }
        return "agent";
    }
}
