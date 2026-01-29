package com.magenta;

import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.io.Input;
import com.magenta.io.TerminalIOManager;
import com.magenta.session.AgentSession;
import com.magenta.session.DefaultCommandHandler;
import com.magenta.session.SessionManager;
import com.magenta.session.StreamingChat;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        initConfigManager(args);

        // Create terminal IO (will be owned by SessionManager)
        TerminalIOManager terminalIO = initTerminalIO();

        // Create initial session
        AgentSession initialSession = initDefaultSession(terminalIO);

        // Initialize SessionManager and run
        try {
            SessionManager.initialize(terminalIO, initialSession);
            SessionManager sessionManager = SessionManager.getInstance();

            terminalIO.println("Starting Magenta...");
            sessionManager.run();
            terminalIO.println("Exiting...");

            sessionManager.close();
        } catch (Exception e) {
            System.err.println("Failed to run session: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
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
                .build();
    }
}
