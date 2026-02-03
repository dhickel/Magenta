package com.magenta.session;

import com.magenta.agent.NetworkId;
import com.magenta.config.Config.AgentConfig;
import com.magenta.context.ContextLimits;
import com.magenta.io.IOManager;
import com.magenta.io.OutputStyle;
import com.magenta.io.ReadResult;
import com.magenta.io.ResponseHandler;
import com.magenta.io.terminal.Command;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityManager;
import com.magenta.tools.ToolProvider;
import com.magenta.task.TaskWorkflow;

import java.util.UUID;

/**
 * AgentSession manages a conversational session with an AI agent.
 * Simplified architecture:
 * - Agent owns SecurityFilter and CommandDetector
 * - Session applies security filtering to I/O
 * - IOManager is pure I/O, no business logic
 */
public class AgentSession implements Session {
    // Session identity
    private final SessionMeta metaData;

    // Session-level resources (IOManager is injected, not owned)
    private IOManager ioManager;
    private boolean exitFlag = false;
    private ResponseHandler responseHandler;

    // Per-agent state
    private final Agent agent;
    private final MessageHandler<AgentSession> messageHandler;
    private final CommandHandler commandHandler;
    private final ToolProvider toolProvider;
    private TaskWorkflow currentTaskWorkflow;

    public AgentSession(
            SessionAlias alias,
            AgentConfig agentConfig,
            SessionId sessionId
    ) {
        this(alias, agentConfig, sessionId, new StreamingChat(), new DefaultCommandHandler());
    }

    public AgentSession(
            SessionAlias alias,
            AgentConfig agentConfig,
            SessionId sessionId,
            MessageHandler<AgentSession> messageHandler,
            CommandHandler commandHandler
    ) {
        this.metaData = new SessionMeta(sessionId, alias, new NetworkId(UUID.randomUUID()));
        this.ioManager = null;
        this.agent = new Agent(agentConfig);
        this.messageHandler = messageHandler;
        this.commandHandler = commandHandler;

        // Create per-agent ToolProvider
        SecurityManager securityManager = SecurityManager.getInstance();
        securityManager.setConfig(agentConfig.security());
        ContextLimits limits = new ContextLimits(
            agentConfig.model().maxContext(),
            agentConfig.model().compactThreshold()
        );
        this.toolProvider = new ToolProvider(null, null, sessionId, limits);
    }

    // === Session Identity ===

    public SessionAlias alias() { return metaData.sessionAlias(); }

    public SessionId sessionId() { return metaData.sessionId(); }

    public NetworkId networkId() { return metaData.networkId(); }

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
    }

    @Override
    public void runOnce() {
        ReadResult result = io().read(
            agent.config().cursor(),
            securityFilter(),
            agent.commandDetector()
        );

        switch (result) {
            case ReadResult.Input(String content, var ts) -> {
                if (!content.isEmpty()) {
                    messageHandler.processMessage(this, content);
                }
            }
            case ReadResult.Cmd(Command cmd, var ts) -> {
                commandHandler.handle(this, cmd);
            }
            case ReadResult.Blocked(var original, String reason, var ts) -> {
                io().printStyled("[FILTERED] " + reason, OutputStyle.ERROR);
            }
        }
    }

    public void run() {
        while (!shouldExit()) {
            runOnce();
        }
    }

    @Override
    public ResponseHandler responseHandler() {
        if (responseHandler == null) {
            int streamDelay = com.magenta.config.ConfigManager.config().streamDelayMs();
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

    public Agent agent() {
        return agent;
    }

    public SecurityFilter securityFilter() {
        if (ioManager != null) {
            return agent.createSecurityFilterFor(ioManager);
        }
        return SecurityFilter.identity();
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public MessageHandler<AgentSession> messageHandler() {
        return messageHandler;
    }

    public CommandHandler commandHandler() {
        return commandHandler;
    }

    public TaskWorkflow currentWorkflowTask() {
        return currentTaskWorkflow;
    }

    public void setWorkflowTask(TaskWorkflow task) {
        this.currentTaskWorkflow = task;
    }

    // === Builder ===

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SessionAlias alias;
        private AgentConfig agentConfig;
        private SessionId sessionId;
        private MessageHandler<AgentSession> messageHandler;
        private CommandHandler commandHandler;
        private IOManager ioManager;

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

        public Builder commandHandler(CommandHandler handler) {
            this.commandHandler = handler;
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
            CommandHandler cmdHandler = commandHandler != null
                ? commandHandler
                : new DefaultCommandHandler();

            AgentSession session = new AgentSession(alias, agentConfig, sessionId, handler, cmdHandler);

            if (ioManager != null) {
                session.attachIO(ioManager);
            }

            return session;
        }

        private void validate() {
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