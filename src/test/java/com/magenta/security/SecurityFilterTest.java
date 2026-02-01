package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;
import com.magenta.io.InternalIOManager;
import com.magenta.io.Message;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecurityFilter with Message ADT integration.
 */
class SecurityFilterTest {

    private SecurityManager securityManager;
    private IOManager io;

    @BeforeEach
    void setUp() {
        securityManager = SecurityManager.getInstance();
        io = new InternalIOManager();
    }

    @Test
    void testIdentityFilterPassesThrough() {
        SecurityFilter filter = SecurityFilter.identity();

        Message.Input input = Message.input("test input");
        Message result = filter.inputFilter().apply(input, io);

        assertTrue(result instanceof Message.Input);
        assertEquals("test input", result.content());
    }

    @Test
    void testInputFilterBlocksPattern() {
        // SecurityConfig(approvalRequiredFor, alwaysAllowCommands, blockedCommands)
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of("rm -rf")
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        Message.Input input = Message.input("rm -rf /home");

        Message result = filter.inputFilter().apply(input, io);

        assertTrue(result.isFiltered());
        assertEquals(Message.FilterType.INPUT, result.filterType());
        assertTrue(result.filterReason().contains("rm -rf"));
    }

    @Test
    void testInputFilterAllowsSafeInput() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of("rm -rf")
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        Message.Input input = Message.input("ls -la");

        Message result = filter.inputFilter().apply(input, io);

        assertTrue(result instanceof Message.Input);
        assertEquals("ls -la", result.content());
        assertFalse(result.isFiltered());
    }

    @Test
    void testOutputFilterPassesThrough() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of()
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        Message.Output output = Message.output("test output");

        Message result = filter.outputFilter().apply(output);

        assertTrue(result instanceof Message.Output);
        assertEquals("test output", result.content());
    }

    @Test
    void testToolFilterBlocksPattern() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of("rm -rf")
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("shell")
            .arguments("rm -rf /tmp")
            .build();

        Message result = filter.toolFilter().apply(request, io);

        assertTrue(result.isFiltered());
        assertEquals(Message.FilterType.TOOL, result.filterType());
        assertTrue(result.filterReason().contains("rm -rf"));
    }

    @Test
    void testToolFilterWhitelistAutoApproves() {
        SecurityConfig config = new SecurityConfig(
            List.of("shell"),
            List.of("ls", "pwd"),
            List.of()
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name("shell")
            .arguments("ls -la")
            .build();

        Message result = filter.toolFilter().apply(request, io);

        assertFalse(result.isFiltered());
        assertTrue(result instanceof Message.System);
        assertEquals("approved", result.content());
    }

    @Test
    void testFilterChaining() {
        SecurityFilter filter1 = SecurityFilter.identity();
        SecurityFilter filter2 = SecurityFilter.identity();

        SecurityFilter chained = filter1.andThen(filter2);

        Message.Input input = Message.input("test");
        Message result = chained.inputFilter().apply(input, io);

        assertTrue(result instanceof Message.Input);
        assertEquals("test", result.content());
    }

    @Test
    void testCurriedInputFilter() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of("blocked")
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        var curriedFilter = filter.curriedInputFilter(io);

        Message result = curriedFilter.apply(Message.input("blocked text"));

        assertTrue(result.isFiltered());
    }

    @Test
    void testCurriedOutputFilter() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of()
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        var curriedFilter = filter.curriedOutputFilter();

        Message result = curriedFilter.apply(Message.output("safe output"));

        assertTrue(result instanceof Message.Output);
        assertEquals("safe output", result.content());
    }
}
