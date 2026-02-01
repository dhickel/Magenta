package com.magenta;

import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.manager.ContextManager;
import com.magenta.context.policy.TokenLimitPolicy;
import com.magenta.context.store.SqliteContextRepository;
import com.magenta.data.DatabaseService;
import com.magenta.io.TerminalIOManager;
import com.magenta.session.*;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        // Initialize Config Manager
        initConfigManager(args);

        // Initialize Database
        DatabaseService dbService = initDbService();

        // Initialize Context Manager Singleton
        SqliteContextRepository contextRepo = new SqliteContextRepository(dbService);
        TokenLimitPolicy contextPolicy = new TokenLimitPolicy(); // Default max tokens
        ContextManager.initialize(contextRepo, contextPolicy);

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
            System.err.println("Failed to run session: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            dbService.close();
        }
    }

    private static TerminalIOManager initTerminalIO() {
        try {
            return TerminalIOManager.getInstance();
        } catch (IOException e) {
            System.err.println("Failed to create TerminalIOManager: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }

    private static Config initConfigManager(String[] args) {
        try {
            ConfigManager.initialize(args);
            return ConfigManager.config();
        } catch (Exception e) {
            System.err.println("Failed to initialize config: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }
    }

    private static AgentSession initDefaultSession(TerminalIOManager ioManager) {
        return AgentSession.builder()
                .agent(ConfigManager.config().baseAgent())
                .messageHandler(new StreamingChat())
                .commandHandler(new DefaultCommandHandler())
                .ioManager(ioManager)
                .sessionId(SessionId.random())
                .build();
    }

    private static DatabaseService initDbService() {
        try {
            DatabaseService dbService = new DatabaseService();
            dbService.init();
            return dbService;
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
            return null;
        }
    }
}