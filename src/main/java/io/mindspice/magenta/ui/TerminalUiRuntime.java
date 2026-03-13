package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TerminalTextUtils;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.ActionListBox;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import com.googlecode.lanterna.graphics.SimpleTheme;
import com.googlecode.lanterna.screen.Screen;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.ui.prompt.PromptService;
import io.mindspice.magenta.ui.prompt.UiPromptRequest;
import io.mindspice.magenta.ui.prompt.UiPromptResponse;
import io.mindspice.magenta.ui.render.UiStatusBar;
import io.mindspice.magenta.ui.slash.SlashCommandAction;
import io.mindspice.magenta.ui.slash.SlashCommandInvocation;
import io.mindspice.magenta.ui.slash.SlashCommandParseResult;
import io.mindspice.magenta.ui.slash.SlashCommandParser;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.slash.SlashCommandSpec;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TerminalUiRuntime {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final RuntimeConfig runtimeConfig;
    private final Magenta magenta;
    private final Screen screen;
    private final WindowBasedTextGUI gui;
    private final TerminalUiConfig config;
    private final TerminalUiSession session;

    private final BasicWindow window;
    private final TranscriptView transcriptView;
    private final Label sessionHeaderLabel;
    private final Label sessionSubHeaderLabel;
    private final Label contextUsageLabel;
    private final ComposerInput inputBox;
    private final Panel promptPane;
    private final Panel promptContainer;
    private final Panel rightPane;
    private final FillSplitPanel contentSplit;
    private final FillSplitPanel leftVerticalSplit;

    private final PromptService promptService;
    private final SlashCommandParser slashParser = new SlashCommandParser();
    private final SlashCommandRegistry slashRegistry;

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean turnBusy = new AtomicBoolean(false);

    private final Object transcriptLock = new Object();
    private final ArrayDeque<TranscriptEntry> transcriptEntries = new ArrayDeque<>();
    private final StreamingAssistantBuffer streamingAssistant = new StreamingAssistantBuffer("assistant> ");
    private long transcriptSequence = 0L;

    private static final int MIN_LEFT_COLS = 56;
    private static final int MIN_RIGHT_COLS = 24;
    private static final int MIN_TRANSCRIPT_ROWS = 10;
    private static final int MIN_COMPOSER_ROWS = 4;

    private CompletableFuture<UiPromptResponse> activePromptFuture;

    TerminalUiRuntime(
            RuntimeConfig runtimeConfig,
            Magenta magenta,
            Screen screen,
            WindowBasedTextGUI gui,
            TerminalUiConfig config,
            TerminalUiSession session
    ) {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.screen = Objects.requireNonNull(screen, "screen");
        this.gui = Objects.requireNonNull(gui, "gui");
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");

        this.window = new MainWindow();
        this.window.setHints(List.of(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        this.gui.setTheme(new SimpleTheme(
                new TextColor.RGB(215, 220, 228),
                new TextColor.RGB(40, 44, 52)
        ));

        this.transcriptView = new TranscriptView();
        this.sessionHeaderLabel = new Label("").setForegroundColor(new TextColor.RGB(208, 214, 224));
        this.sessionSubHeaderLabel = new Label("").setForegroundColor(new TextColor.RGB(178, 188, 203));
        this.contextUsageLabel = new Label("").setForegroundColor(new TextColor.RGB(188, 196, 208));
        this.inputBox = new ComposerInput(this::onLineSubmitted, this::requestAbort);
        this.promptPane = new Panel(new LinearLayout(Direction.VERTICAL));
        this.promptContainer = new Panel(new BorderLayout());
        this.promptContainer.addComponent(promptPane.withBorder(Borders.singleLine("action")), BorderLayout.Location.CENTER);
        this.promptContainer.setVisible(false);

        Panel sessionHeader = new Panel(new LinearLayout(Direction.VERTICAL));
        sessionHeader.addComponent(sessionHeaderLabel);
        sessionHeader.addComponent(sessionSubHeaderLabel);

        Panel transcriptStack = new Panel(new BorderLayout());
        transcriptStack.addComponent(sessionHeader.withBorder(Borders.singleLine("session")), BorderLayout.Location.TOP);
        transcriptStack.addComponent(transcriptView.withBorder(Borders.singleLine("conversation")), BorderLayout.Location.CENTER);
        transcriptStack.addComponent(contextUsageLabel.withBorder(Borders.singleLine("context")), BorderLayout.Location.BOTTOM);

        this.rightPane = new Panel(new BorderLayout());
        Panel rightContent = new Panel(new LinearLayout(Direction.VERTICAL));
        rightContent.addComponent(new Label("context/task views coming soon"));
        rightPane.addComponent(rightContent, BorderLayout.Location.CENTER);

        Panel bottomStack = new Panel(new BorderLayout());
        bottomStack.addComponent(promptContainer, BorderLayout.Location.TOP);
        bottomStack.addComponent(inputBox.withBorder(Borders.singleLine("input")), BorderLayout.Location.CENTER);

        this.leftVerticalSplit = FillSplitPanel.vertical(transcriptStack, bottomStack, 0.84d);
        this.leftVerticalSplit.setMinimumPrimarySizes(MIN_TRANSCRIPT_ROWS, MIN_COMPOSER_ROWS);

        this.contentSplit = FillSplitPanel.horizontal(leftVerticalSplit, rightPane.withBorder(Borders.singleLine("views")), 0.76d);
        this.contentSplit.setMinimumPrimarySizes(MIN_LEFT_COLS, MIN_RIGHT_COLS);
        window.setComponent(contentSplit);

        this.promptService = new LanternaPromptService();
        this.slashRegistry = defaultCommands();

        gui.addWindow(window);
        inputBox.takeFocus();
    }

    public void runLoop() {
        renderBlock("system", List.of(
                "sessionId=" + session.handle().sessionId(),
                "workspaceRoot=" + runtimeConfig.workspaceRoot(),
                "Commands: /help, /session, /model, /clear, /task [name], /new, /compact, /approve-demo, /tool-approval <on|off>, /yolo <on|off>, /event <text>, /exit"
        ));
        renderStatus();

        while (!closed.get() && window.isVisible() && session.handle().isActive()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        close();
    }

    public PromptService promptService() {
        return promptService;
    }

    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            commandExecutor.shutdownNow();
        } catch (Exception ignored) {
            // best effort
        }

        try {
            magenta.removeRoute(session.outputRoute());
        } catch (Exception ignored) {
            // best effort
        }

        try {
            magenta.closeSession(session.handle());
        } catch (Exception ignored) {
            // best effort
        }

        try {
            gui.getGUIThread().invokeLater(() -> {
                try {
                    window.close();
                } catch (Exception ignored) {
                    // best effort
                }
            });
        } catch (Exception ignored) {
            // best effort
        }

        try {
            if (gui.getGUIThread() instanceof SeparateTextGUIThread asyncThread) {
                asyncThread.stop();
            }
        } catch (Exception ignored) {
            // best effort
        }

        try {
            screen.stopScreen();
        } catch (IOException ignored) {
            // best effort
        }
    }

    void onRoutingEvent(io.mindspice.magenta.runtime.routing.RoutingEvent event) {
        if (event == null || !config.observability().routingLogsEnabled()) {
            return;
        }
        RoutingEventFormatter formatter = new RoutingEventFormatter();
        renderBlock("route", formatter.format(event));
    }

    void onSecurityEvent(SecurityManager.SecurityEvent event) {
        if (event == null) {
            return;
        }
        if (shouldRenderSecurityEvent(config.security().eventVisibility(), event)) {
            String line = securityDisplayLine(event);
            renderBlock("security", List.of(line));
        }
        renderStatus();
    }

    void onSessionError(io.mindspice.magenta.runtime.session.SessionException error) {
        if (error == null) {
            return;
        }
        turnBusy.set(false);
        String detail = error.getCause() == null ? "unknown" : String.valueOf(error.getCause().getMessage());
        renderBlock("error", List.of(
                "sessionId=" + error.sessionHandle().sessionId(),
                "message=" + detail
        ));
        renderStatus();
    }

    void onContextCompacted(io.mindspice.magenta.runtime.events.SessionEvent.Action.ContextCompacted event) {
        if (event == null) {
            return;
        }
        renderBlock("context", List.of(contextCompactionDisplayLine(event)));
        renderStatus();
    }

    void onContextBudgetUpdate() {
        renderStatus();
    }

    void onFinalOutputReceived() {
        turnBusy.set(false);
        renderStatus();
    }

    void appendSystemMessage(String text) {
        renderBlock("system", MessageRole.SYSTEM, List.of(text));
    }

    void appendInfo(String text) {
        renderBlock("info", MessageRole.INFO, List.of(text));
    }

    void appendWarn(String text) {
        renderBlock("warn", MessageRole.WARN, List.of(text));
    }

    void appendError(String text) {
        renderBlock("error", MessageRole.ERROR, List.of(text));
    }

    void appendUser(String text) {
        renderBlock("user", MessageRole.USER, List.of(text));
    }

    void appendAssistant(String text) {
        renderBlock(assistantTitle(), MessageRole.ASSISTANT, List.of(text));
    }

    void appendAssistantToken(String token) {
        if (!streamingAssistant.appendToken(token)) {
            return;
        }
        updateStreamingAssistantBlock(streamingAssistant.content());
    }

    void finishAssistantStream() {
        if (!streamingAssistant.started()) {
            return;
        }
        synchronized (transcriptLock) {
            if (!transcriptEntries.isEmpty()) {
                TranscriptEntry last = transcriptEntries.peekLast();
                if (last != null && last.streaming()) {
                    transcriptEntries.removeLast();
                    transcriptEntries.addLast(last.withStreaming(false));
                    refreshTranscriptViewLocked();
                }
            }
        }
        streamingAssistant.reset();
    }

    void appendToolLine(String text) {
        renderBlock("tool", MessageRole.TOOL, List.of(text));
    }

    void appendTable(String title, List<String> headers, List<List<String>> rows) {
        List<String> lines = formatTable(headers, rows);
        renderBlock(title, roleFor(title), lines);
    }

    boolean onLineSubmitted(String line) {
        String submitted = line == null ? "" : line;
        String trimmed = submitted.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        if (turnBusy.get()) {
            appendWarn("turn in progress; wait for completion or abort with ctrl-c");
            return false;
        }

        turnBusy.set(true);
        commandExecutor.submit(() -> {
            boolean awaitingFinal = false;
            try {
                awaitingFinal = processInputLine(submitted);
            } finally {
                if (!awaitingFinal) {
                    turnBusy.set(false);
                    renderStatus();
                }
            }
        });
        return true;
    }

    private boolean processInputLine(String line) {
        String trimmed = line.trim();

        if (config.behavior().isExitCommand(trimmed)) {
            close();
            return false;
        }

        if (dispatchSlashCommand(trimmed)) {
            return false;
        }

        appendUser(line);
        session.messageIn().accept(SessionInput.userMessage(line));
        return true;
    }

    private boolean dispatchSlashCommand(String line) {
        SlashCommandParseResult parseResult = slashParser.parse(line);
        return switch (parseResult) {
            case SlashCommandParseResult.NotCommand ignored -> false;
            case SlashCommandParseResult.ParseError error -> {
                appendError("command parse error: " + error.message());
                yield true;
            }
            case SlashCommandParseResult.Parsed parsed -> {
                runSlashCommand(parsed.invocation());
                yield true;
            }
        };
    }

    private void runSlashCommand(SlashCommandInvocation invocation) {
        SlashCommandSpec spec = slashRegistry.find(invocation.name()).orElse(null);
        if (spec == null) {
            appendError("unknown command: /" + invocation.name());
            return;
        }

        List<String> args = invocation.args();
        int minArity = spec.action().minArity();
        int maxArity = spec.action().maxArity();
        if (args.size() < minArity || args.size() > maxArity) {
            appendError("usage: " + spec.usage());
            return;
        }

        try {
            switch (spec.action()) {
                case SlashCommandAction.ZeroArg zeroArg -> zeroArg.handler().run();
                case SlashCommandAction.OneArg oneArg -> oneArg.handler().accept(args.getFirst());
                case SlashCommandAction.OptionalOneArg optionalOneArg ->
                        optionalOneArg.handler().accept(args.isEmpty() ? "" : args.getFirst());
                case SlashCommandAction.TwoArg twoArg -> twoArg.handler().accept(args.get(0), args.get(1));
                case SlashCommandAction.ThreeArg threeArg -> threeArg.handler().accept(args.get(0), args.get(1), args.get(2));
            }
        } catch (Exception e) {
            appendError("command failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    void onTerminalResized(TerminalSize newSize) {
        runOnUi(() -> {
            inputBox.refreshLayout();
            transcriptView.refreshLayout();
        });
    }

    private void renderBlock(String title, List<String> lines) {
        renderBlock(title, roleFor(title), lines);
    }

    private void renderBlock(String title, MessageRole role, List<String> lines) {
        runOnUi(() -> {
            synchronized (transcriptLock) {
                finishStreamingEntryLocked();
                String payload = boxedBlockText(title, lines);
                transcriptEntries.addLast(new TranscriptEntry(nextTranscriptId(), role, payload, false));
                while (transcriptEntries.size() > 800) {
                    transcriptEntries.removeFirst();
                }
                refreshTranscriptViewLocked();
            }
        });
    }

    private void renderStatus() {
        runOnUi(() -> {
            Magenta.SessionContextUsage usage = session.contextUsageSupplier().get();
            var status = buildStatusBar(magenta, session.handle(), session.contextUsageSupplier());
            sessionHeaderLabel.setText(status.bottomLeft());
            sessionSubHeaderLabel.setText(status.topLeft() + " | " + status.bottomRight());
            contextUsageLabel.setText(
                    "ctx "
                    + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                    + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%) | "
                    + "messages " + usage.messageCount()
            );
        });
    }

    private void adjustHorizontalSplit(int deltaCols) {
        contentSplit.adjustBy(deltaCols);
        transcriptView.refreshLayout();
    }

    private void adjustVerticalSplit(int deltaRows) {
        leftVerticalSplit.adjustBy(deltaRows);
        inputBox.refreshLayout();
        transcriptView.refreshLayout();
    }

    private String boxedBlockText(String title, List<String> lines) {
        List<String> payload = new ArrayList<>();
        String safeTitle = title == null || title.isBlank() ? "event" : title;
        String stamp = config.rendering().showTimestamps() ? TS_FORMAT.format(Instant.now()) + " " : "";
        payload.add(stamp + "┌─ " + safeTitle);
        if (lines == null || lines.isEmpty()) {
            payload.add(stamp + "│ ");
        } else {
            for (String line : lines) {
                String value = line == null ? "" : line;
                String[] physicalLines = value.split("\\R", -1);
                for (String physicalLine : physicalLines) {
                    payload.add(stamp + "│ " + physicalLine);
                }
            }
        }
        payload.add(stamp + "└" + "─".repeat(Math.max(4, safeTitle.length() + 4)));
        return String.join("\n", payload);
    }

    private MessageRole roleFor(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "user" -> MessageRole.USER;
            case "assistant" -> MessageRole.ASSISTANT;
            case "system", "context" -> MessageRole.SYSTEM;
            case "tool" -> MessageRole.TOOL;
            case "warn", "warning", "security" -> MessageRole.WARN;
            case "error" -> MessageRole.ERROR;
            case "route" -> MessageRole.ROUTE;
            default -> MessageRole.INFO;
        };
    }

    private void scrollTranscriptBy(int delta) {
        if (delta == 0) {
            return;
        }
        runOnUi(() -> transcriptView.scrollBy(delta));
    }

    private String assistantTitle() {
        String agentId = magenta.settingsFor(session.handle()).agentId();
        if (agentId == null || agentId.isBlank()) {
            return "assistant";
        }
        return agentId;
    }

    private List<String> formatTable(List<String> headers, List<List<String>> rows) {
        List<String> safeHeaders = headers == null ? List.of() : headers;
        List<List<String>> safeRows = rows == null ? List.of() : rows;

        int columns = safeHeaders.isEmpty()
                ? safeRows.stream().mapToInt(List::size).max().orElse(0)
                : safeHeaders.size();
        if (columns == 0) {
            return List.of();
        }

        int[] widths = new int[columns];
        for (int i = 0; i < safeHeaders.size(); i++) {
            widths[i] = Math.max(widths[i], safe(safeHeaders.get(i)).length());
        }
        for (List<String> row : safeRows) {
            for (int i = 0; i < Math.min(columns, row.size()); i++) {
                widths[i] = Math.max(widths[i], safe(row.get(i)).length());
            }
        }

        List<String> output = new ArrayList<>();
        if (!safeHeaders.isEmpty()) {
            output.add(formatRow(safeHeaders, widths));
            output.add("-".repeat(formatRow(safeHeaders, widths).length()));
        }
        for (List<String> row : safeRows) {
            output.add(formatRow(row, widths));
        }
        return output;
    }

    private String formatRow(List<String> row, int[] widths) {
        List<String> values = new ArrayList<>(widths.length);
        for (int i = 0; i < widths.length; i++) {
            String value = i < row.size() ? safe(row.get(i)) : "";
            values.add(padRight(value, widths[i]));
        }
        return String.join(" | ", values);
    }

    private String padRight(String input, int width) {
        String value = safe(input);
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }

    private void runOnUi(Runnable runnable) {
        if (closed.get()) {
            return;
        }
        try {
            gui.getGUIThread().invokeLater(() -> {
                if (closed.get()) {
                    return;
                }
                runnable.run();
            });
        } catch (Exception ignored) {
            // best effort UI updates
        }
    }

    private void requestAbort() {
        if (!magenta.turnInProgress(session.handle())) {
            return;
        }
        if (magenta.abortTurn(session.handle())) {
            appendWarn("abort requested (ctrl-c)");
        }
    }

    private SlashCommandRegistry defaultCommands() {
        return new SlashCommandRegistry(List.of(
                SlashCommandSpec.zero(
                        "help",
                        List.of("h"),
                        "Show slash command help",
                        "/help",
                        () -> {
                            List<List<String>> rows = slashRegistry.commands().stream()
                                    .map(cmd -> List.of("/" + cmd.name(), cmd.help(), cmd.usage()))
                                    .toList();
                            appendTable("commands", List.of("Command", "Description", "Usage"), rows);
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
                            appendTable("session", List.of("Field", "Value"), List.of(
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
                                appendWarn("model switch blocked: turn in progress; wait for completion or abort first");
                                return;
                            }

                            List<RuntimeConfig.ModelConfig> models = magenta.availableModels();
                            if (models.isEmpty()) {
                                appendWarn("no enabled models available");
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
                                        return model.id() + " -> " + model.model() + " (" + model.provider() + ")" + current;
                                    })
                                    .toList();

                            UiPromptResponse response = promptService.prompt(new UiPromptRequest.SelectPrompt(
                                    "Switch Model",
                                    "Select model number for this session",
                                    options,
                                    defaultIndex
                            ));
                            if (!(response instanceof UiPromptResponse.SelectResponse selected)) {
                                appendWarn("model switch cancelled");
                                return;
                            }

                            RuntimeConfig.ModelConfig chosen = models.get(selected.selectedIndex());
                            if (chosen.id().equals(currentModelId)) {
                                appendInfo("active model unchanged => " + chosen.model() + " (" + chosen.id() + ")");
                                return;
                            }

                            try {
                                RuntimeConfig.ModelConfig next = magenta.switchModel(session.handle(), chosen.id());
                                appendInfo("active model => " + next.model() + " (" + next.id() + ")");
                            } catch (IllegalStateException e) {
                                appendWarn("model switch blocked: " + (e.getMessage() == null ? "unknown reason" : e.getMessage()));
                            }
                        }
                ),
                SlashCommandSpec.zero(
                        "clear",
                        List.of(),
                        "Clear visible terminal output only",
                        "/clear",
                        () -> runOnUi(() -> {
                            synchronized (transcriptLock) {
                                streamingAssistant.reset();
                                transcriptEntries.clear();
                                refreshTranscriptViewLocked();
                            }
                        })
                ),
                SlashCommandSpec.zero(
                        "new",
                        List.of(),
                        "Clear chat history and keep current system/task prompts",
                        "/new",
                        () -> {
                            List<Magenta.SystemMessageOccupancy> occupied = magenta.clearConversation(session.handle());
                            appendInfo("conversation cleared; retained system messages => " + occupied.size());
                            List<List<String>> rows = occupied.stream()
                                    .map(item -> List.of(String.valueOf(item.position()), String.valueOf(item.chars()), preview(item.content(), 72)))
                                    .toList();
                            appendTable("retained system", List.of("#", "Chars", "Preview"), rows);
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
                            appendTable("compact", List.of("Field", "Value"), List.of(
                                    List.of("changed", String.valueOf(result.changed())),
                                    List.of("tokens", before.estimatedContextTokens() + " -> " + after.estimatedContextTokens()),
                                    List.of("messages", before.messageCount() + " -> " + after.messageCount())
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
                                        appendWarn("task update cancelled");
                                        return;
                                    }
                                }
                                String appliedTask = magenta.applyTask(session.handle(), taskArg);
                                appendInfo("active task => " + appliedTask);
                                return;
                            }

                            UiPromptResponse textPrompt = promptService.prompt(new UiPromptRequest.TextPrompt(
                                    "Task Desc",
                                    "Enter task prompt text to apply as persistent system instruction",
                                    false,
                                    ""
                            ));
                            if (!(textPrompt instanceof UiPromptResponse.TextResponse textResponse)) {
                                appendWarn("task prompt cancelled");
                                return;
                            }

                            String promptText = textResponse.text() == null ? "" : textResponse.text().trim();
                            if (promptText.isBlank()) {
                                appendWarn("task prompt cancelled: empty");
                                return;
                            }
                            if (replacingExisting) {
                                UiPromptResponse replacePrompt = promptService.prompt(new UiPromptRequest.ConfirmPrompt(
                                        "Replace Task Prompt",
                                        "Replace active task '" + currentTask + "' with new anon task prompt?",
                                        false
                                ));
                                if (!(replacePrompt instanceof UiPromptResponse.ConfirmResponse confirm && confirm.approved())) {
                                    appendWarn("task prompt update cancelled");
                                    return;
                                }
                            }

                            String appliedTask = magenta.applyAnonTaskPrompt(session.handle(), promptText);
                            appendInfo("active task => " + appliedTask);
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
                                        appendInfo("approve-demo => " + (confirm.approved() ? "approved" : "denied"));
                                case UiPromptResponse.Cancelled cancelled ->
                                        appendWarn("approve-demo => cancelled: " + cancelled.reason());
                                default -> appendWarn("approve-demo => unexpected response");
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
                                appendError("usage: /tool-approval <on|off>");
                                return;
                            }

                            SecurityManager.ToolPolicy current = magenta.toolPolicy(session.handle());
                            SecurityManager.ToolPolicy next = new SecurityManager.ToolPolicy(
                                    promptMode ? RuntimeConfig.SecurityMode.PROMPT : RuntimeConfig.SecurityMode.BLACKLIST,
                                    current.devYoloOverride(),
                                    current.allowedTools(),
                                    current.deniedTools(),
                                    current.allowedPaths(),
                                    current.allowedCommands(),
                                    current.webAccess(),
                                    current.commandRules()
                            );
                            magenta.setToolPolicy(session.handle(), next);
                            appendInfo("tool approval mode => " + next.mode().name());
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
                                appendError("usage: /yolo <on|off>");
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
                            appendWarn("yolo override => " + (enabled ? "ON (full security bypass for this session)" : "OFF"));
                        }
                ),
                SlashCommandSpec.one(
                        "event",
                        List.of(),
                        "Send system event input",
                        "/event <text>",
                        List.of("text"),
                        text -> {
                            session.eventIn().accept(new SessionInput.SysEvent(text, config.behavior().systemEventSourceId(), false));
                            appendSystemMessage("event sent");
                        }
                )
        ));
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

    private static boolean shouldRenderSecurityEvent(
            TerminalUiConfig.SecurityEventVisibility visibility,
            SecurityManager.SecurityEvent event
    ) {
        return switch (visibility) {
            case OFF -> false;
            case ALL -> true;
            case DENIALS_ONLY -> event.decisionCode() == SecurityManager.DecisionCode.DENIED
                                 || event.decisionCode() == SecurityManager.DecisionCode.VALIDATION_ERROR;
        };
    }

    private static String contextCompactionDisplayLine(io.mindspice.magenta.runtime.events.SessionEvent.Action.ContextCompacted event) {
        return "[Context] Compacted: tokens " + event.tokensBefore()
               + " -> " + event.tokensAfter()
               + ", messages " + event.messagesBefore()
               + " -> " + event.messagesAfter()
               + ", strategy=" + event.strategy();
    }

    private static String securityDisplayLine(SecurityManager.SecurityEvent event) {
        String outcome = switch (event.decisionCode()) {
            case ALLOWED -> "Allowed";
            case OVERRIDE_ALLOWED -> "Allowed (Override)";
            case DENIED -> "Denied";
            case VALIDATION_ERROR -> "Validation Error";
        };
        String tool = formatToolName(event.toolName());
        String reason = compact(singleLine(event.reason()));
        if (reason.isBlank()) {
            return "[Security] " + outcome + " | " + tool;
        }
        return "[Security] " + outcome + " | " + tool + " | " + reason;
    }

    private static String formatToolName(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return "Tool";
        }
        String normalized = toolName.trim().replace('-', '_');
        String[] parts = normalized.split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                out.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.isEmpty() ? "Tool" : out.toString();
    }

    private static String singleLine(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replace('\n', ' ').trim();
    }

    private static String compact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 180) {
            return value;
        }
        return value.substring(0, 177) + "...";
    }

    private final class MainWindow extends BasicWindow {
        @Override
        public boolean handleInput(KeyStroke key) {
            if (key.getKeyType() == KeyType.EOF) {
                close();
                TerminalUiRuntime.this.close();
                return true;
            }
            if (key.getKeyType() == KeyType.Escape && activePromptFuture != null) {
                completePrompt(new UiPromptResponse.Cancelled("cancelled"));
                return true;
            }
            if (key.getKeyType() == KeyType.Escape) {
                requestAbort();
                return true;
            }
            if (key.getKeyType() == KeyType.Character
                && key.isCtrlDown()
                && (key.getCharacter() == 'c' || key.getCharacter() == 'C')) {
                requestAbort();
                return true;
            }
            if (key.isAltDown() && key.getKeyType() == KeyType.ArrowLeft) {
                adjustHorizontalSplit(-2);
                return true;
            }
            if (key.isAltDown() && key.getKeyType() == KeyType.ArrowRight) {
                adjustHorizontalSplit(2);
                return true;
            }
            if (key.isAltDown() && key.getKeyType() == KeyType.ArrowUp) {
                adjustVerticalSplit(1);
                return true;
            }
            if (key.isAltDown() && key.getKeyType() == KeyType.ArrowDown) {
                adjustVerticalSplit(-1);
                return true;
            }
            if (key.getKeyType() == KeyType.PageUp) {
                scrollTranscriptBy(-12);
                return true;
            }
            if (key.getKeyType() == KeyType.PageDown) {
                scrollTranscriptBy(12);
                return true;
            }
            return super.handleInput(key);
        }
    }

    private final class PromptOptionList extends ActionListBox {
        PromptOptionList(TerminalSize size) {
            super(size);
        }

        @Override
        public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
            if (keyStroke.getKeyType() == KeyType.Tab) {
                if (getItemCount() == 0) {
                    return Result.HANDLED;
                }
                setSelectedIndex(getSelectedIndex() + 1);
                return Result.HANDLED;
            }
            if (keyStroke.getKeyType() == KeyType.ReverseTab) {
                if (getItemCount() == 0) {
                    return Result.HANDLED;
                }
                setSelectedIndex(getSelectedIndex() - 1);
                return Result.HANDLED;
            }
            return super.handleKeyStroke(keyStroke);
        }
    }

    private final class PromptTextBox extends TextBox {
        private final Runnable onSubmit;

        PromptTextBox(String value, Runnable onSubmit) {
            super(new TerminalSize(80, 1), value == null ? "" : value, Style.SINGLE_LINE);
            this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");
        }

        @Override
        public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
            if (keyStroke.getKeyType() == KeyType.Enter) {
                onSubmit.run();
                return Result.HANDLED;
            }
            return super.handleKeyStroke(keyStroke);
        }
    }

    private final class LanternaPromptService implements PromptService {

        @Override
        public UiPromptResponse prompt(UiPromptRequest request) {
            Objects.requireNonNull(request, "request");
            CompletableFuture<UiPromptResponse> future = new CompletableFuture<>();
            runOnUi(() -> showPromptUi(request, future));
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new UiPromptResponse.Cancelled("interrupted");
            } catch (Exception e) {
                return new UiPromptResponse.Cancelled("prompt_failed");
            }
        }

        private void showPromptUi(UiPromptRequest request, CompletableFuture<UiPromptResponse> future) {
            if (activePromptFuture != null) {
                activePromptFuture.complete(new UiPromptResponse.Cancelled("replaced"));
            }
            activePromptFuture = future;
            promptPane.removeAllComponents();
            promptContainer.setVisible(true);

            Panel content = new Panel(new LinearLayout(Direction.VERTICAL));
            content.addComponent(new Label(request.title()));
            if (!request.message().isBlank()) {
                content.addComponent(new Label(request.message()));
            }

            switch (request) {
                case UiPromptRequest.ConfirmPrompt confirm -> {
                    PromptOptionList list = new PromptOptionList(new TerminalSize(64, 2));
                    list.addItem("Yes", () -> completePrompt(new UiPromptResponse.ConfirmResponse(true)));
                    list.addItem("No", () -> completePrompt(new UiPromptResponse.ConfirmResponse(false)));
                    list.setSelectedIndex(confirm.defaultYes() ? 0 : 1);
                    content.addComponent(list.withBorder(Borders.singleLine("choose")));
                    content.addComponent(new Label("Tab/Shift+Tab to cycle, Enter to select, Esc to cancel"));
                    promptPane.addComponent(content);
                    list.takeFocus();
                }
                case UiPromptRequest.SelectPrompt select -> {
                    if (select.options().isEmpty()) {
                        completePrompt(new UiPromptResponse.Cancelled("no_options"));
                        return;
                    }
                    PromptOptionList list = new PromptOptionList(new TerminalSize(100, Math.max(3, Math.min(10, select.options().size()))));
                    for (int i = 0; i < select.options().size(); i++) {
                        int index = i;
                        String label = (i + 1) + ") " + select.options().get(i);
                        list.addItem(label, () -> completePrompt(new UiPromptResponse.SelectResponse(index, select.options().get(index))));
                    }
                    int defaultIndex = Math.min(Math.max(0, select.defaultIndex()), select.options().size() - 1);
                    list.setSelectedIndex(defaultIndex);
                    content.addComponent(list.withBorder(Borders.singleLine("options")));
                    content.addComponent(new Label("Tab/Shift+Tab to cycle, Enter to select, Esc to cancel"));
                    promptPane.addComponent(content);
                    list.takeFocus();
                }
                case UiPromptRequest.TextPrompt text -> {
                    final PromptTextBox[] textBoxRef = new PromptTextBox[1];
                    PromptTextBox textBox = new PromptTextBox(text.defaultValue(), () -> {
                        String value = textBoxRef[0] == null ? "" : textBoxRef[0].getText();
                        if (!text.allowEmpty() && (value == null || value.isBlank())) {
                            completePrompt(new UiPromptResponse.Cancelled("empty_input"));
                            return;
                        }
                        if (value == null || value.isBlank()) {
                            completePrompt(new UiPromptResponse.TextResponse(text.defaultValue()));
                            return;
                        }
                        completePrompt(new UiPromptResponse.TextResponse(value));
                    });
                    textBoxRef[0] = textBox;
                    content.addComponent(textBox.withBorder(Borders.singleLine("text")));
                    content.addComponent(new Label("Enter to submit, Esc to cancel"));
                    promptPane.addComponent(content);
                    textBox.takeFocus();
                }
            }
        }

        private void completePrompt(UiPromptResponse response) {
            CompletableFuture<UiPromptResponse> future = activePromptFuture;
            activePromptFuture = null;
            promptContainer.setVisible(false);
            promptPane.removeAllComponents();
            inputBox.takeFocus();
            if (future != null) {
                future.complete(response);
            }
        }
    }

    private enum MessageRole {
        USER(new TextColor.RGB(171, 207, 255), new TextColor.RGB(47, 61, 79)),
        ASSISTANT(new TextColor.RGB(185, 231, 208), new TextColor.RGB(45, 70, 62)),
        SYSTEM(new TextColor.RGB(204, 211, 222), new TextColor.RGB(57, 61, 71)),
        TOOL(new TextColor.RGB(244, 214, 163), new TextColor.RGB(82, 68, 44)),
        WARN(new TextColor.RGB(255, 214, 137), new TextColor.RGB(96, 71, 33)),
        ERROR(new TextColor.RGB(255, 177, 177), new TextColor.RGB(92, 49, 57)),
        ROUTE(new TextColor.RGB(171, 200, 236), new TextColor.RGB(50, 59, 73)),
        INFO(new TextColor.RGB(215, 220, 228), new TextColor.RGB(52, 57, 67));

        private final TextColor foreground;
        private final TextColor background;

        MessageRole(TextColor foreground, TextColor background) {
            this.foreground = foreground;
            this.background = background;
        }

        TextColor foreground() {
            return foreground;
        }

        TextColor background() {
            return background;
        }
    }

    private void updateStreamingAssistantBlock(String content) {
        runOnUi(() -> {
            synchronized (transcriptLock) {
                String payload = boxedBlockText(assistantTitle(), List.of(content));
                TranscriptEntry last = transcriptEntries.peekLast();
                if (last != null && last.streaming()) {
                    transcriptEntries.removeLast();
                    transcriptEntries.addLast(new TranscriptEntry(last.id(), MessageRole.ASSISTANT, payload, true));
                } else {
                    transcriptEntries.addLast(new TranscriptEntry(nextTranscriptId(), MessageRole.ASSISTANT, payload, true));
                    while (transcriptEntries.size() > 800) {
                        transcriptEntries.removeFirst();
                    }
                }
                refreshTranscriptViewLocked();
            }
        });
    }

    private void finishStreamingEntryLocked() {
        if (!streamingAssistant.started()) {
            return;
        }
        TranscriptEntry last = transcriptEntries.peekLast();
        if (last != null && last.streaming()) {
            transcriptEntries.removeLast();
            transcriptEntries.addLast(last.withStreaming(false));
        }
        streamingAssistant.reset();
    }

    private void refreshTranscriptViewLocked() {
        List<TranscriptView.Block> blocks = transcriptEntries.stream()
                .map(entry -> new TranscriptView.Block(
                        entry.id(),
                        entry.role().foreground(),
                        entry.role().background(),
                        entry.text()
                ))
                .toList();
        transcriptView.setBlocks(blocks);
    }

    private long nextTranscriptId() {
        transcriptSequence++;
        return transcriptSequence;
    }

    private record TranscriptEntry(long id, MessageRole role, String text, boolean streaming) {
        private TranscriptEntry withStreaming(boolean streaming) {
            return new TranscriptEntry(id, role, text, streaming);
        }
    }

    private void completePrompt(UiPromptResponse response) {
        runOnUi(() -> {
            if (promptService instanceof LanternaPromptService service) {
                service.completePrompt(response);
            }
        });
    }
}
