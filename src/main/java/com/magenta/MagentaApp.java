package com.magenta;

import com.magenta.manager.AgentNetwork;
import com.magenta.config.Arg;
import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.manager.ContextManager;
import com.magenta.io.terminal.TerminalIOManager;
import com.magenta.persistence.Database;
import com.magenta.manager.SecurityManager;
import com.magenta.manager.SessionManager;
import com.magenta.session.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Terminal application orchestrator for Magenta.
 * Owns all thread creation and lifecycle management.
 * NOT a singleton - regular AutoCloseable class.
 *
 * <p>Framework users don't need this class - they create a {@link Magenta}
 * record directly and wire their own components.
 */
public final class MagentaApp implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(MagentaApp.class);

    private final Magenta magenta;
    private final TerminalIOManager terminalIO;
    private final SessionManager sessionManager;
    private final TerminalSession terminalSession;
    private final ScheduledExecutorService flushScheduler;

    public MagentaApp(String[] args) throws Exception {
        logger.info("Starting Magenta application...");

        // 1. Load config
        Config config = ConfigManager.load(args);
        Map<Arg, Arg.Value> parsedArgs = ConfigManager.parseArgs(args);

        // 2. Create services
        Database database = initDatabase(config, parsedArgs);
        ContextManager contextManager = new ContextManager(database);
        AgentNetwork agentNetwork = new AgentNetwork();
        SecurityManager securityManager = new SecurityManager();
        this.magenta = new Magenta(config, database, contextManager, agentNetwork, securityManager);

        // 3. Create terminal app components
        this.terminalIO = new TerminalIOManager();
        this.sessionManager = new SessionManager();
        this.terminalSession = new TerminalSession(terminalIO, sessionManager, magenta);

        // 4. Build and register initial session
        String baseAgentName = config.global().baseAgent();
        SessionAlias initialAlias = SessionAlias.of(baseAgentName);
        AgentSession initialSession = AgentSession.builder()
                .magenta(magenta)
                .alias(initialAlias)
                .agent(config.baseAgent())
                .messageHandler(new StreamingChat())
                .sessionId(SessionId.random())
                .commands(terminalSession.terminalCommands())
                .build();

        terminalSession.registerSession(initialSession);
        terminalSession.switchTo(initialAlias);

        // 5. Set up command completion
        MagentaCompleter completer = new MagentaCompleter(terminalSession);
        terminalIO.setCompleter(completer);

        // 6. Start concurrency (owned here, not in managers)
        this.flushScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MagentaApp-ContextFlush");
            t.setDaemon(true);
            return t;
        });
        flushScheduler.scheduleAtFixedRate(
            contextManager::flushDirtyContexts, 30, 30, TimeUnit.SECONDS
        );
    }

    public Magenta magenta() {
        return magenta;
    }

    public void run() {
        terminalIO.print("Starting Magenta...\n");
        terminalSession.run();
        terminalIO.print("Exiting...\n");
    }

    @Override
    public void close() throws Exception {
        logger.info("Shutting down...");

        // Shutdown flush scheduler
        flushScheduler.shutdown();
        if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            flushScheduler.shutdownNow();
        }

        // Flush remaining contexts
        magenta.contextManager().flushAll();

        // Close terminal session (closes sessions, terminal)
        terminalSession.close();

        // Close database
        if (magenta.database() != null) {
            try {
                magenta.database().close();
            } catch (Exception e) {
                logger.error("Failed to close database: {}", e.getMessage());
            }
        }
    }

    private static Database initDatabase(Config config, Map<Arg, Arg.Value> args) {
        try {
            String dbPath;
            if (args.containsKey(Arg.DATABASE)) {
                dbPath = args.get(Arg.DATABASE).getString();
            } else {
                dbPath = config.baseStoragePath() + "/database.db";
            }
            return new Database(dbPath);
        } catch (Exception e) {
            logger.error("Failed to initialize database: {}", e.getMessage(), e);
            System.err.println("Failed to initialize database: " + e.getMessage());
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }
    }
}
