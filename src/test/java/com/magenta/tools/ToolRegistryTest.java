package com.magenta.tools;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void testRegistrationAndRetrieval() {
        ToolRegistry registry = new ToolRegistry();
        ToolProvider provider = ctx -> "ToolInstance";

        registry.register("test", provider);

        Optional<ToolProvider> retrieved = registry.get("test");
        assertTrue(retrieved.isPresent());
        assertEquals(provider, retrieved.get());

        assertTrue(registry.get("unknown").isEmpty());
    }

    @Test
    void testInstantiation() {
        ToolRegistry registry = new ToolRegistry();
        registry.register("tool1", ctx -> "Instance1");
        registry.register("tool2", ctx -> "Instance2");

        // Context can be null for this test as provider doesn't use it
        List<Object> tools = registry.instantiateTools(List.of("tool1", "tool2", "unknown"), null);

        assertEquals(2, tools.size());
        assertTrue(tools.contains("Instance1"));
        assertTrue(tools.contains("Instance2"));
    }
}
