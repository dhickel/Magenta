package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.config.ConfigManager;
import com.magenta.io.*;
import com.magenta.security.SecurityManager;
import com.magenta.tools.ToolProvider;


public class AgentSession extends AbstractSession {
    // Per-agent state
    private final SecurityManager securityManager;
    private final ToolProvider toolProvider;
    private final Agent agent;
    private final MessageHandler<AgentSession> messageHandler;
    private final CommandHandler commandHandler;
    private final InputParser inputParser;

    public AgentSession(
            IOManager ioManager,
            SecurityManager securityManager,
            ToolProvider toolProvider,
            AgentConfig agentConfig,
            MessageHandler<AgentSession> messageHandler,
            CommandHandler commandHandler,
            InputParser inputParser
    ) {
        super(ioManager);
        this.agent = new Agent(agentConfig);
        this.securityManager = securityManager;
        this.toolProvider = toolProvider;
        this.messageHandler = messageHandler;
        this.commandHandler = commandHandler;
        this.inputParser = inputParser;
    }

    // Agent-specific methods
    @Override
    public void runOnce() {
        String raw = io().read("magenta> ");
        if (raw == null) { return; } // No input, skip iteration

        // Parse input and dispatch
        switch (inputParser.parse(raw)) {
            case Input.Cmd(Command cmd) -> commandHandler.handle(this, cmd);
            case Input.Msg(String text) -> messageHandler.processMessage(this, text);
        }
    }

    public void run() {
        while (!shouldExit()) {
            runOnce();
        }
    }

    // Override responseHandler to provide agent-specific color
    @Override
    public ResponseHandler responseHandler() {
        if (responseHandler == null) {
            int streamDelay = ConfigManager.config().streamDelayMs();
            Integer agentColor = agent.getColor();
            responseHandler = ioManager.createResponseHandler(agentColor, streamDelay);
        }
        return responseHandler;
    }

    // Per-agent state accessors
    public SecurityManager securityManager() {
        return securityManager;
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }

    public Agent agent() {
        return agent;
    }

    public MessageHandler<AgentSession> messageHandler() {
        return messageHandler;
    }

    public CommandHandler commandHandler() {
        return commandHandler;
    }


    // Builder pattern for encapsulated construction
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private AgentConfig agentConfig;
        private MessageHandler<AgentSession> messageHandler;
        private CommandHandler commandHandler;
        private InputParser inputParser;
        private IOManager ioManager;

        public Builder agent(AgentConfig agentConfig) {
            this.agentConfig = agentConfig;
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


        public Builder inputParser(InputParser parser) {
            this.inputParser = parser;
            return this;
        }

        public Builder ioManager(IOManager ioManager) {
            this.ioManager = ioManager;
            return this;
        }

        public AgentSession build() {
            validate();

            // Create per-agent SecurityManager from agent's security config
            SecurityManager securityManager = new SecurityManager(agentConfig.security());

            // Set security filter on IOManager
            ioManager.setSecurityFilter(securityManager.createFilter(ioManager));

            // Set colors config on IOManager if available and supported
            if (ioManager instanceof TerminalIOManager terminalIO) {
                var colors = agentConfig.colors();
                if (colors != null) {
                    terminalIO.setColorsConfig(colors);
                }
            }

            // Set cursor and cursor color on IOManager
            ioManager.setCursor(agentConfig.cursor(), agentConfig.cursorColor());

            // Create per-agent ToolProvider
            ToolProvider toolProvider = new ToolProvider(null, null, securityManager);

            // Create session with all components
            return new AgentSession(ioManager, securityManager, toolProvider, agentConfig,
                    messageHandler, commandHandler, inputParser);
        }

        private void validate() {
            if (agentConfig == null) {
                throw new IllegalStateException("agentConfig is required");
            }
            if (messageHandler == null) {
                throw new IllegalStateException("messageHandler is required");
            }
            if (commandHandler == null) {
                throw new IllegalStateException("commandHandler is required");
            }
            if (inputParser == null) {
                throw new IllegalStateException("inputParser is required");
            }
            if (ioManager == null) {
                throw new IllegalStateException("ioManager is required");
            }
        }
    }
}
