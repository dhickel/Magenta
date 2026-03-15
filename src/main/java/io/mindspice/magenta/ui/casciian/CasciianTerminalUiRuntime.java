package io.mindspice.magenta.ui.casciian;

import casciian.TApplication;
import casciian.TEditor;
import casciian.TField;
import casciian.TKeypress;
import casciian.TList;
import casciian.TPanel;
import casciian.TSplitPane;
import casciian.TText;
import casciian.TWindow;
import casciian.bits.CellAttributes;
import casciian.bits.ColorTheme;
import casciian.event.TKeypressEvent;
import casciian.event.TMouseEvent;
import casciian.event.TResizeEvent;
import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionHandle;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.ui.RoutingEventFormatter;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.TerminalUiSession;
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

import java.io.UnsupportedEncodingException;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class CasciianTerminalUiRuntime {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final int MAX_TRANSCRIPT_ENTRIES = 800;

    private final CasciianLayoutSpec layoutSpec = CasciianLayoutSpec.defaults();
    private final RuntimeConfig runtimeConfig;
    private final Magenta magenta;
    private final TerminalUiConfig config;
    private final TerminalUiSession session;

    private final SlashCommandParser slashParser = new SlashCommandParser();
    private final SlashCommandRegistry slashRegistry;
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean turnBusy = new AtomicBoolean(false);

    private final Object transcriptLock = new Object();
    private final ArrayDeque<TranscriptEntry> transcriptEntries = new ArrayDeque<>();
    private final StreamingBuffer streamingAssistant = new StreamingBuffer("");

    private final MagentaApp app;
    private final CasciianPromptService promptService;
    private volatile Thread appThread;
    private volatile long transcriptSequence = 0L;

    public CasciianTerminalUiRuntime(
            RuntimeConfig runtimeConfig,
            Magenta magenta,
            TerminalUiConfig config,
            TerminalUiSession session
    ) throws Exception {
        this.runtimeConfig = Objects.requireNonNull(runtimeConfig, "runtimeConfig");
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.config = Objects.requireNonNull(config, "config");
        this.session = Objects.requireNonNull(session, "session");
        this.promptService = new CasciianPromptService();
        this.slashRegistry = defaultCommands();
        this.app = new MagentaApp();
    }

    public void runLoop() {
        appThread = Thread.ofPlatform().name("magenta-casciian-ui").start(app);
        awaitUiStartup();
        runOnUi(() -> {
            app.forceInitialLayoutPass();
            renderStatus();
            app.focusComposer();
        });

        while (!closed.get() && session.handle().isActive() && app.isRunningOrStarting()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        close();
        if (appThread != null) {
            try {
                appThread.join(TimeUnit.SECONDS.toMillis(2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
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
            app.invokeLater(app::shutdownUi);
        } catch (Exception ignored) {
            // best effort
        }
    }

    public void onRoutingEvent(io.mindspice.magenta.runtime.routing.RoutingEvent event) {
        if (event == null || !config.observability().routingLogsEnabled()) {
            return;
        }
        RoutingEventFormatter formatter = new RoutingEventFormatter();
        renderBlock("route", formatter.format(event));
    }

    public void onSecurityEvent(SecurityManager.SecurityEvent event) {
        if (event == null) {
            return;
        }
        if (shouldRenderSecurityEvent(config.security().eventVisibility(), event)) {
            renderBlock("security", List.of(securityDisplayLine(event)));
        }
        renderStatus();
    }

    public void onSessionError(io.mindspice.magenta.runtime.session.SessionException error) {
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

    public void onContextCompacted(io.mindspice.magenta.runtime.events.SessionEvent.Action.ContextCompacted event) {
        if (event == null) {
            return;
        }
        renderBlock("context", List.of(contextCompactionDisplayLine(event)));
        renderStatus();
    }

    public void onContextBudgetUpdate() {
        renderStatus();
    }

    public void onFinalOutputReceived() {
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
            TranscriptEntry last = transcriptEntries.peekLast();
            if (last != null && last.streaming()) {
                transcriptEntries.removeLast();
                transcriptEntries.addLast(last.withStreaming(false));
                refreshTranscriptViewLocked();
            }
        }
        streamingAssistant.reset();
    }

    void appendToolLine(String text) {
        renderBlock("tool", MessageRole.TOOL, List.of(text));
    }

    private void renderBlock(String title, List<String> lines) {
        renderBlock(title, roleFor(title), lines);
    }

    private void renderBlock(String title, MessageRole role, List<String> lines) {
        runOnUi(() -> {
            synchronized (transcriptLock) {
                finishStreamingEntryLocked();
                transcriptEntries.addLast(new TranscriptEntry(nextTranscriptId(), role, title, sanitizeLines(lines), false, Instant.now()));
                while (transcriptEntries.size() > MAX_TRANSCRIPT_ENTRIES) {
                    transcriptEntries.removeFirst();
                }
                refreshTranscriptViewLocked();
            }
        });
    }

    private void renderStatus() {
        runOnUi(() -> {
            Magenta.SessionContextUsage usage = session.contextUsageSupplier().get();
            UiStatusBar status = buildStatusBar(magenta, session.handle(), session.contextUsageSupplier());
            String agentId = magenta.settingsFor(session.handle()).agentId();
            app.updateSessionHeader(
                    "agent: " + agentId + " | " + status.topLeft() + " | " + status.bottomLeft() + " | " + status.bottomRight(),
                    "",
                    "root: " + runtimeConfig.workspaceRoot()
                    + " | context " + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                    + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%) | messages "
                    + usage.messageCount()
            );
        });
    }

    private void updateStreamingAssistantBlock(String content) {
        runOnUi(() -> {
            synchronized (transcriptLock) {
                TranscriptEntry last = transcriptEntries.peekLast();
                TranscriptEntry next = new TranscriptEntry(
                        last != null && last.streaming() ? last.id() : nextTranscriptId(),
                        MessageRole.ASSISTANT,
                        assistantTitle(),
                        List.of(content == null ? "" : content),
                        true,
                        Instant.now()
                );
                if (last != null && last.streaming()) {
                    transcriptEntries.removeLast();
                }
                transcriptEntries.addLast(next);
                while (transcriptEntries.size() > MAX_TRANSCRIPT_ENTRIES) {
                    transcriptEntries.removeFirst();
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
        app.setTranscriptEntries(List.copyOf(transcriptEntries));
    }

    private long nextTranscriptId() {
        transcriptSequence++;
        return transcriptSequence;
    }

    private void runOnUi(Runnable runnable) {
        if (closed.get()) {
            return;
        }
        try {
            app.invokeLater(() -> {
                if (!closed.get()) {
                    runnable.run();
                }
            });
        } catch (Exception ignored) {
            // best effort UI updates
        }
    }

    private void awaitUiStartup() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!closed.get() && !app.started() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean onLineSubmitted(String line) {
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

    private void requestAbort() {
        if (!magenta.turnInProgress(session.handle())) {
            return;
        }
        if (magenta.abortTurn(session.handle())) {
            appendWarn("abort requested (ctrl-c)");
        }
    }

    private void adjustHorizontalSplit(int deltaCols) {
        runOnUi(() -> app.adjustHorizontalSplit(deltaCols));
    }

    private void adjustVerticalSplit(int deltaRows) {
        runOnUi(() -> app.adjustVerticalSplit(deltaRows));
    }

    private void scrollTranscriptBy(int delta) {
        if (delta == 0) {
            return;
        }
        runOnUi(() -> app.scrollTranscriptBy(delta));
    }

    private String assistantTitle() {
        String agentId = magenta.settingsFor(session.handle()).agentId();
        if (agentId == null || agentId.isBlank()) {
            return "assistant";
        }
        return agentId;
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

    private List<String> sanitizeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of("");
        }
        List<String> sanitized = new ArrayList<>(lines.size());
        for (String line : lines) {
            sanitized.add(line == null ? "" : line);
        }
        return List.copyOf(sanitized);
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
            String headerLine = formatRow(safeHeaders, widths);
            output.add(headerLine);
            output.add("-".repeat(headerLine.length()));
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
                            renderBlock("commands", MessageRole.INFO, formatTable(List.of("Command", "Description", "Usage"), rows));
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
                            renderBlock("session", MessageRole.INFO, formatTable(List.of("Field", "Value"), List.of(
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
                            )));
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
                                    "Select model for this session",
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
                            renderBlock("retained system", MessageRole.INFO, formatTable(List.of("#", "Chars", "Preview"), rows));
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
                            renderBlock("compact", MessageRole.INFO, formatTable(List.of("Field", "Value"), List.of(
                                    List.of("changed", String.valueOf(result.changed())),
                                    List.of("tokens", before.estimatedContextTokens() + " -> " + after.estimatedContextTokens()),
                                    List.of("messages", before.messageCount() + " -> " + after.messageCount())
                            )));
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
                                    "Task Prompt",
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

        String topLeft = "model: " + settings.modelId();
        String topRight = "ctx: " + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                          + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%)";
        String bottomLeft = "session: " + settings.alias() + " [" + shortSessionId(usage.sessionId().toString()) + "]";
        String bottomRight = "tools=" + settings.toolsEnabled()
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

    private final class CasciianPromptService implements PromptService {
        @Override
        public UiPromptResponse prompt(UiPromptRequest request) {
            Objects.requireNonNull(request, "request");
            CompletableFuture<UiPromptResponse> future = new CompletableFuture<>();
            runOnUi(() -> app.showPrompt(request, future));
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new UiPromptResponse.Cancelled("interrupted");
            } catch (Exception e) {
                return new UiPromptResponse.Cancelled("prompt_failed");
            }
        }
    }

    private enum MessageRole {
        USER(CasciianTheme.roleBlock("user")),
        ASSISTANT(CasciianTheme.roleBlock("assistant")),
        SYSTEM(CasciianTheme.roleBlock("system")),
        TOOL(CasciianTheme.roleBlock("tool")),
        WARN(CasciianTheme.roleBlock("warn")),
        ERROR(CasciianTheme.roleBlock("error")),
        ROUTE(CasciianTheme.roleBlock("route")),
        INFO(CasciianTheme.roleBlock("info"));

        private final CellAttributes blockStyle;

        MessageRole(CellAttributes blockStyle) {
            this.blockStyle = blockStyle;
        }

        CellAttributes blockStyle() {
            return blockStyle;
        }
    }

    private record TranscriptEntry(
            long id,
            MessageRole role,
            String title,
            List<String> lines,
            boolean streaming,
            Instant timestamp
    ) {
        private TranscriptEntry withStreaming(boolean streaming) {
            return new TranscriptEntry(id, role, title, lines, streaming, timestamp);
        }
    }

    private final class MagentaApp extends TApplication {
        private final ConversationWindow conversationWindow;
        private final ViewsWindow viewsWindow;
        private volatile boolean started = false;
        private int lastScreenWidth = -1;
        private int lastScreenHeight = -1;
        private int leftCols = -1;
        private int topRows = -1;
        private CompletableFuture<UiPromptResponse> activePromptFuture;
        private PromptWindow activePromptWindow;

        private MagentaApp() throws UnsupportedEncodingException {
            super(BackendType.XTERM);
            CasciianTheme.applyDarkMinimal(getTheme());
            setHideStatusBar(true);
            setFocusFollowsMouse(true);
            this.conversationWindow = new ConversationWindow(this);
            this.viewsWindow = new ViewsWindow(this);
            layoutWindows(true);
            conversationWindow.activate();
        }

        @Override
        public void run() {
            started = true;
            super.run();
        }

        boolean isRunningOrStarting() {
            return started || !closed.get();
        }

        boolean started() {
            return started;
        }

        @Override
        protected void onPreDraw() {
            super.onPreDraw();
            layoutWindows(false);
        }

        @Override
        protected boolean onKeypress(TKeypressEvent event) {
            if (event.matchesKey(TKeypress.kbEsc)) {
                if (activePromptWindow != null) {
                    closePrompt(new UiPromptResponse.Cancelled("cancelled"));
                    return true;
                }
                requestAbort();
                return true;
            }
            if (event.matchesKey(TKeypress.kbCtrlC)) {
                requestAbort();
                return true;
            }
            if (event.matchesKey(TKeypress.kbAltLeft)) {
                adjustHorizontalSplit(-2);
                return true;
            }
            if (event.matchesKey(TKeypress.kbAltRight)) {
                adjustHorizontalSplit(2);
                return true;
            }
            if (event.matchesKey(TKeypress.kbAltUp)) {
                adjustVerticalSplit(1);
                return true;
            }
            if (event.matchesKey(TKeypress.kbAltDown)) {
                adjustVerticalSplit(-1);
                return true;
            }
            if (event.matchesKey(TKeypress.kbPgUp)) {
                scrollTranscriptBy(-12);
                return true;
            }
            if (event.matchesKey(TKeypress.kbPgDn)) {
                scrollTranscriptBy(12);
                return true;
            }
            return super.onKeypress(event);
        }

        private void layoutWindows(boolean force) {
            int width = Math.max(80, getScreen().getWidth());
            int height = Math.max(24, getScreen().getHeight());
            if (!force && width == lastScreenWidth && height == lastScreenHeight) {
                return;
            }
            lastScreenWidth = width;
            lastScreenHeight = height;

            int gutter = width >= 120 ? 1 : 0;
            CasciianLayoutSpec.Allocation cols = layoutSpec.allocateColumns(Math.max(2, width - gutter));
            leftCols = cols.primary();
            int rightCols = Math.max(layoutSpec.minRightCols(), width - gutter - leftCols);

            conversationWindow.setDimensions(0, 0, leftCols, height);
            viewsWindow.setDimensions(leftCols + gutter, 0, rightCols, height);
            conversationWindow.relayout(force);
            viewsWindow.relayout();
        }

        void forceInitialLayoutPass() {
            layoutWindows(true);
        }

        void setTranscriptEntries(List<TranscriptEntry> entries) {
            conversationWindow.setTranscriptEntries(entries);
        }

        void updateSessionHeader(String primary, String secondary, String context) {
            conversationWindow.updateHeader(primary, secondary, context);
        }

        void focusComposer() {
            conversationWindow.focusComposer();
        }

        void adjustHorizontalSplit(int deltaCols) {
            int screenWidth = Math.max(80, getScreen().getWidth());
            int gutter = screenWidth >= 120 ? 1 : 0;
            int usable = Math.max(2, screenWidth - gutter);
            int minLeft = layoutSpec.minLeftCols();
            int maxLeft = Math.max(minLeft, usable - layoutSpec.minRightCols());
            int current = leftCols <= 0 ? layoutSpec.allocateColumns(usable).primary() : leftCols;
            leftCols = Math.max(minLeft, Math.min(maxLeft, current + deltaCols));
            viewsWindow.setDimensions(leftCols + gutter, 0, Math.max(layoutSpec.minRightCols(), usable - leftCols), Math.max(24, getScreen().getHeight()));
            conversationWindow.setDimensions(0, 0, leftCols, Math.max(24, getScreen().getHeight()));
            conversationWindow.relayout(false);
            viewsWindow.relayout();
        }

        void adjustVerticalSplit(int deltaRows) {
            int innerHeight = conversationWindow.innerHeight();
            int requested = topRows <= 0 ? layoutSpec.allocateRows(innerHeight).primary() : topRows;
            int minTop = layoutSpec.minTopRows();
            int maxTop = Math.max(minTop, innerHeight - layoutSpec.minBottomRows());
            topRows = Math.max(minTop, Math.min(maxTop, requested + deltaRows));
            conversationWindow.setTopRowsOverride(topRows);
            conversationWindow.relayout(false);
        }

        void scrollTranscriptBy(int delta) {
            conversationWindow.scrollTranscriptBy(delta);
        }

        void showPrompt(UiPromptRequest request, CompletableFuture<UiPromptResponse> future) {
            if (activePromptFuture != null) {
                activePromptFuture.complete(new UiPromptResponse.Cancelled("replaced"));
            }
            if (activePromptWindow != null) {
                activePromptWindow.close();
            }
            activePromptFuture = future;
            activePromptWindow = new PromptWindow(this, request);
            activePromptWindow.activate();
        }

        void closePrompt(UiPromptResponse response) {
            CompletableFuture<UiPromptResponse> future = activePromptFuture;
            activePromptFuture = null;
            if (activePromptWindow != null) {
                PromptWindow window = activePromptWindow;
                activePromptWindow = null;
                window.close();
            }
            conversationWindow.focusComposer();
            if (future != null) {
                future.complete(response);
            }
        }

        void shutdownUi() {
            if (activePromptFuture != null) {
                closePrompt(new UiPromptResponse.Cancelled("shutdown"));
            }
            exit();
        }
    }

    private final class ConversationWindow extends TWindow {
        private final TSplitPane verticalSplit;
        private final TPanel transcriptPanel;
        private final TPanel inputPanel;
        private final TranscriptWidget transcriptWidget;
        private final ComposerEditor composer;
        private final StaticTextWidget headerPrimary;
        private final HorizontalSeparatorWidget headerSeparator;
        private final HorizontalSeparatorWidget footerSeparator;
        private final StaticTextWidget contextFooter;
        private int topRowsOverride = -1;

        private ConversationWindow(TApplication app) {
            super(app, "", 0, 0, 80, 24,
                    ABSOLUTEXY | NOCLOSEBOX | NOZOOMBOX | OVERRIDEMENU);
            setBorderStyleForeground("single");
            setBorderStyleInactive("single");
            setBorderStyleMoving("single");
            this.verticalSplit = addSplitPane(0, 0, Math.max(1, getWidth() - 2), Math.max(1, getHeight() - 2), false);

            this.transcriptPanel = new TPanel(null, 0, 0, 10, 10);
            this.transcriptPanel.setTitle("");
            this.inputPanel = new TPanel(null, 0, 0, 10, 10);
            this.inputPanel.setTitle("");
            this.verticalSplit.setTop(transcriptPanel);
            this.verticalSplit.setBottom(inputPanel);

            this.headerPrimary = new StaticTextWidget(transcriptPanel, 1, 0, 20, 1);
            this.headerSeparator = new HorizontalSeparatorWidget(transcriptPanel, 1, 1, 20);
            this.footerSeparator = new HorizontalSeparatorWidget(transcriptPanel, 1, 1, 20);
            this.transcriptWidget = new TranscriptWidget(transcriptPanel, 1, 2, 20, 8);

            this.composer = new ComposerEditor(inputPanel, 1, 1, 20, 4);
            this.contextFooter = new StaticTextWidget(transcriptPanel, 1, 1, 20, 1);
            relayout(true);
        }

        @Override
        public void onResize(TResizeEvent event) {
            super.onResize(event);
            if (event.getType() == TResizeEvent.Type.WIDGET || event.getType() == TResizeEvent.Type.SCREEN) {
                relayout(false);
            }
        }

        void relayout(boolean forceDefaultSplit) {
            int innerWidth = Math.max(1, getWidth() - 2);
            int innerHeight = Math.max(1, getHeight() - 2);
            verticalSplit.setDimensions(0, 0, innerWidth, innerHeight);

            int split = forceDefaultSplit || topRowsOverride <= 0
                    ? layoutSpec.allocateRows(innerHeight).primary()
                    : Math.max(
                            layoutSpec.minTopRows(),
                            Math.min(innerHeight - layoutSpec.minBottomRows(), topRowsOverride)
                    );
            verticalSplit.setSplit(Math.max(1, split));
            // Split-pane children can report previous dimensions for one cycle;
            // run content layout twice to converge immediately on startup.
            applyChildLayout();
            applyChildLayout();
        }

        private void applyChildLayout() {
            ConversationLayout layout = conversationLayoutFor(
                    transcriptPanel.getWidth(),
                    transcriptPanel.getHeight(),
                    inputPanel.getWidth(),
                    inputPanel.getHeight()
            );
            headerPrimary.setDimensions(1, layout.headerPrimaryY(), layout.transcriptWidth(), 1);
            headerSeparator.setDimensions(1, layout.headerSeparatorY(), layout.transcriptWidth());
            transcriptWidget.setDimensions(1, layout.transcriptY(), layout.transcriptWidth(), layout.transcriptRows());
            transcriptWidget.reflowData();
            footerSeparator.setDimensions(1, layout.footerSeparatorY(), layout.transcriptWidth());
            contextFooter.setDimensions(1, layout.footerY(), layout.transcriptWidth(), 1);
            composer.setDimensions(1, 0, layout.inputWidth(), layout.composerRows());
        }

        int innerHeight() {
            return Math.max(1, getHeight() - 2);
        }

        void setTopRowsOverride(int topRowsOverride) {
            this.topRowsOverride = topRowsOverride;
        }

        void updateHeader(String primary, String secondary, String context) {
            headerPrimary.setText(primary);
            contextFooter.setText(context);
        }

        void setTranscriptEntries(List<TranscriptEntry> entries) {
            transcriptWidget.setEntries(entries);
        }

        void focusComposer() {
            composer.activate();
        }

        void scrollTranscriptBy(int delta) {
            transcriptWidget.scrollBy(delta);
        }
    }

    private final class ViewsWindow extends TWindow {
        private final StaticTextWidget content;

        private ViewsWindow(TApplication app) {
            super(app, "", 0, 0, 30, 24,
                    ABSOLUTEXY | NOCLOSEBOX | NOZOOMBOX | OVERRIDEMENU);
            setBorderStyleForeground("single");
            setBorderStyleInactive("single");
            setBorderStyleMoving("single");
            this.content = new StaticTextWidget(this, 1, 1, 10, 10);
            this.content.setText("""
                    context/task views coming soon

                    This window is intentionally separate from the conversation window so future panes can be native Casciian windows instead of hand-drawn pseudo panes.
                    """);
            relayout();
        }

        @Override
        public void onResize(TResizeEvent event) {
            super.onResize(event);
            if (event.getType() == TResizeEvent.Type.WIDGET || event.getType() == TResizeEvent.Type.SCREEN) {
                relayout();
            }
        }

        void relayout() {
            content.setDimensions(1, 1, Math.max(12, getWidth() - 2), Math.max(6, getHeight() - 2));
        }
    }

    private final class PromptWindow extends TWindow {
        private PromptWindow(TApplication app, UiPromptRequest request) {
            super(app, request.title(), 0, 0, 72, 12, CENTERED | MODAL | NOCLOSEBOX | NOZOOMBOX);
            buildPrompt(request);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event.matchesKey(TKeypress.kbEsc)) {
                app.closePrompt(new UiPromptResponse.Cancelled("cancelled"));
                return;
            }
            super.onKeypress(event);
        }

        private void buildPrompt(UiPromptRequest request) {
            List<String> messageLines = wrapParagraphs(request.message(), Math.max(24, getWidth() - 4));
            int row = 1;
            for (String line : messageLines) {
                addLabel(line, 2, row++);
            }
            if (!messageLines.isEmpty()) {
                row++;
            }

            switch (request) {
                case UiPromptRequest.ConfirmPrompt confirm -> {
                    List<String> options = List.of("Approve", "Deny");
                    TList list = addList(options, 2, row, Math.max(24, getWidth() - 4), 3, new casciian.TAction() {
                        @Override
                        public void DO() {
                            app.closePrompt(new UiPromptResponse.ConfirmResponse(((TList) source).getSelectedIndex() == 0));
                        }
                    });
                    list.setSelectedIndex(confirm.defaultYes() ? 0 : 1);
                    list.activate();
                }
                case UiPromptRequest.SelectPrompt select -> {
                    if (select.options().isEmpty()) {
                        app.closePrompt(new UiPromptResponse.Cancelled("no_options"));
                        return;
                    }
                    int listHeight = Math.max(3, Math.min(10, select.options().size() + 1));
                    TList list = addList(select.options(), 2, row, Math.max(24, getWidth() - 4), listHeight, new casciian.TAction() {
                        @Override
                        public void DO() {
                            TList selected = (TList) source;
                            int index = selected.getSelectedIndex();
                            app.closePrompt(new UiPromptResponse.SelectResponse(index, select.options().get(index)));
                        }
                    });
                    list.setSelectedIndex(Math.min(Math.max(0, select.defaultIndex()), select.options().size() - 1));
                    list.activate();
                }
                case UiPromptRequest.TextPrompt text -> {
                    TField field = addField(2, row, Math.max(24, getWidth() - 4), false, text.defaultValue(), new casciian.TAction() {
                        @Override
                        public void DO() {
                            String value = ((TField) source).getText();
                            if (!text.allowEmpty() && (value == null || value.isBlank())) {
                                app.closePrompt(new UiPromptResponse.Cancelled("empty_input"));
                                return;
                            }
                            if ((value == null || value.isBlank()) && !text.defaultValue().isBlank()) {
                                app.closePrompt(new UiPromptResponse.TextResponse(text.defaultValue()));
                                return;
                            }
                            app.closePrompt(new UiPromptResponse.TextResponse(value));
                        }
                    });
                    field.activate();
                }
            }
        }
    }

    private final class ComposerEditor extends TEditor {
        private ComposerEditor(TPanel parent, int x, int y, int width, int height) {
            super(parent, "", x, y, width, height);
            setAutoWrap(true);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event.matchesKey(TKeypress.kbCtrlC)) {
                requestAbort();
                return;
            }
            if (event.matchesKey(TKeypress.kbAltLeft)) {
                adjustHorizontalSplit(-2);
                return;
            }
            if (event.matchesKey(TKeypress.kbAltRight)) {
                adjustHorizontalSplit(2);
                return;
            }
            if (event.matchesKey(TKeypress.kbAltUp)) {
                adjustVerticalSplit(1);
                return;
            }
            if (event.matchesKey(TKeypress.kbAltDown)) {
                adjustVerticalSplit(-1);
                return;
            }
            if (event.matchesKey(TKeypress.kbPgUp)) {
                scrollTranscriptBy(-12);
                return;
            }
            if (event.matchesKey(TKeypress.kbPgDn)) {
                scrollTranscriptBy(12);
                return;
            }
            if (event.matchesKey(TKeypress.kbCtrlN) || event.matchesKey(TKeypress.kbShiftEnter)) {
                replaceSelection("\n");
                return;
            }
            if (event.matchesKey(TKeypress.kbEnter)) {
                String text = getText();
                if (onLineSubmitted(trimTrailingBlankLines(text))) {
                    setText("");
                }
                return;
            }
            super.onKeypress(event);
        }

        private String trimTrailingBlankLines(String value) {
            if (value == null) {
                return "";
            }
            return value.replaceFirst("\\s+$", "");
        }
    }

    private static final class TranscriptWidget extends casciian.TScrollable {
        private List<TranscriptEntry> entries = List.of();
        private List<RenderedLine> renderedLines = List.of();

        private TranscriptWidget(TPanel parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height);
            this.vScroller = new casciian.TVScroller(this, Math.max(0, width - 1), 0, Math.max(1, height));
            setVerticalSmallChange(1);
            setVerticalBigChange(Math.max(1, height));
            reflowData();
        }

        @Override
        public void onResize(TResizeEvent event) {
            super.onResize(event);
            if (event.getType() == TResizeEvent.Type.WIDGET || event.getType() == TResizeEvent.Type.SCREEN) {
                reflowData();
            }
        }

        @Override
        public void onMouseDown(TMouseEvent event) {
            if (event.isMouseWheelUp()) {
                verticalDecrement();
                return;
            }
            if (event.isMouseWheelDown()) {
                verticalIncrement();
                return;
            }
            super.onMouseDown(event);
        }

        @Override
        public void onKeypress(TKeypressEvent event) {
            if (event.matchesKey(TKeypress.kbUp)) {
                verticalDecrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbDown)) {
                verticalIncrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbPgUp)) {
                bigVerticalDecrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbPgDn)) {
                bigVerticalIncrement();
                return;
            }
            if (event.matchesKey(TKeypress.kbHome)) {
                toTop();
                return;
            }
            if (event.matchesKey(TKeypress.kbEnd)) {
                toBottom();
                return;
            }
            super.onKeypress(event);
        }

        @Override
        public void draw() {
            int contentWidth = Math.max(1, getWidth() - 1);
            CellAttributes fill = getTheme().getColor(ColorTheme.TTEXT);
            int start = getVerticalValue();
            int row = 0;
            for (int index = start; index < renderedLines.size() && row < getHeight(); index++, row++) {
                RenderedLine line = renderedLines.get(index);
                hLineXY(0, row, Math.max(1, getWidth() - 1), ' ', fill);
                putStringXY(0, row, trimToWidth(line.text(), contentWidth), line.style());
            }
            while (row < getHeight()) {
                hLineXY(0, row, Math.max(1, getWidth() - 1), ' ', fill);
                row++;
            }
        }

        @Override
        public void reflowData() {
            boolean atBottom = getVerticalValue() >= Math.max(0, getBottomValue() - 1);
            int previousTop = getVerticalValue();
            int contentWidth = Math.max(12, getWidth() - 1);
            List<RenderedLine> nextLines = new ArrayList<>();
            for (TranscriptEntry entry : entries) {
                nextLines.addAll(renderEntry(entry, contentWidth));
                nextLines.add(new RenderedLine(" ".repeat(contentWidth), getTheme().getColor(ColorTheme.TTEXT)));
            }
            if (!nextLines.isEmpty()) {
                nextLines.remove(nextLines.size() - 1);
            }
            this.renderedLines = List.copyOf(nextLines);
            int visibleRows = Math.max(1, getHeight());
            setBottomValue(Math.max(0, renderedLines.size() - visibleRows));
            setVerticalBigChange(visibleRows);
            if (atBottom) {
                setVerticalValue(getBottomValue());
            } else {
                setVerticalValue(Math.min(previousTop, getBottomValue()));
            }
            placeScrollbars();
        }

        void setEntries(List<TranscriptEntry> entries) {
            this.entries = entries == null ? List.of() : List.copyOf(entries);
            reflowData();
        }

        void scrollBy(int delta) {
            setVerticalValue(Math.max(getTopValue(), Math.min(getBottomValue(), getVerticalValue() + delta)));
        }

        private List<RenderedLine> renderEntry(TranscriptEntry entry, int width) {
            List<RenderedLine> lines = new ArrayList<>();
            CellAttributes style = entry.role().blockStyle();
            lines.add(new RenderedLine(formatTranscriptTag(entry.title(), entry.timestamp(), width), style));
            for (String bodyLine : formatTranscriptBodyLines(entry.lines(), width)) {
                lines.add(new RenderedLine(bodyLine, style));
            }
            return lines;
        }

        private String trimToWidth(String text, int width) {
            if (text.length() >= width) {
                return text.substring(0, width);
            }
            return text;
        }

        private record RenderedLine(String text, CellAttributes style) {
        }
    }

    static String formatTranscriptTag(String title, Instant timestamp, int width) {
        int boundedWidth = Math.max(4, width);
        String label = title == null || title.isBlank() ? "event" : title;
        if (timestamp == null) {
            return trimToWidth("┌─ " + label, boundedWidth);
        }
        return trimToWidth("┌─ [" + TS_FORMAT.format(timestamp) + "] " + label, boundedWidth);
    }

    static List<String> formatTranscriptBodyLines(List<String> sourceLines, int width) {
        int boundedWidth = Math.max(4, width);
        int bodyWidth = Math.max(1, boundedWidth - 2);
        List<String> source = sourceLines == null || sourceLines.isEmpty() ? List.of("") : sourceLines;
        List<String> wrappedLines = new ArrayList<>();
        for (String rawLine : source) {
            List<String> wrapped = CasciianMessageFormatter.wordWrap(rawLine == null ? "" : rawLine, bodyWidth);
            if (wrapped.isEmpty()) {
                wrapped = List.of("");
            }
            wrappedLines.addAll(wrapped);
        }
        if (wrappedLines.isEmpty()) {
            wrappedLines.add("");
        }

        List<String> rendered = new ArrayList<>(wrappedLines.size());
        for (String wrappedLine : wrappedLines) {
            rendered.add(trimToWidth("│ " + wrappedLine, boundedWidth));
        }
        return List.copyOf(rendered);
    }

    static ConversationLayout conversationLayoutFor(int transcriptPanelWidth, int transcriptPanelHeight, int inputPanelWidth, int inputPanelHeight) {
        int transcriptWidth = Math.max(12, transcriptPanelWidth - 2);
        int headerPrimaryY = 0;
        int headerSecondaryY = 0;
        int headerSeparatorY = 1;
        int transcriptY = 2;
        int footerY = Math.max(transcriptY + 4, transcriptPanelHeight - 1);
        int footerSeparatorY = footerY - 1;
        int transcriptRows = Math.max(3, footerSeparatorY - transcriptY);
        int inputWidth = Math.max(12, inputPanelWidth - 2);
        int composerRows = Math.max(3, inputPanelHeight);
        return new ConversationLayout(
                transcriptWidth,
                headerPrimaryY,
                headerSecondaryY,
                headerSeparatorY,
                transcriptY,
                transcriptRows,
                footerSeparatorY,
                footerY,
                inputWidth,
                composerRows
        );
    }

    record ConversationLayout(
            int transcriptWidth,
            int headerPrimaryY,
            int headerSecondaryY,
            int headerSeparatorY,
            int transcriptY,
            int transcriptRows,
            int footerSeparatorY,
            int footerY,
            int inputWidth,
            int composerRows
    ) {
    }

    private static String trimToWidth(String text, int width) {
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        return text;
    }

    private static final class HorizontalSeparatorWidget extends casciian.TWidget {
        private HorizontalSeparatorWidget(casciian.TWidget parent, int x, int y, int width) {
            super(parent, x, y, width, 1);
        }

        void setDimensions(int x, int y, int width) {
            super.setDimensions(x, y, width, 1);
        }

        @Override
        public void draw() {
            CellAttributes fill = getTheme().getColor(ColorTheme.TTEXT);
            hLineXY(0, 0, Math.max(1, getWidth()), '─', fill);
        }
    }

    private static final class StaticTextWidget extends casciian.TWidget {
        private String text = "";

        private StaticTextWidget(casciian.TWidget parent, int x, int y, int width, int height) {
            super(parent, x, y, width, height);
        }

        void setText(String text) {
            this.text = text == null ? "" : text;
        }

        @Override
        public void draw() {
            CellAttributes fill = getTheme().getColor(ColorTheme.TTEXT);
            putBackgroundAttrBox(0, 0, getWidth(), getHeight(), fill);
            List<String> lines = wrapParagraphs(text, Math.max(1, getWidth()));
            int row = 0;
            for (; row < Math.min(getHeight(), lines.size()); row++) {
                putStringXY(0, row, padRight(lines.get(row), getWidth()), fill);
            }
            for (; row < getHeight(); row++) {
                putStringXY(0, row, " ".repeat(Math.max(0, getWidth())), fill);
            }
        }

        private String padRight(String value, int width) {
            String safe = value == null ? "" : value;
            if (safe.length() >= width) {
                return safe.substring(0, width);
            }
            return safe + " ".repeat(width - safe.length());
        }
    }

    private static List<String> wrapParagraphs(String text, int width) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return CasciianMessageFormatter.wordWrap(text, Math.max(8, width));
    }

    private static final class StreamingBuffer {
        private final String prefix;
        private final StringBuilder content = new StringBuilder();
        private boolean started = false;

        private StreamingBuffer(String prefix) {
            this.prefix = prefix == null ? "" : prefix;
        }

        boolean appendToken(String token) {
            if (token == null || token.isEmpty()) {
                return false;
            }
            if (!started) {
                content.append(prefix);
                started = true;
            }
            content.append(token);
            return true;
        }

        boolean started() {
            return started;
        }

        String content() {
            return content.toString();
        }

        void reset() {
            content.setLength(0);
            started = false;
        }
    }
}
