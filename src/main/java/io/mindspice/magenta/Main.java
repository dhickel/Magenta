package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.ui.casciian.CasciianUiScaffold;
import io.mindspice.magenta.ui.casciian.CasciianTerminalUiBootstrap;
import io.mindspice.magenta.ui.TerminalUiBootstrap;
import io.mindspice.magenta.ui.TerminalUiCallbacks;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.ToolApprovalPromptAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        CliArgs cli = parseArgs(args);
        String uiBackend = System.getenv().getOrDefault("MAGENTA_UI_BACKEND", "casciian")
                .trim()
                .toLowerCase(java.util.Locale.ROOT);
        if (uiBackend.equals("casciian-demo")) {
            try {
                CasciianUiScaffold.runDemo();
                return;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to start Casciian demo UI", e);
            }
        }

        Path configPath = cli.configPath();
        RuntimeConfig runtimeConfig = RuntimeConfig.load(configPath);
        if (cli.forceYolo()) {
            runtimeConfig = runtimeConfig.withYoloOverride();
        }
        if (cli.logLevelOverride() != null) {
            runtimeConfig = runtimeConfig.withLogLevelOverride(cli.logLevelOverride());
        }
        boolean routingLogsEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("MAGENTA_UI_ROUTE_LOGS", "false"));
        ensureWorkspaceRoot(runtimeConfig.workspaceRoot());
        ToolApprovalPromptAdapter approvalAdapter = new ToolApprovalPromptAdapter();
        Magenta magenta = new Magenta(runtimeConfig, null, approvalAdapter);
        RuntimeConfig.TerminalConfig terminalConfig = runtimeConfig.terminal();

        TerminalUiConfig uiConfig = new TerminalUiConfig(
                new TerminalUiConfig.Session(
                        "magenta",
                        SessionParams.ofStreaming(true),
                        routingLogsEnabled ? RoutingEventLevel.FINAL : RoutingEventLevel.NONE
                ),
                new TerminalUiConfig.Rendering(
                        terminalConfig.rendering().colorEnabled(),
                        terminalConfig.rendering().showTimestamps(),
                        terminalConfig.rendering().showStatusBar(),
                        toUiPalette(terminalConfig.rendering().colors())
                ),
                new TerminalUiConfig.Behavior(null, "you> ", "cli-system"),
                new TerminalUiConfig.Observability(routingLogsEnabled),
                new TerminalUiConfig.Security(toUiVisibility(terminalConfig.security().eventVisibility())),
                new TerminalUiConfig.ToolOutput(toUiToolOutputFormat(terminalConfig.tools().outputFormat())),
                new TerminalUiConfig.Prompts(true, 240),
                new TerminalUiCallbacks(
                        ignored -> {},
                        ignored -> {},
                        ignored -> {}
                )
        );

        try {
            if (uiBackend.equals("lanterna")) {
                TerminalUiBootstrap.bootstrap(magenta, uiConfig, approvalAdapter).runLoop();
            } else {
                CasciianTerminalUiBootstrap.bootstrap(magenta, uiConfig, approvalAdapter).runLoop();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start terminal UI", e);
        }
    }

    private static CliArgs parseArgs(String[] args) {
        Path configPath = Path.of("configs", "magenta.yaml");
        boolean configPathSet = false;
        boolean forceYolo = false;
        RuntimeConfig.LogLevel logLevelOverride = null;
        List<String> unknownFlags = new ArrayList<>();

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg == null || arg.isBlank()) {
                    continue;
                }
                if ("--yolo".equals(arg.trim())) {
                    forceYolo = true;
                    continue;
                }
                if ("--log-level".equals(arg.trim())) {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--log-level requires a value");
                    }
                    String value = args[++i];
                    logLevelOverride = parseLogLevelArg(value);
                    continue;
                }
                if (arg.startsWith("-")) {
                    unknownFlags.add(arg);
                    continue;
                }
                if (configPathSet) {
                    throw new IllegalArgumentException("Only one config path argument is supported");
                }
                configPath = Path.of(arg);
                configPathSet = true;
            }
        }

        if (!unknownFlags.isEmpty()) {
            throw new IllegalArgumentException("Unsupported flags: " + String.join(", ", unknownFlags));
        }
        return new CliArgs(configPath, forceYolo, logLevelOverride);
    }

    private static RuntimeConfig.LogLevel parseLogLevelArg(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--log-level requires one of: off, error, info, debug, trace");
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "off" -> RuntimeConfig.LogLevel.OFF;
            case "error" -> RuntimeConfig.LogLevel.ERROR;
            case "info" -> RuntimeConfig.LogLevel.INFO;
            case "debug" -> RuntimeConfig.LogLevel.DEBUG;
            case "trace" -> RuntimeConfig.LogLevel.TRACE;
            default -> throw new IllegalArgumentException(
                    "Unsupported --log-level value: " + value + " (expected off|error|info|debug|trace)"
            );
        };
    }

    private record CliArgs(Path configPath, boolean forceYolo, RuntimeConfig.LogLevel logLevelOverride) {
    }

    private static void ensureWorkspaceRoot(Path workspaceRoot) {
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to initialize workspace root: " + workspaceRoot.toAbsolutePath(), e);
        }
    }

    private static TerminalUiConfig.ColorPalette toUiPalette(RuntimeConfig.TerminalColorConfig colors) {
        return new TerminalUiConfig.ColorPalette(
                toUiColor(colors.system()),
                toUiColor(colors.user()),
                toUiColor(colors.assistant()),
                toUiColor(colors.info()),
                toUiColor(colors.warn()),
                toUiColor(colors.error()),
                toUiColor(colors.muted()),
                toUiColor(colors.defaultColor())
        );
    }

    private static TerminalUiConfig.ColorName toUiColor(RuntimeConfig.TerminalColor color) {
        return switch (color) {
            case DEFAULT -> TerminalUiConfig.ColorName.DEFAULT;
            case BLACK -> TerminalUiConfig.ColorName.BLACK;
            case RED -> TerminalUiConfig.ColorName.RED;
            case GREEN -> TerminalUiConfig.ColorName.GREEN;
            case YELLOW -> TerminalUiConfig.ColorName.YELLOW;
            case BLUE -> TerminalUiConfig.ColorName.BLUE;
            case MAGENTA -> TerminalUiConfig.ColorName.MAGENTA;
            case CYAN -> TerminalUiConfig.ColorName.CYAN;
            case WHITE -> TerminalUiConfig.ColorName.WHITE;
            case BRIGHT -> TerminalUiConfig.ColorName.BRIGHT;
        };
    }

    private static TerminalUiConfig.SecurityEventVisibility toUiVisibility(
            RuntimeConfig.TerminalSecurityVisibility visibility
    ) {
        return switch (visibility) {
            case DENIALS_ONLY -> TerminalUiConfig.SecurityEventVisibility.DENIALS_ONLY;
            case ALL -> TerminalUiConfig.SecurityEventVisibility.ALL;
            case OFF -> TerminalUiConfig.SecurityEventVisibility.OFF;
        };
    }

    private static TerminalUiConfig.ToolOutputFormat toUiToolOutputFormat(
            RuntimeConfig.TerminalToolOutputFormat format
    ) {
        return switch (format) {
            case COMPACT_SUMMARY -> TerminalUiConfig.ToolOutputFormat.COMPACT_SUMMARY;
        };
    }
}
