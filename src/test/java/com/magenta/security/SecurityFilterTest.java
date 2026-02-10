package com.magenta.security;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.IOManager;
import com.magenta.io.InternalIOManager;
import com.magenta.manager.SecurityManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SecurityFilter with Optional-based filtering.
 */
class SecurityFilterTest {

    private SecurityManager securityManager;
    private IOManager io;

    @BeforeEach
    void setUp() {
        securityManager = new SecurityManager();
        io = new InternalIOManager();
    }

    @Test
    void testIdentityFilterPassesThrough() {
        SecurityFilter filter = SecurityFilter.identity();

        Optional<String> blocked = filter.inputFilter().apply("test input", io);

        assertTrue(blocked.isEmpty(), "Identity filter should pass through");
    }

    @Test
    void testInputFilterBlocksPattern() {
        SecurityConfig config = new SecurityConfig(
            List.of(),
            List.of(),
            List.of("rm -rf")
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);

        Optional<String> blocked = filter.inputFilter().apply("rm -rf /home", io);

        assertTrue(blocked.isPresent(), "Should block rm -rf");
        assertTrue(blocked.get().contains("rm -rf"));
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

        Optional<String> blocked = filter.inputFilter().apply("ls -la", io);

        assertTrue(blocked.isEmpty(), "Should allow safe input");
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

        Optional<String> blocked = filter.outputFilter().apply("test output");

        assertTrue(blocked.isEmpty(), "Output filter should pass through");
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

        Optional<String> blocked = filter.toolFilter().apply(request, io);

        assertTrue(blocked.isPresent(), "Should block rm -rf");
        assertTrue(blocked.get().contains("rm -rf"));
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

        Optional<String> blocked = filter.toolFilter().apply(request, io);

        assertTrue(blocked.isEmpty(), "Whitelisted command should be approved");
    }

    @Test
    void testFilterChaining() {
        SecurityFilter filter1 = SecurityFilter.identity();
        SecurityFilter filter2 = SecurityFilter.identity();

        SecurityFilter chained = filter1.andThen(filter2);

        Optional<String> blocked = chained.inputFilter().apply("test", io);

        assertTrue(blocked.isEmpty(), "Chained identity filters should pass through");
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

        Optional<String> blocked = curriedFilter.apply("blocked text");

        assertTrue(blocked.isPresent(), "Should block text containing 'blocked'");
    }
}
