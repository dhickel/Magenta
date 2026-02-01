package com.magenta.experiment;

import com.magenta.config.Config.SecurityConfig;
import com.magenta.io.InternalIOManager;
import com.magenta.io.IOManager;
import com.magenta.io.Message;
import com.magenta.security.SecurityFilter;
import com.magenta.security.SecurityManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

import java.util.List;

/**
 * Demonstrates the Message ADT security filtering in action.
 * Run this to see how filtering works at different layers.
 */
public class SecurityFilterDemo {

    public static void main(String[] args) {
        System.out.println("=== Message ADT Security Filtering Demo ===\n");

        // Setup
        SecurityManager securityManager = SecurityManager.getInstance();
        IOManager io = new InternalIOManager();

        // Configure security policy
        SecurityConfig config = new SecurityConfig(
            List.of("shell"),           // Approval required for shell
            List.of("ls", "pwd"),       // Auto-allow ls and pwd
            List.of("rm -rf", "sudo")   // Block rm -rf and sudo
        );
        securityManager.setConfig(config);

        SecurityFilter filter = securityManager.createFilter(io);
        io.setSecurityFilter(filter);

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

        // Demo 4: Message ADT features
        System.out.println("--- Demo 4: Message ADT Features ---");
        testMessageADT();
        System.out.println();

        // Demo 5: Filter composition
        System.out.println("--- Demo 5: Filter Composition ---");
        testFilterComposition(io);
        System.out.println();

        System.out.println("=== Demo Complete ===");
    }

    private static void testInputFilter(SecurityFilter filter, IOManager io, String input) {
        Message.Input inputMsg = Message.input(input);
        Message result = filter.inputFilter().apply(inputMsg, io);

        System.out.printf("Input: \"%s\"\n", input);
        if (result.isFiltered()) {
            System.out.printf("  ❌ FILTERED: %s\n", result.filterReason());
        } else {
            System.out.printf("  ✓ ALLOWED\n");
        }
    }

    private static void testOutputFilter(SecurityFilter filter, String output) {
        Message.Output outputMsg = Message.output(output);
        Message result = filter.outputFilter().apply(outputMsg);

        System.out.printf("Output: \"%s\"\n", output);
        if (result.isFiltered()) {
            System.out.printf("  ❌ FILTERED: %s\n", result.filterReason());
        } else {
            System.out.printf("  ✓ ALLOWED\n");
        }
    }

    private static void testToolFilter(SecurityFilter filter, IOManager io, String toolName, String arguments) {
        ToolExecutionRequest request = ToolExecutionRequest.builder()
            .name(toolName)
            .arguments(arguments)
            .build();

        Message result = filter.toolFilter().apply(request, io);

        System.out.printf("Tool: %s, Args: \"%s\"\n", toolName, arguments);
        if (result.isFiltered()) {
            System.out.printf("  ❌ FILTERED: %s\n", result.filterReason());
        } else if (result instanceof Message.System sys && "approved".equals(sys.content())) {
            System.out.printf("  ✓ APPROVED\n");
        } else {
            System.out.printf("  ? UNKNOWN: %s\n", result);
        }
    }

    private static void testMessageADT() {
        // Create different message types
        Message.Input input = Message.input("user input");
        Message.Output output = Message.output("agent response", 5);
        Message.System system = Message.system("system notification");
        Message.Filtered filtered = Message.blocked("bad input", "Security violation", Message.FilterType.INPUT);

        System.out.println("Message Types:");
        System.out.printf("  Input: content=\"%s\", timestamp=%s\n", input.content(), input.timestamp());
        System.out.printf("  Output: content=\"%s\", color=%d\n", output.content(), output.colorCode());
        System.out.printf("  System: content=\"%s\", style=%s\n", system.content(), system.style());
        System.out.printf("  Filtered: content=\"%s\", reason=\"%s\", type=%s\n",
            filtered.content(), filtered.filterReason(), filtered.filterType());

        // Test convenience methods
        System.out.println("\nConvenience Methods:");
        System.out.printf("  input.isFiltered() = %s\n", input.isFiltered());
        System.out.printf("  filtered.isFiltered() = %s\n", filtered.isFiltered());
        System.out.printf("  Message.of(\"text\") = %s\n", Message.of("text"));
    }

    private static void testFilterComposition(IOManager io) {
        SecurityFilter filter1 = SecurityFilter.identity();
        SecurityFilter filter2 = SecurityFilter.identity();

        SecurityFilter composed = filter1.andThen(filter2);

        Message.Input input = Message.input("test");
        Message result = composed.inputFilter().apply(input, io);

        System.out.println("Filter Composition:");
        System.out.printf("  identity.andThen(identity) = %s\n", result);
        System.out.printf("  Result type: %s\n", result.getClass().getSimpleName());

        // Test currying
        var curriedInputFilter = composed.curriedInputFilter(io);
        Message curriedResult = curriedInputFilter.apply(Message.input("curried test"));
        System.out.printf("  Curried filter result: %s\n", curriedResult);
    }
}
