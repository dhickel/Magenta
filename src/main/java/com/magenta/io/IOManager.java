package com.magenta.io;

import com.magenta.security.SecurityFilter;

/**
 * Combined I/O interface for input and output operations with integrated security.
 * Implementations handle different I/O contexts (terminal, agent-to-agent, etc.).
 */
public interface IOManager extends InputPipe, OutputPipe, AutoCloseable {

    /**
     * Get the security filter for this IOManager.
     */
    SecurityFilter securityFilter();

    /**
     * Set the security filter for this IOManager.
     */
    void setSecurityFilter(SecurityFilter filter);

    /**
     * Create a ResponseHandler for streaming output.
     * @param agentColor Optional color code for agent output
     * @param delayMs Character delay in milliseconds (0 for immediate)
     * @return ResponseHandler configured for this IOManager context
     */
    ResponseHandler createResponseHandler(Integer agentColor, int delayMs);

    @Override
    default void close() throws Exception {
        // Default no-op, implementations can override
    }
}
