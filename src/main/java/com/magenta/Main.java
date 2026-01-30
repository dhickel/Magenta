package com.magenta;

import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.manager.ContextManager;
import com.magenta.context.policy.TokenLimitPolicy;
import com.magenta.context.store.SqliteContextRepository;
import com.magenta.data.DatabaseService;
import com.magenta.io.Input;
import com.magenta.io.TerminalIOManager;
import com.magenta.session.*;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        initConfigManager(args);

        // Initialize Database
        DatabaseService dbService = new DatabaseService();
        try {
            dbService.init();
        } catch (Exception e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        // Initialize Context Manager Singleton
        SqliteContextRepository contextRepo = new SqliteContextRepository(dbService);
        TokenLimitPolicy contextPolicy = new TokenLimitPolicy(8192); // Default max tokens
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
            SessionManager.initialize(terminalIO, initialSession, initialAlias);
            SessionManager sessionManager = SessionManager.getInstance();

            terminalIO.println("Starting Magenta...");
            sessionManager.run();
            terminalIO.println("Exiting...");

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
            return new TerminalIOManager();
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
                .inputParser(Input::defaultParser)
                .ioManager(ioManager)
                .sessionId(SessionId.random())
                .build();
    }
}