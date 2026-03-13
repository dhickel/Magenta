package io.mindspice.magenta.ui;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionHandle;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
    private final Terminal.SignalHandler previousIntHandler;

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
        this.previousIntHandler = terminal.handle(Terminal.Signal.INT, signal -> {
            if (!magenta.turnInProgress(session.handle())) {
                return;
            }
            if (magenta.abortTurn(session.handle())) {
                renderer.printWarn("abort requested (ctrl-c)");
            }
        });
    }

    public void runLoop() {
        renderer.renderBlock(new UiRenderBlock(
                "Magenta Terminal UI",
                List.of(
                        "sessionId=" + session.handle().sessionId(),
                        "workspaceRoot=" + runtimeConfig.workspaceRoot(),
                        "Commands: /help, /session, /model, /clear, /task [name], /new, /compact, /approve-demo, /tool-approval <on|off>, /yolo <on|off>, /event <text>, /exit"
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

            session.messageIn().accept(SessionInput.userMessage(line));
            waitForTurnCompletion();
            renderStatus();
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
            renderer.close();
        } catch (Exception ignored) {
            // best effort close
        }

        try {
            terminal.handle(Terminal.Signal.INT, previousIntHandler);
        } catch (Exception ignored) {
            // best effort restore
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
                                            List.of("securityMode", policy.mode().name()),
                                            List.of("yoloOverride", String.valueOf(policy.devYoloOverride())),
                                            List.of("activeTask", magenta.activeTask(session.handle()))
                                    )
                            ));
                        }
                ),
                SlashCommandSpec.zero(
                        "model",
                        List.of(),
                        "Switch active session model using a numbered selector",
                        "/model",
                        () -> {
                            if (magenta.turnInProgress(session.handle())) {
                                renderer.printWarn("model switch blocked: turn in progress; wait for completion or abort first");
                                return;
                            }

                            List<RuntimeConfig.ModelConfig> models = magenta.availableModels();
                            if (models.isEmpty()) {
                                renderer.printWarn("no enabled models available");
                                return;
                            }

                            String currentModelId = magenta.settingsFor(session.handle()).modelId();
                            int defaultIndex = 0;
                            for (int i = 0; i < models.size(); i++) {
                                if (models.get(i).id().equals(currentModelId)) {
                                    defaultIndex = i;
                                    break;
                                }
                            }

                            List<String> options = models.stream()
                                    .map(model -> {
                                        String current = model.id().equals(currentModelId) ? " [current]" : "";
                                        return model.id()
                                               + " -> " + model.model()
                                               + " (" + model.provider() + ")"
                                               + current;
                                    })
                                    .toList();

                            UiPromptResponse response = promptService.prompt(new UiPromptRequest.SelectPrompt(
                                    "Switch Model",
                                    "Select model number for this session",
                                    options,
                                    defaultIndex
                            ));
                            if (!(response instanceof UiPromptResponse.SelectResponse selected)) {
                                renderer.printWarn("model switch cancelled");
                                return;
                            }

                            RuntimeConfig.ModelConfig chosen = models.get(selected.selectedIndex());
                            if (chosen.id().equals(currentModelId)) {
                                renderer.printInfo("active model unchanged => " + chosen.model() + " (" + chosen.id() + ")");
                                return;
                            }

                            try {
                                RuntimeConfig.ModelConfig next = magenta.switchModel(session.handle(), chosen.id());
                                renderer.printInfo("active model => " + next.model() + " (" + next.id() + ")");
                            } catch (IllegalStateException e) {
                                renderer.printWarn("model switch blocked: " + (e.getMessage() == null ? "unknown reason" : e.getMessage()));
                            }
                        }
                ),
                SlashCommandSpec.zero(
                        "clear",
                        List.of(),
                        "Clear visible terminal output only",
                        "/clear",
                        renderer::clearScreen
                ),
                SlashCommandSpec.zero(
                        "new",
                        List.of(),
                        "Clear chat history and keep current system/task prompts",
                        "/new",
                        () -> {
                            List<Magenta.SystemMessageOccupancy> occupied = magenta.clearConversation(session.handle());
                            renderer.printInfo("conversation cleared; retained system messages => " + occupied.size());
                            List<List<String>> rows = occupied.stream()
                                    .map(item -> List.of(
                                            String.valueOf(item.position()),
                                            String.valueOf(item.chars()),
                                            preview(item.content(), 72)
                                    ))
                                    .toList();
                            renderer.renderTable(new UiRenderTable(
                                    List.of("#", "Chars", "Preview"),
                                    rows
                            ));
                        }
                ),
                SlashCommandSpec.zero(
                        "compact",
                        List.of(),
                        "Force compaction now (ignores compact threshold)",
                        "/compact",
                        () -> {
                            Magenta.SessionContextUsage before = session.contextUsageSupplier().get();
                            Magenta.ForcedCompactionResult result = magenta.forceCompact(session.handle());
                            Magenta.SessionContextUsage after = session.contextUsageSupplier().get();
                            renderer.renderTable(new UiRenderTable(
                                    List.of("Field", "Value"),
                                    List.of(
                                            List.of("changed", String.valueOf(result.changed())),
                                            List.of("tokens", before.estimatedContextTokens() + " -> " + after.estimatedContextTokens()),
                                            List.of("messages", before.messageCount() + " -> " + after.messageCount())
                                    )
                            ));
                        }
                ),
                SlashCommandSpec.optionalOne(
                        "task",
                        List.of(),
                        "Apply exposed task by name/path, or prompt for anon task text",
                        "/task [task-name]",
                        List.of("task-name"),
                        taskArg -> {
                            String currentTask = magenta.activeTask(session.handle());
                            boolean replacingExisting = currentTask != null && !currentTask.isBlank();
                            if (taskArg != null && !taskArg.isBlank()) {
                                if (replacingExisting) {
                                    UiPromptResponse replacePrompt = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                                            "Replace Task",
                                            "Replace active task '" + currentTask + "' with '" + taskArg + "'?",
                                            false
                                    ));
                                    if (!(replacePrompt instanceof UiPromptResponse.ConfirmResponse confirm && confirm.approved())) {
                                        renderer.printWarn("task update cancelled");
                                        return;
                                    }
                                }
                                String appliedTask = magenta.applyTask(session.handle(), taskArg);
                                renderer.printInfo("active task => " + appliedTask);
                                return;
                            }

                            UiPromptResponse textPrompt = promptService.prompt(new UiPromptRequest.TextPrompt(
                                    "Task Desc",
                                    "Enter task prompt text to apply as persistent system instruction",
                                    false,
                                    ""
                            ));
                            if (!(textPrompt instanceof UiPromptResponse.TextResponse textResponse)) {
                                renderer.printWarn("task prompt cancelled");
                                return;
                            }

                            String promptText = textResponse.text() == null ? "" : textResponse.text().trim();
                            if (promptText.isBlank()) {
                                renderer.printWarn("task prompt cancelled: empty");
                                return;
                            }
                            if (replacingExisting) {
                                UiPromptResponse replacePrompt = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                                        "Replace Task Prompt",
                                        "Replace active task '" + currentTask + "' with new anon task prompt?",
                                        false
                                ));
                                if (!(replacePrompt instanceof UiPromptResponse.ConfirmResponse confirm && confirm.approved())) {
                                    renderer.printWarn("task prompt update cancelled");
                                    return;
                                }
                            }

                            String appliedTask = magenta.applyAnonTaskPrompt(session.handle(), promptText);
                            renderer.printInfo("active task => " + appliedTask);
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
                        "yolo",
                        List.of(),
                        "Toggle full security override for the session",
                        "/yolo <on|off>",
                        List.of("on|off"),
                        value -> {
                            String mode = value.trim().toLowerCase(Locale.ROOT);
                            boolean enabled = "on".equals(mode);
                            boolean disabled = "off".equals(mode);
                            if (!enabled && !disabled) {
                                renderer.printError("usage: /yolo <on|off>");
                                return;
                            }

                            SecurityManager.ToolPolicy current = magenta.toolPolicy(session.handle());
                            SecurityManager.ToolPolicy next = new SecurityManager.ToolPolicy(
                                    current.mode(),
                                    enabled,
                                    current.allowedTools(),
                                    current.deniedTools(),
                                    current.allowedPaths(),
                                    current.allowedCommands(),
                                    current.webAccess(),
                                    current.commandRules()
                            );
                            magenta.setToolPolicy(session.handle(), next);
                            renderer.printWarn("yolo override => " + (enabled ? "ON (full security bypass for this session)" : "OFF"));
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
        renderer.renderStatus(buildStatusBar(magenta, session.handle(), session.contextUsageSupplier()));
    }

    static UiStatusBar buildStatusBar(
            Magenta magenta,
            SessionHandle handle,
            Supplier<Magenta.SessionContextUsage> contextUsageSupplier
    ) {
        Objects.requireNonNull(magenta, "magenta");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(contextUsageSupplier, "contextUsageSupplier");

        Magenta.SessionContextUsage usage = contextUsageSupplier.get();
        var settings = magenta.settingsFor(handle);
        var policy = magenta.toolPolicy(handle);

        String topLeft = "model: " + usage.modelName();
        String topRight = "ctx: " + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                          + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%)";
        String bottomLeft = "session: " + settings.alias() + " [" + shortSessionId(usage.sessionId().toString()) + "]";
        String bottomRight = "tools=" + settings.toolsEnabled()
                             + " stream=session:" + settings.streamingEnabled()
                             + ",model:" + settings.modelSupportsStreaming()
                             + " security=" + policy.mode().name()
                             + " yolo=" + (policy.devYoloOverride() ? "on" : "off");

        return new UiStatusBar(topLeft, topRight, bottomLeft, bottomRight);
    }

    private static String shortSessionId(String id) {
        if (id == null || id.length() < 8) {
            return id == null ? "" : id;
        }
        return id.substring(0, 8);
    }

    static AtomicReference<SlashCommandRegistry> registryRef() {
        return new AtomicReference<>(SlashCommandRegistry.empty());
    }

    private static String preview(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String compact = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() <= maxChars) {
            return compact;
        }
        int limit = Math.max(0, maxChars - 3);
        return compact.substring(0, limit) + "...";
    }

    private void waitForTurnCompletion() {
        long start = System.nanoTime();
        long startTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(250);
        while (!closed.get() && !magenta.turnInProgress(session.handle())) {
            if (System.nanoTime() - start >= startTimeoutNanos) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        while (!closed.get() && magenta.turnInProgress(session.handle())) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

}
