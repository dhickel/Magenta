package io.mindspice.magenta2.ai.chat.tool.shell;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.docker.AgentContainerRuntimeService;
import io.mindspice.magenta2.ai.orchestration.docker.AgentExecResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationTaskContextHolder;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentShellToolService {
    private static final String WILDCARD = "*";
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;
    private static final int MAX_TIMEOUT_SECONDS = 30;
    private static final int OUTPUT_LIMIT_BYTES = 16_384;

    private final Path root;
    private final Set<String> allowedCommands;
    private final boolean allowAllCommands;
    private final AgentContainerRuntimeService containerRuntimeService;

    @Autowired
    public AgentShellToolService(AiConfig aiConfig,
                                  @Autowired(required = false) RuntimeSettingsService runtimeSettingsService,
                                  @Autowired(required = false) AgentContainerRuntimeService containerRuntimeService) throws IOException {
        if (aiConfig == null || aiConfig.dataRoot() == null) {
            throw new IllegalArgumentException("AI config dataRoot is required for shell tools");
        }
        if (!Files.isDirectory(aiConfig.dataRoot())) {
            throw new IllegalArgumentException("AI config dataRoot must be an existing directory: " + aiConfig.dataRoot());
        }
        AgentConfig defaultAgent = aiConfig.agents() == null ? null : aiConfig.agents().get(aiConfig.defaultAgent());
        List<String> commands = runtimeSettingsService == null
            ? (defaultAgent == null ? List.of() : defaultAgent.allowedShellCommands())
            : runtimeSettingsService.allowedShellCommands();
        this.root = aiConfig.dataRoot().toRealPath();
        this.allowAllCommands = commands != null && commands.contains(WILDCARD);
        this.allowedCommands = commands == null
            ? Set.of()
            : commands.stream().filter(StringUtils::hasText).collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.containerRuntimeService = containerRuntimeService;
    }

    public AgentShellToolService(AiConfig aiConfig) throws IOException {
        this(aiConfig, null, null);
    }

    AgentShellToolService(Path root, List<String> allowedCommands) throws IOException {
        this.root = root.toRealPath();
        List<String> commands = allowedCommands == null ? List.of() : allowedCommands;
        this.allowAllCommands = commands.contains(WILDCARD);
        this.allowedCommands = commands.stream()
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.containerRuntimeService = null;
    }

    AgentShellToolService(Path root, List<String> allowedCommands,
                          AgentContainerRuntimeService containerRuntimeService) throws IOException {
        this.root = root.toRealPath();
        List<String> commands = allowedCommands == null ? List.of() : allowedCommands;
        this.allowAllCommands = commands.contains(WILDCARD);
        this.allowedCommands = commands.stream()
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.containerRuntimeService = containerRuntimeService;
    }

    public ShellExecResult exec(String command, String workingDirectory, Integer timeoutSeconds)
        throws IOException, InterruptedException {
        List<String> commandLine = parseCommandLine(command);
        String executable = validateExecutable(commandLine.getFirst());
        if (!allowAllCommands && !allowedCommands.contains(executable)) {
            throw new IllegalArgumentException("shell command is not allowed: " + executable);
        }

        // Check orchestration task context: if an agentId is present, route
        // execution into the agent's managed Docker container.
        OrchestrationTaskContext taskContext = OrchestrationTaskContextHolder.current();
        if (taskContext != null && taskContext.hasAgentContext()) {
            if (containerRuntimeService == null) {
                throw new IllegalStateException(
                    "Task requires containerized execution for agent " + taskContext.agentId()
                        + " but container runtime is not available. Docker must be enabled and configured.");
            }
            return execInContainer(command, executable, commandLine, workingDirectory, timeoutSeconds, taskContext);
        }

        // No orchestration context — use host shell execution
        return execOnHost(command, executable, commandLine, workingDirectory, timeoutSeconds);
    }

    /**
     * Execute a command inside the managed Docker container for the current
     * orchestration task context. Container working directory defaults to
     * {@code /home/agent}. Container output path is {@code /output}; leased
     * project roots appear below {@code /projects/{projectId}}.
     */
    private ShellExecResult execInContainer(String command, String executable,
                                             List<String> commandLine,
                                             String workingDirectory, Integer timeoutSeconds,
                                             OrchestrationTaskContext taskContext) {
        int timeout = clamp(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);
        String containerWorkDir = resolveContainerWorkingDirectory(workingDirectory);
        AgentExecResult result = containerRuntimeService.execInAgent(
            taskContext.agentId(),
            taskContext.agentName(),
            command,
            containerWorkDir,
            timeout
        );

        return new ShellExecResult(
            executable,
            command,
            List.copyOf(commandLine.subList(1, commandLine.size())),
            containerWorkDir,
            result.exitCode(),
            result.stdout(),
            result.stderr(),
            result.timedOut(),
            false,
            "docker",
            result.containerId()
        );
    }

    /**
     * Execute a command on the host filesystem. Only available when no
     * orchestration task context is active.
     */
    private ShellExecResult execOnHost(String command, String executable,
                                        List<String> commandLine,
                                        String workingDirectory, Integer timeoutSeconds)
        throws IOException, InterruptedException {
        Path workingDir = resolveWorkingDirectory(workingDirectory);
        int timeout = clamp(timeoutSeconds, DEFAULT_TIMEOUT_SECONDS, 1, MAX_TIMEOUT_SECONDS);

        Process process = new ProcessBuilder(commandLine)
            .directory(workingDir.toFile())
            .start();
        CompletableFuture<CapturedOutput> stdoutCapture = CompletableFuture.supplyAsync(() -> capture(process.getInputStream()));
        CompletableFuture<CapturedOutput> stderrCapture = CompletableFuture.supplyAsync(() -> capture(process.getErrorStream()));

        boolean interrupted = false;
        boolean completed = false;
        CapturedOutput out;
        CapturedOutput err;
        try {
            try {
                completed = process.waitFor(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
            if (!completed) {
                process.destroyForcibly();
                try {
                    process.waitFor(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        } finally {
            // Bound and cancel capture futures on ALL terminal paths
            out = drainCaptureFuture(stdoutCapture);
            err = drainCaptureFuture(stderrCapture);
            // Safety net: ensure process is dead on all paths
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
            throw new InterruptedException("Shell execution interrupted");
        }

        return new ShellExecResult(
            executable,
            command,
            List.copyOf(commandLine.subList(1, commandLine.size())),
            displayPath(workingDir),
            completed ? process.exitValue() : null,
            out.text(),
            err.text(),
            !completed,
            out.truncated() || err.truncated(),
            "host"
        );
    }

    /**
     * Drains a capture future with a bounded timeout, cancelling the future if it
     * does not complete within the deadline. Returns a fallback {@link CapturedOutput}
     * on all failure paths so callers never block indefinitely.
     */
    private static CapturedOutput drainCaptureFuture(CompletableFuture<CapturedOutput> future) {
        try {
            return future.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return new CapturedOutput("[capture timed out]", true);
        } catch (ExecutionException | CancellationException e) {
            return new CapturedOutput("[capture failed: " + e.getMessage() + "]", true);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return new CapturedOutput("[capture interrupted]", true);
        }
    }

    private CapturedOutput capture(InputStream inputStream) {
        byte[] buffer = new byte[4096];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean truncated = false;
        try (InputStream in = inputStream) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                int remaining = OUTPUT_LIMIT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(read, remaining));
                }
                if (read > remaining) {
                    truncated = true;
                }
            }
        } catch (IOException exception) {
            String message = "[failed to capture process output: " + exception.getMessage() + "]";
            return new CapturedOutput(message, true);
        }
        return new CapturedOutput(output.toString(StandardCharsets.UTF_8), truncated);
    }

    private List<String> parseCommandLine(String command) {
        if (!StringUtils.hasText(command)) {
            throw new IllegalArgumentException("command is required");
        }
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Character quote = null;
        boolean escaped = false;
        for (int i = 0; i < command.length(); i++) {
            char ch = command.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (quote != null) {
                if (ch == quote) {
                    quote = null;
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '\'' || ch == '"') {
                quote = ch;
                continue;
            }
            if (Character.isWhitespace(ch)) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(ch);
        }
        if (escaped) {
            current.append('\\');
        }
        if (quote != null) {
            throw new IllegalArgumentException("command has an unterminated quote");
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }
        return List.copyOf(tokens);
    }

    private String validateExecutable(String executable) {
        if (executable.contains("/") || executable.contains("\\") || executable.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("command must be a bare executable name");
        }
        return executable;
    }

    private Path resolveWorkingDirectory(String workingDirectory) throws IOException {
        String requested = StringUtils.hasText(workingDirectory) ? workingDirectory : ".";
        Path input = Path.of(requested);
        Path resolved = input.isAbsolute() ? input.normalize() : root.resolve(input).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("workingDirectory escapes data root");
        }
        Path real = resolved.toRealPath();
        if (!real.startsWith(root)) {
            throw new IllegalArgumentException("workingDirectory escapes data root");
        }
        if (!Files.isDirectory(real, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workingDirectory is not a directory: " + displayPath(resolved));
        }
        return real;
    }

    private String resolveContainerWorkingDirectory(String workingDirectory) {
        if (!StringUtils.hasText(workingDirectory) || ".".equals(workingDirectory)) {
            return "/home/agent";
        }
        String normalized = workingDirectory.trim().replace('\\', '/');
        if (!normalized.startsWith("/")) {
            normalized = "/home/agent/" + normalized;
        }
        Path path = Path.of(normalized).normalize();
        String resolved = path.toString();
        if (!isAllowedContainerWorkingDirectory(resolved)) {
            throw new IllegalArgumentException(
                "container workingDirectory must be /home/agent, /projects, /workspace, /output, or a subpath");
        }
        return resolved;
    }

    private boolean isAllowedContainerWorkingDirectory(String path) {
        return path.equals("/workspace") || path.startsWith("/workspace/")
            || path.equals("/home/agent") || path.startsWith("/home/agent/")
            || path.equals("/projects") || path.startsWith("/projects/")
            || path.equals("/output") || path.startsWith("/output/");
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int actual = value == null ? defaultValue : value;
        return Math.min(max, Math.max(min, actual));
    }

    private String displayPath(Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        return relative.toString().isEmpty() ? "." : relative.toString();
    }

    private record CapturedOutput(String text, boolean truncated) {
    }

    public record ShellExecResult(
        String command,
        String commandLine,
        List<String> args,
        String workingDirectory,
        Integer exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean truncated,
        String executionType,
        String containerId
    ) {
        public ShellExecResult(String command, String commandLine, List<String> args,
                               String workingDirectory, Integer exitCode, String stdout,
                               String stderr, boolean timedOut, boolean truncated) {
            this(command, commandLine, args, workingDirectory, exitCode, stdout, stderr,
                timedOut, truncated, "host", null);
        }

        public ShellExecResult(String command, String commandLine, List<String> args,
                               String workingDirectory, Integer exitCode, String stdout,
                               String stderr, boolean timedOut, boolean truncated,
                               String executionType) {
            this(command, commandLine, args, workingDirectory, exitCode, stdout, stderr,
                timedOut, truncated, executionType, null);
        }
    }
}
