package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.context.policy.ContextLimits;
import com.magenta.io.*;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityManager;
import com.magenta.tools.ToolProvider;

import java.util.Optional;

/**
 * AgentSession manages a conversational session with an AI agent.
 * Simplified architecture:
 * - Agent owns SecurityFilter and CommandDetector
 * - Session applies security filtering to I/O
 * - IOManager is pure I/O, no business logic
 */
public class AgentSession extends AbstractSession {
    // Per-agent state
    private final Agent agent;
    private final ToolProvider toolProvider;
    private final MessageHandler<AgentSession> messageHandler;
    private final CommandHandler commandHandler;
    private final SessionId sessionId;

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
        super(alias, null); // IOManager will be attached later
        this.sessionId = sessionId;
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
        this.toolProvider = new ToolProvider(null, null, securityManager, sessionId, limits);
    }

    @Override
    public void attachIO(IOManager ioManager) {
        super.attachIO(ioManager);

        // Configure IOManager for this agent
        ioManager.setCursor(agent.config().cursor(), agent.config().cursorColor());

        // Set colors config if available and supported
        if (ioManager instanceof TerminalIOManager terminalIO) {
            var colors = agent.config().colors();
            if (colors != null) {
                terminalIO.setColorsConfig(colors);
            }
        }
    }

    @Override
    public void runOnce() {
        String raw = io().read(agent.config().cursor());
        if (raw == null || raw.isEmpty()) {
            return; // No input, skip iteration
        }

        // Detect command using agent's detector
        Optional<Command> cmd = agent.commandDetector().detect(raw);

        if (cmd.isPresent()) {
            // Handle command
            commandHandler.handle(this, cmd.get());
        } else {
            // Apply security filter to message
            Message.Input input = Message.input(raw);
            SecurityFilter filter = securityFilter();
            Message filtered = filter.inputFilter().apply(input, io());

            if (filtered instanceof Message.Input validInput) {
                // Process valid message
                messageHandler.processMessage(this, validInput.content());
            } else if (filtered instanceof Message.Filtered f) {
                // Show filtered message
                io().print(Message.system("[FILTERED] " + f.reason(), OutputStyle.ERROR));
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

    // === Accessors ===

    public Agent agent() {
        return agent;
    }

    public SecurityFilter securityFilter() {
        // Create security filter on-demand when IOManager is available
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

    public SessionId sessionId() {
        return sessionId;
    }

    // === Builder (simplified) ===

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

            // Use defaults if not provided
            MessageHandler<AgentSession> handler = messageHandler != null
                ? messageHandler
                : new StreamingChat();
            CommandHandler cmdHandler = commandHandler != null
                ? commandHandler
                : new DefaultCommandHandler();

            AgentSession session = new AgentSession(alias, agentConfig, sessionId, handler, cmdHandler);

            // Attach IOManager if provided
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