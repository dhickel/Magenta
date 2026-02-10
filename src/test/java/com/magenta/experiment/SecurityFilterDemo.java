package com.magenta.experiment;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.InternalIOManager;
import com.magenta.io.IOManager;
import com.magenta.security.SecurityFilter;
import com.magenta.manager.SecurityManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;
import java.util.Optional;

/**
 * Demonstrates the simplified security filtering with Optional-based results.
 * Run this to see how filtering works at different layers.
 */
public class SecurityFilterDemo {

    public static void main(String[] args) {
        System.out.println("=== Security Filtering Demo ===\n");

        // Setup
        SecurityManager securityManager = new SecurityManager();
        IOManager io = new InternalIOManager();

        // Configure security policy
        SecurityConfig config = new SecurityConfig(
            List.of("shell"),           // Approval required for shell
            List.of("ls", "pwd"),       // Auto-allow ls and pwd
            List.of("rm -rf", "sudo")   // Block rm -rf and sudo
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);

        // Demo 1: Input filtering
        System.out.println("--- Demo 1: Input Filtering ---");
        testInputFilter(filter, io, "ls -la");              // Should pass
        testInputFilter(filter, io, "rm -rf /tmp");         // Should block
        testInputFilter(filter, io, "sudo apt update");     // Should block
        System.out.println();

        // Demo 2: Output filtering
        System.out.println("--- Demo 2: Output Filtering ---");
        testOutputFilter(filter, "Hello, world!");          // Should pass
        testOutputFilter(filter, "Normal output");          // Should pass
        System.out.println();

        // Demo 3: Tool filtering
        System.out.println("--- Demo 3: Tool Filtering ---");
        testToolFilter(filter, io, "shell", "ls -la");          // Whitelisted - auto approve
        testToolFilter(filter, io, "shell", "pwd");             // Whitelisted - auto approve
        testToolFilter(filter, io, "shell", "rm -rf /home");    // Blocked
        testToolFilter(filter, io, "shell", "echo hello");      // Requires approval (but no user)
        System.out.println();

        // Demo 4: Filter composition
        System.out.println("--- Demo 4: Filter Composition ---");
        testFilterComposition(io);
        System.out.println();

        System.out.println("=== Demo Complete ===");
    }

    private static void testInputFilter(SecurityFilter filter, IOManager io, String input) {
        Optional<String> blocked = filter.inputFilter().apply(input, io);

        System.out.printf("Input: \"%s\"\n", input);
        if (blocked.isPresent()) {
            System.out.printf("  X FILTERED: %s\n", blocked.get());
        } else {
            System.out.printf("  OK ALLOWED\n");
        }
    }

    private static void testOutputFilter(SecurityFilter filter, String output) {
        Optional<String> blocked = filter.outputFilter().apply(output);

        System.out.printf("Output: \"%s\"\n", output);
        if (blocked.isPresent()) {
            System.out.printf("  X FILTERED: %s\n", blocked.get());
        } else {
            System.out.printf("  OK ALLOWED\n");
        }
    }

    private static void testToolFilter(SecurityFilter filter, IOManager io, String toolName, String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name(toolName)
            .arguments(arguments)
            .build();

        Optional<String> blocked = filter.toolFilter().apply(request, io);

        System.out.printf("Tool: %s, Args: \"%s\"\n", toolName, arguments);
        if (blocked.isPresent()) {
            System.out.printf("  X FILTERED: %s\n", blocked.get());
        } else {
            System.out.printf("  OK APPROVED\n");
        }
    }

    private static void testFilterComposition(IOManager io) {
        SecurityFilter filter1 = SecurityFilter.identity();
        SecurityFilter filter2 = SecurityFilter.identity();

        SecurityFilter composed = filter1.andThen(filter2);

        Optional<String> blocked = composed.inputFilter().apply("test", io);

        System.out.println("Filter Composition:");
        System.out.printf("  identity.andThen(identity) = %s\n", blocked.isEmpty() ? "PASS" : "BLOCKED");

        // Test currying
        var curriedInputFilter = composed.curriedInputFilter(io);
        Optional<String> curriedResult = curriedInputFilter.apply("curried test");
        System.out.printf("  Curried filter result: %s\n", curriedResult.isEmpty() ? "PASS" : "BLOCKED");
    }
}
