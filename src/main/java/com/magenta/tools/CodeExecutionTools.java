package com.magenta.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code execution and validation tools.
 * Supports running tests, building projects, and sandboxed code execution.
 */
public class CodeExecutionTools {

    private final Path projectRoot;
    private final ExecutorService executor;

    public CodeExecutionTools() {
        this.projectRoot = Paths.get(System.getProperty("user.dir"));
        this.executor = Executors.newCachedThreadPool();
    }

    @Tool("Run Maven build with optional goals (e.g., 'clean compile', 'test', 'package')")
    public String mavenBuild(String goals) {
        return executeBuildCommand("mvn " + goals, 300); // 5 min timeout
    }

    @Tool("Run a specific test class or method")
    public String runTest(String testClass, @P(value = "Optional test method name", required = false) String testMethod) {
        String command = "mvn test -Dtest=" + testClass;
        if (testMethod != null) {
            command += "#" + testMethod;
        }
        return executeBuildCommand(command, 120);
    }

    @Tool("Run all tests in the project")
    public String runAllTests() {
        return executeBuildCommand("mvn test", 300);
    }

    @Tool("Validate code compilation without running tests")
    public String validateCompilation() {
        return executeBuildCommand("mvn clean compile", 120);
    }

    @Tool("Run static code analysis (if configured)")
    public String analyzeCode() {
        // Check if spotbugs/checkstyle configured
        if (Files.exists(projectRoot.resolve("checkstyle.xml"))) {
            return executeBuildCommand("mvn checkstyle:check", 60);
        }
        return "No static analysis tools configured";
    }

    private String executeBuildCommand(String command, int timeoutSeconds) {
        try {
            List<String> commandParts = splitCommand(command);
            ProcessBuilder pb = new ProcessBuilder(commandParts);
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Capture output with timeout
            Future<String> outputFuture = executor.submit(() -> {
                StringBuilder output = new StringBuilder();
                try (var reader = process.inputReader()) {
                    reader.lines().forEach(line -> output.append(line).append("\n"));
                }
                return output.toString();
            });

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "Error: Command timed out after " + timeoutSeconds + " seconds";
            }

            String output = outputFuture.get(5, TimeUnit.SECONDS);
            int exitCode = process.exitValue();

            return String.format(
                "Exit Code: %d\n\n%s",
                exitCode,
                truncateOutput(output, 5000) // Limit output size
            );

        } catch (Exception e) {
            return "Error executing command: " + e.getMessage();
        }
    }

    private List<String> splitCommand(String command) {
        List<String> parts = new ArrayList<>();
        // Match:
        // 1. Quoted string: "..." or '...'
        // 2. Non-whitespace sequences that might contain quoted parts (e.g. -Dkey="value")
        // This is complex. Standard approach:
        // Use a simple state machine or a more robust regex.

        // Regex that matches:
        // - Sequences of non-whitespace characters, OR
        // - Quoted strings
        // But we want to keep them attached if they are. e.g. prefix="value" should be one token.

        // Improved regex:
        // Match a token which consists of:
        // (quoted part | non-whitespace-non-quote)+

        // Pattern: ([^"'\s]+|"[^"]*"|'[^']*')+
        Pattern pattern = Pattern.compile("([^\"'\\s]+|\"([^\"]*)\"|'([^']*)')+");
        Matcher matcher = pattern.matcher(command);

        while (matcher.find()) {
            String match = matcher.group();
            // Remove outer quotes if the entire token matches a quoted string?
            // Usually shell removes quotes.
            // e.g. "hello world" -> hello world
            // e.g. -Dmsg="hello world" -> -Dmsg=hello world

            // Simplified unquoting for the match:
            // This is still tricky with regex alone.
            // Let's iterate over the match and manually unquote parts.
            parts.add(unquote(match));
        }

        return parts;
    }

    private String unquote(String token) {
        StringBuilder sb = new StringBuilder();
        boolean inDoubleQuote = false;
        boolean inSingleQuote = false;

        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
            } else if (c == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String truncateOutput(String output, int maxChars) {
        if (output.length() <= maxChars) {
            return output;
        }
        return output.substring(0, maxChars) + "\n\n... (output truncated)";
    }
}
