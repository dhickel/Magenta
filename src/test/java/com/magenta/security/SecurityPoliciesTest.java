package com.magenta.security;

import com.magenta.tools.ToolContext;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPoliciesTest {

    @Test
    void testFileAccessPolicy() {
        // Prepare allowed paths
        Path allowed = Paths.get("/tmp/allowed").toAbsolutePath().normalize();
        ToolSecurityPolicy policy = SecurityPolicies.fileAccessPolicy(List.of(allowed));

        // Allowed request
        String allowedJson = "{\"path\": \"" + allowed.resolve("file.txt").toString().replace("\\", "\\\\") + "\"}";
        ToolExecutionRequest allowedReq = ToolExecutionRequest.builder()
            .id("1")
            .name("readFile")
            .arguments(allowedJson)
            .build();
        assertTrue(policy.validate(allowedReq, null).isEmpty());

        // Denied request
        Path denied = Paths.get("/tmp/denied").toAbsolutePath().normalize();
        String deniedJson = "{\"path\": \"" + denied.resolve("secret.txt").toString().replace("\\", "\\\\") + "\"}";
        ToolExecutionRequest deniedReq = ToolExecutionRequest.builder()
            .id("2")
            .name("readFile")
            .arguments(deniedJson)
            .build();
        assertTrue(policy.validate(deniedReq, null).isPresent());
    }

    @Test
    void testFileAccessPolicyWithDiff() {
        // Prepare allowed paths
        Path allowed = Paths.get("/tmp/allowed").toAbsolutePath().normalize();
        ToolSecurityPolicy policy = SecurityPolicies.fileAccessPolicy(List.of(allowed));

        Path safeFile = allowed.resolve("safe.txt");
        Path secretFile = Paths.get("/etc/passwd"); // Example sensitive file

        // Case 1: Both paths allowed
        String safeJson = String.format("{\"pathA\": \"%s\", \"pathB\": \"%s\"}",
            safeFile.toString().replace("\\", "\\\\"),
            safeFile.toString().replace("\\", "\\\\"));

        ToolExecutionRequest safeReq = ToolExecutionRequest.builder()
            .id("1")
            .name("diff")
            .arguments(safeJson)
            .build();
        assertTrue(policy.validate(safeReq, null).isEmpty());

        // Case 2: pathA denied
        String deniedAJson = String.format("{\"pathA\": \"%s\", \"pathB\": \"%s\"}",
            secretFile.toString().replace("\\", "\\\\"),
            safeFile.toString().replace("\\", "\\\\"));

        ToolExecutionRequest deniedAReq = ToolExecutionRequest.builder()
            .id("2")
            .name("diff")
            .arguments(deniedAJson)
            .build();
        assertTrue(policy.validate(deniedAReq, null).isPresent(), "Should block restricted pathA");

        // Case 3: pathB denied
        String deniedBJson = String.format("{\"pathA\": \"%s\", \"pathB\": \"%s\"}",
            safeFile.toString().replace("\\", "\\\\"),
            secretFile.toString().replace("\\", "\\\\"));

        ToolExecutionRequest deniedBReq = ToolExecutionRequest.builder()
            .id("3")
            .name("diff")
            .arguments(deniedBJson)
            .build();
        assertTrue(policy.validate(deniedBReq, null).isPresent(), "Should block restricted pathB");
    }

    @Test
    void testRateLimitPolicy() {
        // 1 call per minute -> 1/60 calls per second
        ToolSecurityPolicy policy = SecurityPolicies.rateLimitPolicy(1);
        ToolExecutionRequest req = ToolExecutionRequest.builder()
            .id("1")
            .name("tool")
            .arguments("{}")
            .build();

        // First call should succeed (stored permit)
        assertTrue(policy.validate(req, null).isEmpty());

        // Second call immediate should fail
        assertTrue(policy.validate(req, null).isPresent());
    }
}
