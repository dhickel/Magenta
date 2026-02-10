package com.magenta.security;

import com.magenta.tools.ToolContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.Optional;

/**
 * Tool-specific security policy.
 */
public interface ToolSecurityPolicy {
    /**
     * Validate tool execution request.
     * @return Optional.empty() if allowed, Optional.of(reason) if blocked
     */
    Optional<String> validate(ToolExecutionRequest request, ToolContext context);
}
