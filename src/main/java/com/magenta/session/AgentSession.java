package com.magenta.session;

import com.magenta.Magenta;
import com.magenta.config.Config.AgentConfig;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.io.OutputStyle;
import com.magenta.io.ReadResult;
import com.magenta.io.ResponseHandler;
import com.magenta.io.terminal.TerminalDisplay;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.CommandSet;
import com.magenta.security.SecurityFilter;
import com.magenta.manager.SecurityManager;
import com.magenta.task.TaskWorkflow;
import org.jline.utils.AttributedString;

import java.util.List;
import java.util.UUID;

/**
 * AgentSession manages a conversational session with an AI agent.
 * Simplified architecture:
 * - Agent owns SecurityFilter and command registry
 * - Session applies security filtering to I/O
 * - IOManager is pure I/O, no business logic
 */
public class AgentSession implements Session {
    // Services container
    private final Magenta magenta;

    // Session identity
    private final SessionMeta metaData;

    // Session-level resources (IOManager is injected, not owned)
    private IOManager ioManager;
    private boolean exitFlag = false;
    private ResponseHandler responseHandler;

    // Per-agent state
    private final Agent agent;
    private final MessageHandler<AgentSession> messageHandler;
    private final CommandSet commandSet;
    private final ContextLimits contextLimits;
    private TaskWorkflow currentTaskWorkflow;

    // View state
    private TerminalView currentView = new TerminalView.Chat();
    private long lastStateHash = 0;

    public AgentSession(
            Magenta magenta,
            SessionAlias alias,
            AgentConfig agentConfig,
            SessionId sessionId
    ) {
        this(magenta, alias, agentConfig, sessionId, new StreamingChat(), CommandSet.empty());
    }

    public AgentSession(
            Magenta magenta,
            SessionAlias alias,
            AgentConfig agentConfig,
            SessionId sessionId,
            MessageHandler<AgentSession> messageHandler,
            CommandSet sessionCommands
    ) {
        this.magenta = magenta;
        this.metaData = new SessionMeta(sessionId, alias, com.magenta.agent.NetworkId.random());
        this.ioManager = null;
        this.agent = new Agent(agentConfig);
        this.messageHandler = messageHandler;
        this.commandSet = new SystemCommands(magenta).commands()
            .composedWith(agent.commands())
            .composedWith(sessionCommands);

        // Create per-agent context limits
        magenta.securityManager().setConfig(agentConfig.security());
        this.contextLimits = new ContextLimits(
            agentConfig.model().maxContext(),
            agentConfig.model().compactThreshold()
        );
    }

    // === Session Identity ===

    public SessionAlias alias() { return metaData.sessionAlias(); }

    public SessionId sessionId() { return metaData.sessionId(); }

    public com.magenta.agent.NetworkId networkId() { return metaData.networkId(); }

    public SessionMeta sessionMeta() { return metaData; }

    // === Session Interface ===

    @Override
    public IOManager io() { return ioManager; }

    @Override
    public boolean shouldExit() { return exitFlag; }

    @Override
    public void setExit(boolean exit) { this.exitFlag = exit; }

    @Override
    public void attachIO(IOManager io) {
        this.ioManager = io;
        this.responseHandler = null; // Clear cached handler

        // Configure IOManager for this agent
        io.setCursor(agent.config().cursor(), agent.config().cursorColor());

        // Set colors config if available and supported
        if (io instanceof TerminalIOManager terminalIO) {
            var colors = agent.config().colors();
            if (colors != null) {
                terminalIO.setColorsConfig(colors);
            }
        }

        // Initialize tools with session context
        agent.initTools(io, sessionId(), contextLimits, alias(), magenta);
    }

    @Override
    public void runOnce() {
        // Read input
        ReadResult result = io().read(
            agent.config().cursor(),
            securityFilter(),
            commandSet
        );

        // Process based on result type
        switch (result) {
            case ReadResult.Input(String content, var ts) -> {
                // Let view handle input first
                boolean handled = currentView.handleInput(this, content);

                // If view didn't handle, process normally
                if (!handled && !content.isEmpty()) {
                    messageHandler.processMessage(this, content);
                }
            }
            case ReadResult.Cmd(Command cmd, String raw, var ts) -> {
                cmd.handle(this, raw);
            }
            case ReadResult.Blocked(var original, String reason, var ts) -> {
                io().printStyled("[FILTERED] " + reason, OutputStyle.ERROR);
            }
        }

        // Redraw after processing
        redraw();
    }

    public void run() {
        while (!shouldExit()) {
            runOnce();
        }
    }

