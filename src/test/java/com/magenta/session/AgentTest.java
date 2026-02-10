package com.magenta.session;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentTest {
    @Test
    void testRegistryPopulated() {
        assertTrue(Agent.registry().get("shell").isPresent());
        assertTrue(Agent.registry().get("filesystem").isPresent());
        assertTrue(Agent.registry().get("git").isPresent());
        assertTrue(Agent.registry().get("todo").isPresent());
    }
}
