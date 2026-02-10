package com.magenta.tools;

import com.magenta.agent.AgentMessage;
import com.magenta.config.Config;
import com.magenta.config.Config.DelegationTemplate;
import com.magenta.manager.AgentNetwork;
import com.magenta.task.TaskWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentToolsTest {

    private AgentTools tools;
    private AgentNetwork network;
    private Config config;

    @BeforeEach
    void setUp() {
        network = mock(AgentNetwork.class);
        config = new Config();
        config.delegationTemplates = Map.of(
            "code-review", new DelegationTemplate(
                "reviewer",
                "Code Review",
                "Review {file}",
                "report"
            )
        );
        tools = new AgentTools("me", network, config);
    }

    @Test
    void testDelegateToAgent() {
        String result = tools.delegateToAgent("code-review", "{\"file\": \"Main.java\"}");

        assertTrue(result.contains("Task delegated to reviewer"));

        verify(network).delegateTask(
            eq("me"),
            eq("reviewer"),
            argThat(task -> task.getResolvedTaskPrompt().contains("Review Main.java"))
        );
    }

    @Test
    void testDelegateToAgentUnknownType() {
        String result = tools.delegateToAgent("unknown", "{}");
        assertTrue(result.contains("Error: Unknown task type"));
    }
}
