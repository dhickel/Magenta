package com.magenta;

import com.magenta.config.Config;
import com.magenta.config.ConfigManager;
import com.magenta.context.ContextLimits;
import com.magenta.io.InternalIOManager;
import com.magenta.manager.AgentNetwork;
import com.magenta.manager.ContextManager;
import com.magenta.manager.SecurityManager;
import com.magenta.session.Agent;
import com.magenta.session.SessionAlias;
import com.magenta.session.SessionId;
import com.magenta.tools.ToolContext;
import com.magenta.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class IntegrationTest {

    private Magenta magenta;
    private Config config;
    private InternalIOManager io;

    @BeforeEach
    void setUp() {
        // Setup minimal valid config
        config = new Config();
        config.endpoints = Collections.emptyMap();
        config.securities = Collections.emptyMap();
        config.models = Collections.emptyMap();
        config.agents = Collections.emptyMap();
        config.taskTemplates = Collections.emptyMap();
        config.delegationTemplates = Collections.emptyMap();

        // Setup services
        magenta = new Magenta(
            config,
            null, // No DB
            mock(ContextManager.class),
            mock(AgentNetwork.class),
            new SecurityManager()
        );

        io = new InternalIOManager();
    }

    @Test
    void testToolInstantiationChain() {
        // Create context
        SessionId sessionId = SessionId.random();
        SessionAlias alias = new SessionAlias("test-agent");
        ContextLimits limits = new ContextLimits(1000, 500);
        ToolContext context = new ToolContext(io, sessionId, limits, alias, magenta);

        // Use registry from Agent class
        ToolRegistry registry = Agent.registry();

        // Instantiate core tools
        List<String> toolsToLoad = List.of(
            "filesystem", "planning", "code-execution", "search", "agent"
        );

        List<Object> instantiated = registry.instantiateTools(toolsToLoad, context);

        assertEquals(5, instantiated.size());

        // Verify types (indirectly via class name to avoid imports if packages change)
        assertTrue(instantiated.stream().anyMatch(t -> t.getClass().getSimpleName().equals("FileSystemTools")));
        assertTrue(instantiated.stream().anyMatch(t -> t.getClass().getSimpleName().equals("PlanningTools")));
        assertTrue(instantiated.stream().anyMatch(t -> t.getClass().getSimpleName().equals("CodeExecutionTools")));
        assertTrue(instantiated.stream().anyMatch(t -> t.getClass().getSimpleName().equals("SearchTools")));
        assertTrue(instantiated.stream().anyMatch(t -> t.getClass().getSimpleName().equals("AgentTools")));
    }
}
