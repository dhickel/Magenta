package com.magenta.session;

import com.magenta.config.Arg;
import com.magenta.config.Config;
import com.magenta.config.Config.AgentConfig;
import com.magenta.config.ConfigManager;
import com.magenta.io.IOManager;
import com.magenta.io.TerminalIOManager;
import com.magenta.security.SecurityManager;
import com.magenta.tools.ToolProvider;

import java.util.Map;

/**
 * An agent session that implements the Session interface.
 * Coordinates I/O context with agent behavior.
 * Owns its IOManager and closes it on session close.
 */
public class AgentSession implements Session {
    // Session implementation fields
    private final IOManager ioManager;
    private final GlobalContext globalContext;
    private boolean exitFlag = false;

    // Agent-specific fields
    private final Agent agent;
    private final InteractionMode mode;

    public AgentSession(IOManager io, GlobalContext context, String agentName) {
        this(io, context, agentName, new InteractionMode.StreamingChat());
    }

    public AgentSession(IOManager io, GlobalContext context, String agentName, InteractionMode mode) {
        // Session setup
        this.ioManager = io;
        this.globalContext = context;

        // Agent setup
        AgentConfig agentConfig = context.config().agents.get(agentName);
        if (agentConfig == null) {
            throw new IllegalStateException("Agent not found: " + agentName);
        }
        this.agent = new Agent(agentConfig);
        this.mode = mode;
    }

    // Agent-specific methods
    public void run() {
        while (!shouldExit()) {
            mode.processIteration(this, agent);
        }
    }

    // Session interface implementation
    @Override
    public IOManager io() {
        return ioManager;
    }

    @Override
    public GlobalContext context() {
        return globalContext;
    }

    @Override
    public boolean shouldExit() {
        return exitFlag;
    }

    @Override
    public void setExit(boolean exit) {
        this.exitFlag = exit;
    }

    // AutoCloseable implementation
    @Override
    public void close() throws Exception {
        ioManager.close();
    }


    public Agent agent() {
        return agent;
    }

    public InteractionMode mode() {
        return mode;
    }



    // Builder pattern for encapsulated construction
    public static Builder builder() {
        return new Builder();
    }

    public static AgentSession fromConfig(String[] args) {
        Map<Arg, Arg.Value> arguments = Arg.parseAll(args);
        String configPath = arguments.get(Arg.CONFIG).getString();
        Config config = ConfigManager.loadOrFail(configPath);

        return builder()
                .config(config)
                .arguments(arguments)
                .agent(config.global().baseAgent())
                .build();
    }

    public static class Builder {
        private Config config;
        private Map<Arg, Arg.Value> arguments;
        private String agentName;
        private InteractionMode mode = new InteractionMode.StreamingChat(); // Default
        private IOManager ioManager; // Optional override

        public Builder config(Config config) {
            this.config = config;
            return this;
        }

        public Builder arguments(Map<Arg, Arg.Value> arguments) {
            this.arguments = arguments;
            return this;
        }

        public Builder agent(String agentName) {
            this.agentName = agentName;
            return this;
        }

        public Builder mode(InteractionMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder ioManager(IOManager ioManager) {
            this.ioManager = ioManager;
            return this;
        }

        public AgentSession build() {
            validate();

            // Get agent config
            AgentConfig agentConfig = config.agents.get(agentName);
            if (agentConfig == null) {
                throw new IllegalStateException("Agent not found: " + agentName);
            }

            // Create IOManager if not provided
            if (ioManager == null) {
                try {
                    TerminalIOManager terminalIO = new TerminalIOManager();

                    // Configure colors if available
                    if (config.colors != null) {
                        terminalIO.setColorsConfig(config.colors);
                    }

                    ioManager = terminalIO;
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create IOManager", e);
                }
            }

            // Create SecurityManager from agent's security config
            SecurityManager securityManager = new SecurityManager(agentConfig.security());

            // Set security filter on IOManager
            ioManager.setSecurityFilter(securityManager.createFilter(ioManager));

            // Create ToolProvider
            ToolProvider toolProvider = new ToolProvider(null, null, securityManager);

            // Create GlobalContext
            GlobalContext context = new GlobalContext(config, arguments, securityManager, toolProvider);

            // Create session
            return new AgentSession(ioManager, context, agentName, mode);
        }

        private void validate() {
            if (config == null) {
                throw new IllegalStateException("Config is required");
            }
            if (agentName == null) {
                throw new IllegalStateException("Agent name is required");
            }
        }
    }
}
