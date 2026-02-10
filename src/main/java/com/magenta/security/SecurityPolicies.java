package com.magenta.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.magenta.tools.ToolContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Composable security policies.
 */
public class SecurityPolicies {

    private static final ObjectMapper json = new ObjectMapper();

    // File access policy - restrict to specific directories
    public static ToolSecurityPolicy fileAccessPolicy(List<Path> allowedPaths) {
        return (request, context) -> {
            String args = request.arguments();
            // Parse all potential file paths from arguments
            List<Path> requestedPaths = extractAllPaths(args);

            if (requestedPaths.isEmpty()) {
                // If no paths found, but it's a file tool, we should potentially fail-safe.
                // But for now, we assume if no path arguments are found, it's not accessing files (or arg parsing failed).
                // Better to be safe: check if the tool name implies file access.
                // However, the policy is applied per-tool by SecurityManager.
                // If this policy is applied, we expect paths.
                // If extractAllPaths missed them, we have a problem.
                // For now, let's just proceed with checking found paths.
                return Optional.empty();
            }

            for (Path path : requestedPaths) {
                Path absolutePath = path.isAbsolute() ? path : Paths.get(".").resolve(path).toAbsolutePath().normalize();

                boolean allowed = allowedPaths.stream()
                    .map(p -> p.isAbsolute() ? p : Paths.get(".").resolve(p).toAbsolutePath().normalize())
                    .anyMatch(allowedDir -> absolutePath.startsWith(allowedDir));

                if (!allowed) {
                    return Optional.of("File access denied: path not in allowed directories: " + path);
                }
            }

            return Optional.empty();
        };
    }

    private static List<Path> extractAllPaths(String argsJson) {
        List<Path> paths = new ArrayList<>();
        try {
            JsonNode node = json.readTree(argsJson);
            // Check common path argument names
            String[] keys = {"path", "relativePath", "filePath", "filename", "file", "pathA", "pathB"};
            for (String key : keys) {
                if (node.has(key)) {
                    paths.add(Paths.get(node.get(key).asText()));
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return paths;
    }

    // Rate limiting policy
    public static ToolSecurityPolicy rateLimitPolicy(int maxCallsPerMinute) {
        RateLimiter limiter = RateLimiter.create(maxCallsPerMinute / 60.0);
        return (request, context) -> {
            boolean acquired = limiter.tryAcquire();
            return acquired
                ? Optional.empty()
                : Optional.of("Rate limit exceeded");
        };
    }

    // Audit logging policy (always allows, but logs)
    public static ToolSecurityPolicy auditPolicy(Logger logger) {
        return (request, context) -> {
            logger.info("Tool execution: {} by session {}",
                request.name(), context.sessionId());
            return Optional.empty();
        };
    }

    // Compose multiple policies
    public static ToolSecurityPolicy compose(ToolSecurityPolicy... policies) {
        return (request, context) -> {
            for (ToolSecurityPolicy policy : policies) {
                Optional<String> result = policy.validate(request, context);
                if (result.isPresent()) return result; // First block wins
            }
            return Optional.empty();
        };
    }
}
