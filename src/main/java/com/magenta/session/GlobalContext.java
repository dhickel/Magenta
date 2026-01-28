package com.magenta.session;

import com.magenta.config.Arg;
import com.magenta.config.Config;
import com.magenta.security.SecurityManager;
import com.magenta.tools.ToolProvider;

import java.util.Map;

/**
 * Holds application-wide state shared across all agents.
 * Separates global configuration from per-session state.
 */
public class GlobalContext {
    private final Config config;
    private final Map<Arg, Arg.Value> args;
    private final SecurityManager securityManager;
    private final ToolProvider toolProvider;

    public GlobalContext(Config config, Map<Arg, Arg.Value> args,
                        SecurityManager securityManager, ToolProvider toolProvider) {
        this.config = config;
        this.args = args;
        this.securityManager = securityManager;
        this.toolProvider = toolProvider;
    }

    public Config config() {
        return config;
    }

    public Map<Arg, Arg.Value> args() {
        return args;
    }

    public SecurityManager securityManager() {
        return securityManager;
    }

    public ToolProvider toolProvider() {
        return toolProvider;
    }
}
