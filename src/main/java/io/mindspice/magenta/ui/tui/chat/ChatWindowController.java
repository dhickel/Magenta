package io.mindspice.magenta.ui.tui.chat;

import io.mindspice.magenta.Magenta;
import io.mindspice.magenta.runtime.events.SessionEvent;
import io.mindspice.magenta.runtime.routing.RoutingEvent;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.session.SessionException;
import io.mindspice.magenta.runtime.session.SessionInput;
import io.mindspice.magenta.runtime.session.SessionOutput;
import io.mindspice.magenta.ui.AssistantOutputTarget;
import io.mindspice.magenta.ui.AssistantOutputWriter;
import io.mindspice.magenta.ui.RoutingEventFormatter;
import io.mindspice.magenta.ui.TerminalUiConfig;
import io.mindspice.magenta.ui.ToolOutputFormatter;
import io.mindspice.magenta.ui.slash.SlashCommandAction;
import io.mindspice.magenta.ui.slash.SlashCommandInvocation;
import io.mindspice.magenta.ui.slash.SlashCommandParseResult;
import io.mindspice.magenta.ui.slash.SlashCommandParser;
import io.mindspice.magenta.ui.slash.SlashCommandRegistry;
import io.mindspice.magenta.ui.slash.SlashCommandSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatWindowController implements ChatController {
    private static final int MAX_TOOL_LINE = 180;

    private final Magenta magenta;
    private final TerminalUiConfig config;
    private final ChatBinding binding;
    private final ChatWindow window;
    private final Runnable closeRuntime;

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean turnBusy = new AtomicBoolean(false);

    private final SlashCommandParser slashParser = new SlashCommandParser();
    private final SlashCommandRegistry slashRegistry;
    private final RoutingEventFormatter routingFormatter = new RoutingEventFormatter();
    private final ToolOutputFormatter toolFormatter = new ToolOutputFormatter();
    private final AssistantOutputWriter outputWriter;

    private final Object streamLock = new Object();
    private final StringBuilder streamBuffer = new StringBuilder();

    public ChatWindowController(
            Magenta magenta,
            TerminalUiConfig config,
            ChatBinding binding,
            ChatWindow window,
            Runnable closeRuntime
    ) {
        this.magenta = Objects.requireNonNull(magenta, "magenta");
        this.config = Objects.requireNonNull(config, "config");
        this.binding = Objects.requireNonNull(binding, "binding");
        this.window = Objects.requireNonNull(window, "window");
        this.closeRuntime = Objects.requireNonNull(closeRuntime, "closeRuntime");
        this.slashRegistry = buildSlashRegistry();
        this.outputWriter = new AssistantOutputWriter(new ChatOutputTarget(), false, assistantTitle(), false);
    }

    public void initializeWindowState() {
        renderStatus();
        window.focusComposer();
    }

    public void onRoutingEvent(RoutingEvent event) {
        if (event == null || !config.observability().routingLogsEnabled()) {
            return;
        }
        appendBlock("route", routingFormatter.format(event));
    }

    public void onSecurityEvent(SecurityManager.SecurityEvent event) {
        if (event == null) {
            return;
        }
        if (shouldRenderSecurityEvent(config.security().eventVisibility(), event)) {
            appendBlock("security", List.of(securityDisplayLine(event)));
        }
        renderStatus();
    }

    public void onSessionError(SessionException error) {
        if (error == null) {
            return;
        }
        turnBusy.set(false);
        String detail = error.getCause() == null ? "unknown" : String.valueOf(error.getCause().getMessage());
        appendBlock("error", List.of(
                "sessionId=" + error.sessionHandle().sessionId(),
                "message=" + detail
        ));
        renderStatus();
    }

    public void onContextCompacted(SessionEvent.Action.ContextCompacted event) {
        if (event == null) {
            return;
        }
        appendBlock("context", List.of(contextCompactionDisplayLine(event)));
        renderStatus();
    }

    public void onContextBudgetUpdate() {
        renderStatus();
    }

    public void onOutput(SessionOutput output) {
        if (output == null || closed.get()) {
            return;
        }
        outputWriter.onOutput(output);
        switch (output) {
            case SessionOutput.FinalOutput ignored -> {
                turnBusy.set(false);
                renderStatus();
            }
            case SessionOutput.ToolMessageOutput ignored -> renderStatus();
            case SessionOutput.StreamedOutput ignored -> {
            }
            case SessionOutput.ToolCallOutput ignored -> {
            }
        }
    }

    @Override
    public boolean submitComposerText(String text) {
        if (closed.get()) {
            return false;
        }
        String submitted = text == null ? "" : text;
        String trimmed = submitted.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (!binding.handle().isActive()) {
            markStaleSession();
            return false;
        }
        if (turnBusy.get()) {
            appendBlock("warn", List.of("turn in progress; wait for completion or abort with ctrl-c"));
            return false;
        }

        turnBusy.set(true);
        renderStatus();
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

    @Override
    public void requestAbort() {
        if (closed.get() || !binding.handle().isActive()) {
            return;
        }
        if (!magenta.turnInProgress(binding.handle())) {
            appendBlock("warn", List.of("no active turn to abort"));
            return;
        }
        if (magenta.abortTurn(binding.handle())) {
            appendBlock("warn", List.of("abort requested (ctrl-c)"));
        }
    }

    @Override
    public String requestCloseWindow() {
        if (closed.get()) {
            return "Chat window is already closed";
        }
        if (!binding.handle().isActive()) {
            closeRuntime.run();
            return "Closed chat window";
        }
        if (turnBusy.get() || magenta.turnInProgress(binding.handle())) {
            appendBlock("warn", List.of("cannot close chat window while a turn is in progress; abort first"));
            return "Cannot close chat window while a turn is in progress";
        }
        closeRuntime.run();
        return "Closed chat window and backing session";
    }

    public void shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            commandExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private boolean processInputLine(String line) {
        String normalized = stripTrailingLineBreaks(line);
        String trimmed = normalized.trim();

        if (config.behavior().isExitCommand(trimmed)) {
            closeRuntime.run();
            return false;
        }

        if (dispatchSlashCommand(trimmed)) {
            return false;
        }

        appendBlock("user", List.of(normalized));
        binding.messageIn().accept(SessionInput.userMessage(normalized));
        return true;
    }

    static String stripTrailingLineBreaks(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replaceFirst("\\R++\\z", "");
    }

    private boolean dispatchSlashCommand(String line) {
        SlashCommandParseResult parseResult = slashParser.parse(line);
        return switch (parseResult) {
            case SlashCommandParseResult.NotCommand ignored -> false;
            case SlashCommandParseResult.ParseError error -> {
                appendBlock("error", List.of("command parse error: " + error.message()));
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
            appendBlock("error", List.of("unknown command: /" + invocation.name()));
            return;
        }

        List<String> args = invocation.args();
        int minArity = spec.action().minArity();
        int maxArity = spec.action().maxArity();
        if (args.size() < minArity || args.size() > maxArity) {
            appendBlock("error", List.of("usage: " + spec.usage()));
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
                case SlashCommandAction.VarArg varArg -> varArg.handler().accept(args);
            }
        } catch (Exception e) {
            appendBlock("error", List.of(
                    "command failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
            ));
        }
    }

    private SlashCommandRegistry buildSlashRegistry() {
        return new SlashCommandRegistry(List.of(
                SlashCommandSpec.zero(
                        "help",
                        List.of("h"),
                        "Show slash command help",
                        "/help",
                        () -> {
                            List<String> lines = new ArrayList<>();
                            lines.add("/help - Show slash command help");
                            lines.add("/session - Show current session + context usage");
                            lines.add("/clear - Clear visible transcript only");
                            lines.add("/new - Clear conversation and keep system/task prompts");
                            lines.add("/close - Close this chat window and its backing session");
                            lines.add("/exit - Exit terminal UI");
                            appendBlock("help", lines);
                        }
                ),
                SlashCommandSpec.zero(
                        "session",
                        List.of("s"),
                        "Show current session + context usage",
                        "/session",
                        () -> {
                            var usage = binding.contextUsage().get();
                            var settings = magenta.settingsFor(binding.handle());
                            var policy = magenta.toolPolicy(binding.handle());
                            appendBlock("session", List.of(
                                    "sessionId=" + usage.sessionId(),
                                    "alias=" + settings.alias(),
                                    "model=" + usage.modelName() + " (" + usage.modelId() + ")",
                                    "context=" + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                                    + " (" + String.format(Locale.ROOT, "%.2f", usage.percentOfMaxContext()) + "%)",
                                    "messages=" + usage.messageCount(),
                                    "toolsEnabled=" + settings.toolsEnabled(),
                                    "streamingEnabled=" + settings.streamingEnabled(),
                                    "securityMode=" + policy.mode().name(),
                                    "yoloOverride=" + policy.devYoloOverride(),
                                    "activeTask=" + magenta.activeTask(binding.handle())
                            ));
                        }
                ),
                SlashCommandSpec.zero(
                        "clear",
                        List.of(),
                        "Clear visible terminal output only",
                        "/clear",
                        window::clearTranscript
                ),
                SlashCommandSpec.zero(
                        "new",
                        List.of(),
                        "Clear chat history and keep current system/task prompts",
                        "/new",
                        () -> {
                            List<Magenta.SystemMessageOccupancy> occupied = magenta.clearConversation(binding.handle());
                            appendBlock("info", List.of("conversation cleared; retained system messages => " + occupied.size()));
                        }
                ),
                SlashCommandSpec.zero(
                        "close",
                        List.of(),
                        "Close this chat window and its backing session",
                        "/close",
                        () -> requestCloseWindow()
                ),
                SlashCommandSpec.zero(
                        "exit",
                        List.of("quit"),
                        "Exit terminal UI",
                        "/exit",
                        closeRuntime
                )
        ));
    }

    private void markStaleSession() {
        window.setComposerEnabled(false);
        appendBlock("error", List.of("session handle is inactive; close and reopen the chat window to recover"));
        window.setStatus("session inactive");
    }

    private void renderStatus() {
        if (closed.get()) {
            return;
        }
        try {
            var usage = binding.contextUsage().get();
            var settings = magenta.settingsFor(binding.handle());
            String agent = (settings.agentId() == null || settings.agentId().isBlank()) ? "assistant" : settings.agentId();
            String shortSession = shortSessionId(usage.sessionId().toString());
            String title = "Chat | agent: " + agent + " | session: " + shortSession;
            String status = "context: " + usage.estimatedContextTokens() + "/" + usage.maxContextTokens()
                    + " (" + String.format(Locale.ROOT, "%.1f", usage.percentOfMaxContext()) + "%)"
                    + " | root: " + compactRoot(magenta.runtimeConfig().workspaceRoot().toString())
                    + " | model: " + settings.modelId()
                    + " | security: " + magenta.toolPolicy(binding.handle()).mode().name()
                    + " | yolo: " + (magenta.toolPolicy(binding.handle()).devYoloOverride() ? "on" : "off");
            appendUi(() -> {
                window.setTitle(title);
                window.setStatus(status);
            });
        } catch (Exception e) {
            appendUi(() -> window.setStatus("status unavailable"));
        }
    }

    private void appendBlock(String title, List<String> lines) {
        appendStyledBlock(title, title, lines);
    }

    private void appendStyledBlock(String title, String styleKey, List<String> lines) {
        appendUi(() -> window.appendBlock(title, styleKey, lines));
    }

    private void appendUi(Runnable runnable) {
        if (closed.get()) {
            return;
        }
        try {
            window.getApplication().invokeLater(() -> {
                if (!closed.get()) {
                    runnable.run();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private String assistantTitle() {
        String agentId = magenta.settingsFor(binding.handle()).agentId();
        return (agentId == null || agentId.isBlank()) ? "assistant" : agentId;
    }

    private String shortSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "unknown";
        }
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    private String compactRoot(String root) {
        if (root == null || root.isBlank()) {
            return ".";
        }
        return compact(root, 36);
    }

    private boolean shouldRenderSecurityEvent(
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

    private String contextCompactionDisplayLine(SessionEvent.Action.ContextCompacted event) {
        return "Compacted: tokens " + event.tokensBefore()
                + " -> " + event.tokensAfter()
                + ", messages " + event.messagesBefore()
                + " -> " + event.messagesAfter()
                + ", strategy=" + event.strategy();
    }

    private String securityDisplayLine(SecurityManager.SecurityEvent event) {
        String outcome = switch (event.decisionCode()) {
            case ALLOWED -> "Allowed";
            case OVERRIDE_ALLOWED -> "Allowed (Override)";
            case DENIED -> "Denied";
            case VALIDATION_ERROR -> "Validation Error";
        };
        String reason = compact(event.reason(), MAX_TOOL_LINE);
        if (reason.isBlank()) {
            return "Security " + outcome + " | " + event.toolName();
        }
        return "Security " + outcome + " | " + event.toolName() + " | " + reason;
    }

    private String compact(String value, int maxLen) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String single = value.replace('\n', ' ').replace('\r', ' ').trim();
        int limit = Math.max(32, maxLen);
        if (single.length() <= limit) {
            return single;
        }
        return single.substring(0, limit - 3) + "...";
    }

    private final class ChatOutputTarget implements AssistantOutputTarget {
        private String lastFinalizedStreamText = "";

        @Override
        public synchronized void printAssistantToken(String token) {
            if (token == null || token.isEmpty()) {
                return;
            }
            if (!streamingEnabledForSession()) {
                return;
            }
            synchronized (streamLock) {
                streamBuffer.append(token);
                String content = streamBuffer.toString();
                appendUi(() -> window.updateStreaming(assistantTitle(), "assistant", content));
            }
        }

        @Override
        public synchronized void finishAssistantStreamLine() {
            if (!streamingEnabledForSession()) {
                synchronized (streamLock) {
                    streamBuffer.setLength(0);
                    lastFinalizedStreamText = "";
                }
                return;
            }
            synchronized (streamLock) {
                lastFinalizedStreamText = streamBuffer.toString();
                streamBuffer.setLength(0);
                appendUi(window::finishStreaming);
            }
        }

        @Override
        public synchronized void printAssistantFinal(String text) {
            if (text == null || text.isBlank()) {
                return;
            }
            synchronized (streamLock) {
                if (!lastFinalizedStreamText.isBlank() && lastFinalizedStreamText.equals(text)) {
                    lastFinalizedStreamText = "";
                    return;
                }
                lastFinalizedStreamText = "";
            }
            appendStyledBlock(assistantTitle(), "assistant", List.of(text));
        }

        @Override
        public void printToolCall(String toolName, String argumentsJson) {
            ToolOutputFormatter.FormattedToolCall call = toolFormatter.formatCall(toolName, argumentsJson);
            appendBlock("tool", renderToolLines(call.title(), call.lines()));
        }

        @Override
        public void printToolResult(String toolName, String content, boolean failed) {
            ToolOutputFormatter.FormattedToolResult result = toolFormatter.formatResult(toolName, content);
            appendBlock("tool", renderToolLines(result.title(), result.lines()));
        }

        @Override
        public void printStreamFallbackNotice(String reason) {
            if (reason == null || reason.isBlank()) {
                return;
            }
            appendBlock("warn", List.of("stream-fallback> " + reason));
        }

        private boolean streamingEnabledForSession() {
            try {
                return magenta.settingsFor(binding.handle()).streamingEnabled();
            } catch (Exception ignored) {
                return false;
            }
        }

        private List<String> renderToolLines(String title, List<String> lines) {
            List<String> out = new ArrayList<>();
            if (title != null && !title.isBlank()) {
                out.add(title);
            }
            if (lines != null) {
                for (String line : lines) {
                    if (line == null) {
                        continue;
                    }
                    out.add(line);
                }
            }
            return out;
        }
    }
}