    @Override
    public ResponseHandler responseHandler() {
        if (responseHandler == null) {
            int streamDelay = magenta.config().streamDelayMs();
            Integer agentColor = agent.config().resolveColor();
            responseHandler = ioManager.createResponseHandler(agentColor, streamDelay);
        }
        return responseHandler;
    }

    @Override
    public void close() throws Exception {
        // Don't close ioManager - we don't own it (SessionManager does)
    }

    // === Accessors ===

    public Magenta magenta() {
        return magenta;
    }

    public Agent agent() {
        return agent;
    }

    /**
     * Get the agent configuration name for this session.
     * @return Agent config name (e.g., "default", "helpful")
     */
    public String agentConfigName() {
        return agent.config().name();
    }

    public SecurityFilter securityFilter() {
        if (ioManager != null) {
            return agent.createSecurityFilterFor(ioManager, magenta.securityManager());
        }
        return SecurityFilter.identity();
    }

    public MessageHandler<AgentSession> messageHandler() {
        return messageHandler;
    }

    public List<Command> commands() {
        return commandSet.commands();
    }

    public CommandSet commandSet() {
        return commandSet;
    }

    public TaskWorkflow currentWorkflowTask() {
        return currentTaskWorkflow;
    }

    public void setWorkflowTask(TaskWorkflow task) {
        this.currentTaskWorkflow = task;
    }

    public ContextLimits contextLimits() {
        return contextLimits;
    }

    // === View Management ===

    /**
     * Get current terminal view.
     */
    public TerminalView currentView() {
        return currentView;
    }

    /**
     * Switch to a new terminal view.
     * Triggers immediate redraw.
     *
     * @param view New view to display
     */
    public void setView(TerminalView view) {
        this.currentView = view;
        forceRedraw();
    }

    /**
     * Calculate state hash for redraw optimization.
     * Only redraws if state changed.
     */
    private long calculateStateHash() {
        long hash = currentView.getClass().hashCode();
        hash = 31 * hash + agent.hashCode();
        hash = 31 * hash + metaData.sessionId().hashCode();

        // Include context token count
        if (magenta.contextManager() != null) {
            var ctx = magenta.contextManager().loadContext(sessionId());
            hash = 31 * hash + ctx.totalEstimatedTokens();
        }

        return hash;
    }

    /**
     * Redraw current view with caching.
     * Only updates display if state has changed since last redraw.
     */
    private void redraw() {
        if (!(ioManager instanceof TerminalIOManager.TerminalIOProxy proxy)) {
            return; // Not a terminal proxy, skip rendering
        }

        long stateHash = calculateStateHash();
        if (stateHash == lastStateHash) {
            return; // State unchanged, skip redraw
        }

        TerminalDisplay display = proxy.display();
        List<AttributedString> lines = currentView.render(this, display);

        if (!lines.isEmpty()) {
            display.updateLines(lines);
        }

        lastStateHash = stateHash;
    }

    /**
     * Force redraw (bypass cache).
     * Use when state hash doesn't capture the change.
     */
    public void forceRedraw() {
        lastStateHash = 0;
        redraw();
    }

    // === Builder ===

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Magenta magenta;
        private SessionAlias alias;
        private AgentConfig agentConfig;
        private SessionId sessionId;
        private MessageHandler<AgentSession> messageHandler;
        private CommandSet commands = CommandSet.empty();
        private IOManager ioManager;

        public Builder magenta(Magenta magenta) {
            this.magenta = magenta;
            return this;
        }

        public Builder alias(SessionAlias alias) {
            this.alias = alias;
            return this;
        }

        public Builder agent(AgentConfig agentConfig) {
            this.agentConfig = agentConfig;
            return this;
        }

        public Builder sessionId(SessionId sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder messageHandler(MessageHandler<AgentSession> handler) {
            this.messageHandler = handler;
            return this;
        }

        public Builder commands(CommandSet commands) {
            this.commands = commands != null ? commands : CommandSet.empty();
            return this;
        }

        public Builder ioManager(IOManager ioManager) {
            this.ioManager = ioManager;
            return this;
        }

        public AgentSession build() {
            validate();

            MessageHandler<AgentSession> handler = messageHandler != null
                ? messageHandler
                : new StreamingChat();
            AgentSession session = new AgentSession(magenta, alias, agentConfig, sessionId, handler, commands);

            if (ioManager != null) {
                session.attachIO(ioManager);
            }

            return session;
        }

        private void validate() {
            if (magenta == null) {
                throw new IllegalStateException("magenta is required");
            }
            if (alias == null) {
                throw new IllegalStateException("alias is required");
            }
            if (agentConfig == null) {
                throw new IllegalStateException("agentConfig is required");
            }
            if (sessionId == null) {
                throw new IllegalStateException("sessionId is required");
            }
        }
    }

}
