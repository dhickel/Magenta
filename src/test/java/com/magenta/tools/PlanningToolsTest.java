package com.magenta.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanningToolsTest {

    private PlanningTools tools;

    @BeforeEach
    void setUp() {
        tools = new PlanningTools("test-session");
    }

    @Test
    void testPlanLifecycle() {
        String stepsJson = "[\"Step 1\", \"Step 2\"]";
        String result = tools.createPlan("Test Goal", stepsJson);
        assertTrue(result.contains("Plan created"));

        // Extract ID
        // Format: Plan created (ID: abc)\n...
        String idLine = result.lines().filter(l -> l.contains("ID:")).findFirst().orElse("");
        String id = idLine.substring(idLine.indexOf("ID: ") + 4, idLine.indexOf(")"));

        String status = tools.planStatus(id);
        assertTrue(status.contains("Progress: 0/2"));
        assertTrue(status.contains("[ ] 1. Step 1"));

        tools.completeStep(id, 1);
        status = tools.planStatus(id);
        assertTrue(status.contains("Progress: 1/2"));
        assertTrue(status.contains("[✓] 1. Step 1"));
        assertTrue(status.contains("Next: Step 2"));
    }

    @Test
    void testScratchpad() {
        tools.scratchpadWrite("key1", "value1");
        assertEquals("value1", tools.scratchpadRead("key1"));
        assertTrue(tools.scratchpadList().contains("key1"));
    }
}
