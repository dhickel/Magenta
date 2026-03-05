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
        ensureWorkspaceRoot(runtimeConfig.workspaceRoot());
        ToolApprovalPromptAdapter approvalAdapter = new ToolApprovalPromptAdapter();
        Magenta magenta = new Magenta(runtimeConfig, null, approvalAdapter);

        TerminalUiConfig uiConfig = new TerminalUiConfig(
                new TerminalUiConfig.Session("terminal", SessionParams.ofStreaming(true), RoutingEventLevel.FINAL),
                new TerminalUiConfig.Rendering(true, false, true),
                new TerminalUiConfig.Behavior(null, "you> ", "cli-system"),
                new TerminalUiConfig.Prompts(true, 240),
                new TerminalUiCallbacks(
                        event -> System.err.println("[route] " + event),
                        event -> System.err.println("[security] " + event.toolName() + " " + event.decisionCode()),
                        error -> System.err.println("[session-error] " + error.getMessage())
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
}
