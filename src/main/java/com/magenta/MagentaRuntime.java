package com.magenta;

import com.magenta.agent.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.ContextManager;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.session.AgentSession;
import com.magenta.session.DefaultCommandHandler;
import com.magenta.session.SessionAlias;
import com.magenta.session.SessionId;
import com.magenta.session.SessionManager;
import com.magenta.session.StreamingChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Global bootstrap and runtime holder for Magenta.
 * Centralizes initialization order for managers that are required at startup.
 */
public final class MagentaRuntime implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MagentaRuntime.class);
    private static volatile MagentaRuntime instance;

    private final Config config;
    private final AgentNetwork agentNetwork;
    private final ContextManager contextManager;
    private final TerminalIOManager terminalIO;
    private final AgentSession initialSession;
    private final SessionManager sessionManager;

    public static MagentaRuntime initialize(String[] args) {
        if (instance == null) {
            synchronized (MagentaRuntime.class) {
                if (instance == null) {
                    instance = new MagentaRuntime(args);
                }
            }
        }
        return instance;
    }

    public static MagentaRuntime getInstance() {
        if (instance == null) {
            throw new IllegalStateException("MagentaRuntime not initialized. Call initialize(args) first.");
        }
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    private MagentaRuntime(String[] args) {
        logger.info("Starting Magenta application...");

        this.config = initConfigManager(args);
        this.agentNetwork = AgentNetwork.getInstance();
        this.contextManager = ContextManager.getInstance();
        this.terminalIO = initTerminalIO();
        this.initialSession = initDefaultSession(terminalIO);
        this.sessionManager = initSessionManager(terminalIO, initialSession);
    }

    public Config config() {
        return config;
    }

    public AgentNetwork agentNetwork() {
        return agentNetwork;
    }

    public ContextManager contextManager() {
        return contextManager;
    }

    public TerminalIOManager terminalIO() {
        return terminalIO;
    }

    public AgentSession initialSession() {
        return initialSession;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public void run() {
        terminalIO.outputPipe().print("Starting Magenta...\n");
        sessionManager.run();
        terminalIO.outputPipe().print("Exiting...\n");
    }

    @Override
    public void close() throws Exception {
        logger.info("Shutting down...");
        sessionManager.close();
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
            ConfigManager.initialize(args == null ? new String[0] : args);
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

    private static SessionManager initSessionManager(TerminalIOManager ioManager, AgentSession initialSession) {
        try {
            SessionManager.initialize(ioManager, initialSession);
            return SessionManager.getInstance();
        } catch (Exception e) {
            logger.error("Failed to initialize SessionManager: {}", e.getMessage(), e);
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }
    }
}
