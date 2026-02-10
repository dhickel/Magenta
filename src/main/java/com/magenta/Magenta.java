package com.magenta;

import com.magenta.manager.AgentNetwork;
import com.magenta.config.Config;
import com.magenta.manager.ContextManager;
import com.magenta.persistence.Database;
import com.magenta.manager.SecurityManager;

/**
 * Central services container for the Magenta framework.
 * Framework users instantiate this directly with their own wired services.
 * MagentaApp creates one for the terminal application.
 *
 * @param config Application configuration
 * @param database Database for persistence (nullable for no-persistence mode)
 * @param contextManager Context lifecycle manager
 * @param agentNetwork Inter-agent message routing
 * @param securityManager Security filter factory
 */
public record Magenta(
    Config config,
    Database database,
    ContextManager contextManager,
    AgentNetwork agentNetwork,
    SecurityManager securityManager
) {}
