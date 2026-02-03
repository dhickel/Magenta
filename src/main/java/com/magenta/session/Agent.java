package com.magenta.session;

import com.magenta.config.Config.AgentConfig;
import com.magenta.io.terminal.CommandDetector;
import com.magenta.io.IOManager;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityManager;

/**
 * Agent holds configuration, model, and agent-specific security.
 * Immutable data class.
 */
public record Agent(
    AgentConfig config,
    ChatModel model,
    SecurityFilter securityFilter,
    CommandDetector commandDetector
) {
    public Agent(AgentConfig config) {
        this(
            config,
            config.model().asStreamingChatModel(),
            createSecurityFilter(config),
            CommandDetector.defaults()
        );
    }

    private static SecurityFilter createSecurityFilter(AgentConfig config) {
        // Create a temporary identity filter that will be replaced when IOManager is available
        // The actual filter needs IOManager for approval prompts, so we defer creation
        return SecurityFilter.identity();
    }

    /**
     * Create a SecurityFilter bound to a specific IOManager.
     * Call this when the agent is attached to a session with IOManager.
     */
    public SecurityFilter createSecurityFilterFor(IOManager io) {
        SecurityManager securityManager = SecurityManager.getInstance();
        securityManager.setConfig(config.security());
        return securityManager.createFilter(io);
    }
}