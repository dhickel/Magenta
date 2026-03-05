package io.mindspice.magenta.ui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;
import io.mindspice.magenta.ui.render.UiRenderBlock;
import io.mindspice.magenta.ui.render.UiRenderTable;
import io.mindspice.magenta.ui.render.UiRenderer;
import io.mindspice.magenta.ui.render.UiStatusBar;
import io.mindspice.magenta.ui.render.UiStyle;
import io.mindspice.magenta.ui.slash.SlashCommandDispatcher;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.slash.SlashCommandSpec;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class TerminalUiRuntime {

    private final RuntimeConfig runtimeConfig;
    private final Magenta magenta;
    private final Terminal terminal;
    private final LineReader lineReader;
    private final UiRenderer renderer;
    private final PromptService promptService;
    private final TerminalUiConfig config;
    private final TerminalUiSession session;
    private final SlashCommandRegistry slashRegistry;
    private final SlashCommandDispatcher slashDispatcher;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    TerminalUiRuntime(
            RuntimeConfig runtimeConfig,
            Magenta magenta,
            Terminal terminal,
            LineReader lineReader,
            UiRenderer renderer,
            PromptService promptService,
            TerminalUiConfig config,
            TerminalUiSession session,
            SlashCommandRegistry slashRegistry
    ) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.terminal = Objects.requireNonNull(terminal, "terminal");
        this.lineReader = Objects.requireNonNull(lineReader, "lineReader");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.promptService = Objects.requireNonNull(promptService, "promptService");
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.slashRegistry = Objects.requireNonNull(slashRegistry, "slashRegistry");
        this.slashDispatcher = new SlashCommandDispatcher(slashRegistry, renderer);
    }

    public void runLoop() {
        renderer.renderBlock(new UiRenderBlock(
                "Magenta Terminal UI",
                List.of(
                        "sessionId=" + session.handle().sessionId(),
                        "workspaceRoot=" + runtimeConfig.workspaceRoot(),
                        "Commands: /help, /session, /approve-demo, /tool-approval <on|off>, /event <text>, /exit"
                ),
                UiStyle.SYSTEM
        ));

        while (true) {
            renderStatus();
            String line = readLine();
            if (line == null) {
                break;
            }

            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            if (config.behavior().isExitCommand(trimmed)) {
                break;
            }

            if (slashDispatcher.dispatchIfCommand(trimmed)) {
                continue;
            }

            renderer.printUser("you> " + line);
            session.messageIn().accept(SessionInput.userMessage(line));
        }

        close();
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            magenta.removeRoute(session.outputRoute());
        } catch (Exception ignored) {
            // best effort close
        }

        try {
            magenta.closeSession(session.handle());
        } catch (Exception ignored) {
            // best effort close
        }

        try {
            terminal.close();
        } catch (IOException ignored) {
            // best effort close
        }
    }

    static SlashCommandRegistry defaultCommands(
            Magenta magenta,
            TerminalUiConfig config,
            TerminalUiSession session,
            UiRenderer renderer,
            PromptService promptService,
            AtomicReference<SlashCommandRegistry> registryRef
    ) {
        return new SlashCommandRegistry(List.of(
                SlashCommandSpec.zero(
                        "help",
                        List.of("h"),
                        "Show slash command help",
                        "/help",
                        () -> {
                            SlashCommandRegistry registry = registryRef.get();
                            List<List<String>> rows = registry.commands().stream()
                                    .map(cmd -> List.of("/" + cmd.name(), cmd.help(), cmd.usage()))
                                    .toList();
                            renderer.renderTable(new UiRenderTable(List.of("Command", "Description", "Usage"), rows));
                        }
                ),
                SlashCommandSpec.zero(
                        "session",
                        List.of("s"),
                        "Show current session + context usage",
                        "/session",
                        () -> {
                            Magenta.SessionContextUsage usage = session.contextUsageSupplier().get();
                            var settings = magenta.settingsFor(session.handle());
                            var policy = magenta.toolPolicy(session.handle());

                            renderer.renderTable(new UiRenderTable(
                                    List.of("Field", "Value"),
                                    List.of(
                                            List.of("sessionId", usage.sessionId().toString()),
                                            List.of("alias", settings.alias()),
                                            List.of("model", usage.modelName() + " (" + usage.modelId() + ")"),
                                            List.of("context", usage.estimatedContextTokens() + "/" + usage.maxContextTokens()),
                                            List.of("context %", String.format(Locale.ROOT, "%.2f%%", usage.percentOfMaxContext())),
                                            List.of("messages", String.valueOf(usage.messageCount())),
                                            List.of("toolsEnabled", String.valueOf(settings.toolsEnabled())),
                                            List.of("streamingEnabled", String.valueOf(settings.streamingEnabled())),
                                            List.of("securityMode", policy.mode().name())
                                    )
                            ));
                        }
                ),
                SlashCommandSpec.zero(
                        "approve-demo",
                        List.of(),
                        "Demo confirm prompt flow",
                        "/approve-demo",
                        () -> {
                            UiPromptResponse response = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                                    "Approve Demo",
                                    "Approve this demo action?",
                                    false
                            ));
                            switch (response) {
                                case UiPromptResponse.ConfirmResponse confirm ->
                                        renderer.printInfo("approve-demo => " + (confirm.approved() ? "approved" : "denied"));
                                case UiPromptResponse.Cancelled cancelled ->
                                        renderer.printWarn("approve-demo => cancelled: " + cancelled.reason());
                                default -> renderer.printWarn("approve-demo => unexpected response");
                            }
                        }
                ),
                SlashCommandSpec.one(
                        "tool-approval",
                        List.of("approval"),
                        "Toggle tool approval prompt mode",
                        "/tool-approval <on|off>",
                        List.of("on|off"),
                        value -> {
                            String mode = value.trim().toLowerCase(Locale.ROOT);
                            boolean promptMode = "on".equals(mode);
                            boolean blacklistMode = "off".equals(mode);
                            if (!promptMode && !blacklistMode) {
                                renderer.printError("usage: /tool-approval <on|off>");
                                return;
                            }

                            SecurityManager.ToolPolicy current = magenta.toolPolicy(session.handle());
                            SecurityManager.ToolPolicy next = new SecurityManager.ToolPolicy(
                                    promptMode
                                            ? RuntimeConfig.SecurityMode.PROMPT
                                            : RuntimeConfig.SecurityMode.BLACKLIST,
                                    current.devYoloOverride(),
                                    current.allowedTools(),
                                    current.deniedTools(),
                                    current.allowedPaths(),
                                    current.allowedCommands(),
                                    current.webAccess(),
                                    current.commandRules()
                            );
                            magenta.setToolPolicy(session.handle(), next);
                            renderer.printInfo("tool approval mode => " + next.mode().name());
                        }
                ),
                SlashCommandSpec.one(
                        "event",
                        List.of(),
                        "Send system event input",
                        "/event <text>",
                        List.of("text"),
                        text -> {
                            session.eventIn().accept(new SessionInput.SysEvent(
                                    text,
                                    config.behavior().systemEventSourceId(),
                                    false
                            ));
                            renderer.printSystem("event sent");
                        }
                )
        ));
    }

    private String readLine() {
        try {
            return lineReader.readLine(config.behavior().userPrompt());
        } catch (UserInterruptException ignored) {
            return "";
        } catch (EndOfFileException ignored) {
            return null;
        }
    }

    private void renderStatus() {
        Magenta.SessionContextUsage usage = session.contextUsageSupplier().get();
        var settings = magenta.settingsFor(session.handle());
        var policy = magenta.toolPolicy(session.handle());

        String topLeft = "model: " + usage.modelName();
        String topRight = "ctx: " + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                          + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%)";
        String bottomLeft = "session: " + settings.alias() + " [" + shortSessionId(usage.sessionId().toString()) + "]";
        String bottomRight = "tools=" + settings.toolsEnabled() + " stream=" + settings.streamingEnabled()
                             + " security=" + policy.mode().name();

        renderer.renderStatus(new UiStatusBar(topLeft, topRight, bottomLeft, bottomRight));
    }

    private String shortSessionId(String id) {
        if (id == null || id.length() < 8) {
            return id == null ? "" : id;
        }
        return id.substring(0, 8);
    }

    static AtomicReference<SlashCommandRegistry> registryRef() {
        return new AtomicReference<>(SlashCommandRegistry.empty());
    }

    static AtomicBoolean streamFlag() {
        return new AtomicBoolean(false);
    }
}
