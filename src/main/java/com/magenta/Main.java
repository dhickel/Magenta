package com.magenta;

import com.magenta.agent.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.ContextManager;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        logger.info("Starting Magenta application...");
        // Initialize Config Manager
        initConfigManager(args);

        // Initialize AgentNetwork
        AgentNetwork.initialize();

        // Initialize Context Manager Singleton
        ContextManager.initialize();

        // Create terminal IO (will be owned by SessionManager)
        TerminalIOManager terminalIO = initTerminalIO();

        // Create initial session
        // Default session uses base agent name as alias
        String baseAgentName = ConfigManager.config().global().baseAgent();
        SessionAlias initialAlias = SessionAlias.of(baseAgentName);
        
        AgentSession initialSession = initDefaultSession(terminalIO);

        // Initialize SessionManager and run
        try {
            SessionManager.initialize(terminalIO, initialSession);
            SessionManager sessionManager = SessionManager.getInstance();

            terminalIO.outputPipe().print("Starting Magenta...\n");
            sessionManager.run();
            terminalIO.outputPipe().print("Exiting...\n");

            sessionManager.close();
        } catch (Exception e) {
            logger.error("Failed to run session: {}", e.getMessage(), e);
            System.exit(1);
        } finally {
            logger.info("Shutting down...");
        }
    }

    private static TerminalIOManager initTerminalIO() {
        try {
            logger.debug("Initializing TerminalIOManager...");
            return TerminalIOManager.getInstance();
        } catch (IOException e) {
            logger.error("Failed to create TerminalIOManager: {}", e.getMessage(), e);
            System.exit(1);
            return null;
        }
    }

    private static Config initConfigManager(String[] args) {
        try {
            logger.debug("Initializing ConfigManager...");
            ConfigManager.initialize(args);
            logger.info("Configuration loaded successfully");
            return ConfigManager.config();
        } catch (Exception e) {
            logger.error("Failed to initialize config: {}", e.getMessage(), e);
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }
    }

    private static AgentSession initDefaultSession(TerminalIOManager ioManager) {
        String baseAgentName = ConfigManager.config().global().baseAgent();
        SessionAlias initialAlias = SessionAlias.of(baseAgentName);

        return AgentSession.builder()
                .alias(initialAlias)
                .agent(ConfigManager.config().baseAgent())
                .messageHandler(new StreamingChat())
                .commandHandler(new DefaultCommandHandler())
                .ioManager(ioManager)
                .sessionId(SessionId.random())
                .build();
    }
}
