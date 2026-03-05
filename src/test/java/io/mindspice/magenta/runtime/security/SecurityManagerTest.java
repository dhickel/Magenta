package io.mindspice.magenta.runtime.security;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityManagerTest {

    @Test
    void yoloOverrideShortCircuitsToAllowed() {
        RuntimeConfig.SecurityPolicyConfig config = new RuntimeConfig.SecurityPolicyConfig(
                RuntimeConfig.SecurityMode.DENY_ALL,
                true,
                List.of("."),
                List.of(),
                List.of(),
                List.of(),
                new RuntimeConfig.WebAccessConfig(false, false),
                List.of()
        );
        SecurityManager manager = new SecurityManager(config, Path.of(".").toAbsolutePath().normalize(), null);

        SecurityManager.Decision decision = manager.authorize(request(UUID.randomUUID(), "read_file", "{\"path\":\"README.md\"}"), Set.of("read_file"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.OVERRIDE_ALLOWED);
    }

    @Test
    void deniesToolNotInAgentSettings() {
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null
        );

        SecurityManager.Decision decision = manager.authorize(request(UUID.randomUUID(), "shell_command", "{\"cmd\":\"ls\"}"), Set.of("read_file"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(decision.reason()).contains("agent settings");
    }

    @Test
    void promptModeWithoutCallbackDeniesDeterministically() {
        RuntimeConfig.SecurityPolicyConfig config = new RuntimeConfig.SecurityPolicyConfig(
                RuntimeConfig.SecurityMode.PROMPT,
                false,
                List.of("."),
                List.of(),
                List.of(),
                List.of(),
                new RuntimeConfig.WebAccessConfig(false, false),
                List.of()
        );
        SecurityManager manager = new SecurityManager(config, Path.of(".").toAbsolutePath().normalize(), null);

        SecurityManager.Decision decision = manager.authorize(request(UUID.randomUUID(), "read_file", "{\"path\":\"README.md\"}"), Set.of("read_file"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(decision.reason()).contains("approval callback");
    }

    @Test
    void setToolPolicyReplacesSessionPolicy() {
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        ToolRequest request = request(sessionId, "read_file", "{\"path\":\"README.md\"}");
        SecurityManager.Decision allowedBefore = manager.authorize(request, Set.of("read_file"));

        SecurityManager.ToolPolicy denyAllPolicy = new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.DENY_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        );
        manager.setToolPolicy(sessionId, denyAllPolicy);

        SecurityManager.Decision deniedAfter = manager.authorize(request, Set.of("read_file"));

        assertThat(allowedBefore.allowed()).isTrue();
        assertThat(deniedAfter.allowed()).isFalse();
        assertThat(deniedAfter.code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
    }

    @Test
    void deniesFilePathOutsideAllowedRoots() {
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        SecurityManager.ToolPolicy policy = new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("./configs"),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        );
        manager.setToolPolicy(sessionId, policy);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "read_file", "{\"path\":\"./pom.xml\"}"),
                Set.of("read_file")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("outside allowed roots");
    }

    private ToolRequest request(UUID sessionId, String toolName, String argsJson) {
        return new ToolRequest(
                sessionId.toString(),
                "agent-default",
                new ContextElement.ToolCall("call-1", toolName, argsJson)
        );
    }
}
