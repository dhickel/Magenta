package io.mindspice.magenta;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.routing.RoutingEventLevel;
import io.mindspice.magenta.runtime.session.config.SessionParams;
import io.mindspice.magenta.ui.TerminalUiBootstrap;
import io.mindspice.magenta.ui.TerminalUiCallbacks;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.ToolApprovalPromptAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) {
        Path configPath = args.length > 0 ? Path.of(args[0]) : Path.of("configs", "magenta.yaml");
        RuntimeConfig runtimeConfig = RuntimeConfig.load(configPath);
        boolean routingLogsEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("MAGENTA_UI_ROUTE_LOGS", "false"));
        ensureWorkspaceRoot(runtimeConfig.workspaceRoot());
        ToolApprovalPromptAdapter approvalAdapter = new ToolApprovalPromptAdapter();
        Magenta magenta = new Magenta(runtimeConfig, null, approvalAdapter);
        RuntimeConfig.TerminalConfig terminalConfig = runtimeConfig.terminal();

        TerminalUiConfig uiConfig = new TerminalUiConfig(
                new TerminalUiConfig.Session(
                        "terminal",
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
            var runtime = TerminalUiBootstrap.bootstrap(magenta, uiConfig, approvalAdapter);
            runtime.runLoop();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start terminal UI", e);
        }
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
