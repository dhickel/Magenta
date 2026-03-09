package io.mindspice.magenta.runtime.security;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityManagerTest {

    @ParameterizedTest
    @ValueSource(strings = {"read_file", "list_directory", "file_metadata", "write_file", "delete_file", "grep_files", "search_replace"})
    void deniesAllFileToolsWhenPathOutsideAllowedRoots(String toolName) {
        SecurityManager manager = manager(null);
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
                request(sessionId, toolName, "{\"path\":\"./pom.xml\"}"),
                Set.of(toolName)
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(decision.reason()).contains("approved roots");
    }

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
        SecurityManager manager = new SecurityManager(
                config,
                Path.of(".").toAbsolutePath().normalize(),
                null,
                descriptors()
        );

        SecurityManager.Decision decision = manager.authorize(
                request(UUID.randomUUID(), "read_file", "{\"path\":\"README.md\"}"),
                Set.of("read_file")
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.OVERRIDE_ALLOWED);
    }

    @Test
    void deniesToolNotInAgentSettings() {
        SecurityManager manager = manager(null);

        SecurityManager.Decision decision = manager.authorize(
                request(UUID.randomUUID(), "shell_command", "{\"cmd\":\"ls\"}"),
                Set.of("read_file")
        );

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
        SecurityManager manager = new SecurityManager(
                config,
                Path.of(".").toAbsolutePath().normalize(),
                null,
                descriptors()
        );

        SecurityManager.Decision decision = manager.authorize(
                request(UUID.randomUUID(), "read_file", "{\"path\":\"README.md\"}"),
                Set.of("read_file")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(decision.reason()).contains("approval callback");
    }

    @Test
    void setToolPolicyReplacesSessionPolicy() {
        SecurityManager manager = manager(null);
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
    void shellCommandMustMatchAllowedCommandsWhenConfigured() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        SecurityManager.ToolPolicy policy = new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of("echo"),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        );
        manager.setToolPolicy(sessionId, policy);

        SecurityManager.Decision denied = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"ls -la\"}"),
                Set.of("shell_command")
        );
        SecurityManager.Decision allowed = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"echo hello\"}"),
                Set.of("shell_command")
        );

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.reason()).contains("allowedCommands");
        assertThat(allowed.allowed()).isTrue();
    }

    @Test
    void shellCommandAllowsQuotedArgumentsForRulePrefixMatch() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        SecurityManager.ToolPolicy policy = new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of(new SecurityManager.CommandRule("allow-git-commit", RuntimeConfig.SecurityRuleAction.ALLOW, List.of("git", "commit"), ""))
        );
        manager.setToolPolicy(sessionId, policy);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"git commit -m \\\"hello\\\"\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void shellCommandRejectsChainedOperators() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"echo ok; ls\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.VALIDATION_ERROR);
        assertThat(decision.reason()).contains(";");
    }

    @Test
    void shellCommandUnmatchedRulesDoNotImplicitlyPrompt() {
        SecurityManager manager = manager(ignored -> SecurityManager.ApprovalResponse.DENY);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of(new SecurityManager.CommandRule("allow-rg", RuntimeConfig.SecurityRuleAction.ALLOW, List.of("rg"), ""))
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"git status\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.ALLOWED);
    }

    @Test
    void shellCommandCatchAllPromptRuleCanBeApprovedByCallback() {
        AtomicInteger callbackCalls = new AtomicInteger();
        SecurityManager manager = manager(ignored -> {
            callbackCalls.incrementAndGet();
            return SecurityManager.ApprovalResponse.APPROVE;
        });
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of(new SecurityManager.CommandRule("prompt-any", RuntimeConfig.SecurityRuleAction.PROMPT, List.of(), ""))
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"git status\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.ALLOWED);
        assertThat(callbackCalls.get()).isEqualTo(1);
    }

    @Test
    void shellCommandRejectsUnterminatedQuote() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"echo \\\"oops\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.VALIDATION_ERROR);
        assertThat(decision.reason()).contains("unterminated quote");
    }

    @Test
    void shellCommandRejectsTrailingEscape() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "shell_command", "{\"cmd\":\"echo hello\\\\\"}"),
                Set.of("shell_command")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.VALIDATION_ERROR);
        assertThat(decision.reason()).contains("trailing escape");
    }

    @Test
    void customValidatorCanDenyToolRequest() {
        ToolSecurityDescriptor descriptor = new ToolSecurityDescriptor(
                List.of(),
                false,
                "",
                List.of(),
                false,
                List.of(),
                false,
                context -> new SecurityManager.Decision(
                        SecurityManager.DecisionCode.DENIED,
                        false,
                        "Denied by custom validator",
                        RuntimeConfig.SecurityMode.APPROVE_ALL
                )
        );
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("custom_tool", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "custom_tool", "{}"),
                Set.of("custom_tool")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("custom validator");
    }

    @Test
    void customValidatorExceptionFailsClosed() {
        ToolSecurityDescriptor descriptor = new ToolSecurityDescriptor(
                List.of(),
                false,
                "",
                List.of(),
                false,
                List.of(),
                false,
                context -> {
                    throw new IllegalStateException("boom");
                }
        );
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("custom_tool", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "custom_tool", "{}"),
                Set.of("custom_tool")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.VALIDATION_ERROR);
        assertThat(decision.reason()).contains("validator callback failed");
    }

    @Test
    void requiredPathDescriptorFailsClosedWhenPathMissing() {
        ToolSecurityDescriptor descriptor = ToolSecurityDescriptor.path(List.of("destination"), true);
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("copy_file", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "copy_file", "{\"path\":\"README.md\"}"),
                Set.of("copy_file")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.code()).isEqualTo(SecurityManager.DecisionCode.VALIDATION_ERROR);
    }

    @Test
    void renamedPathKeyStillReceivesPolicyValidation() {
        ToolSecurityDescriptor descriptor = ToolSecurityDescriptor.path(List.of("destination"), true);
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("copy_file", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("./configs"),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "copy_file", "{\"destination\":\"./pom.xml\"}"),
                Set.of("copy_file")
        );

        assertThat(decision.allowed()).isFalse();
    }

    @Test
    void defaultPathWhenMissingSatisfiesRequiredPathValidation() {
        ToolSecurityDescriptor descriptor = ToolSecurityDescriptor.path(List.of("rootPath", "path"), true, ".");
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("grep_files", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "grep_files", "{\"pattern\":\"fractal\"}"),
                Set.of("grep_files")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void defaultPathWhenMissingStillEnforcesAllowedPaths() {
        ToolSecurityDescriptor descriptor = ToolSecurityDescriptor.path(List.of("rootPath", "path"), true, ".");
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("grep_files", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("./configs"),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "grep_files", "{\"pattern\":\"fractal\"}"),
                Set.of("grep_files")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("approved roots");
    }

    @Test
    void todoToolsNoLongerUsePathChecksWhenDescriptorHasNoPathRequirements() {
        ToolSecurityDescriptor descriptor = ToolSecurityDescriptor.path(List.of(), false);
        SecurityManager manager = new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                null,
                Map.of("todo_create", descriptor)
        );
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                false,
                Set.of(),
                Set.of(),
                List.of("./configs"),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "todo_create", "{\"title\":\"x\"}"),
                Set.of("todo_create")
        );

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void todoAliasInDeniedToolsBlocksAllTodoToolIds() {
        SecurityManager manager = manager(null);
        UUID sessionId = UUID.randomUUID();
        manager.initializePolicy(sessionId);
        manager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.BLACKLIST,
                false,
                Set.of(),
                Set.of("todo"),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        ));

        SecurityManager.Decision decision = manager.authorize(
                request(sessionId, "todo_create", "{\"title\":\"x\"}"),
                Set.of("todo_create")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("blacklisted");
    }

    private SecurityManager manager(SecurityManager.ApprovalCallback callback) {
        return new SecurityManager(
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                Path.of(".").toAbsolutePath().normalize(),
                callback,
                descriptors()
        );
    }

    private ToolRequest request(UUID sessionId, String toolName, String argsJson) {
        return new ToolRequest(
                sessionId.toString(),
                "agent-default",
                new ContextElement.ToolCall("call-1", toolName, argsJson)
        );
    }

    private Map<String, ToolSecurityDescriptor> descriptors() {
        ToolSecurityDescriptor pathDescriptor = ToolSecurityDescriptor.path(List.of("path", "filePath", "targetPath"), true);
        ToolSecurityDescriptor defaultDirectoryPathDescriptor = ToolSecurityDescriptor.path(
                List.of("path", "filePath", "targetPath", "rootPath"),
                true,
                "."
        );
        ToolSecurityDescriptor sqliteDescriptor = ToolSecurityDescriptor.path(List.of("dbPath", "path"), true);
        ToolSecurityDescriptor todoDescriptor = ToolSecurityDescriptor.path(List.of(), false);
        ToolSecurityDescriptor commandDescriptor = ToolSecurityDescriptor.command(List.of("cmd", "command"), true);
        return Map.ofEntries(
                Map.entry("read_file", pathDescriptor),
                Map.entry("list_directory", defaultDirectoryPathDescriptor),
                Map.entry("file_metadata", pathDescriptor),
                Map.entry("write_file", pathDescriptor),
                Map.entry("delete_file", pathDescriptor),
                Map.entry("grep_files", ToolSecurityDescriptor.path(List.of("rootPath", "path"), true, ".")),
                Map.entry("search_replace", pathDescriptor),
                Map.entry("shell_command", commandDescriptor),
                Map.entry("sqlite_query", sqliteDescriptor),
                Map.entry("sqlite_exec", sqliteDescriptor),
                Map.entry("todo_create", todoDescriptor),
                Map.entry("todo_list", todoDescriptor),
                Map.entry("todo_update", todoDescriptor),
                Map.entry("todo_delete", todoDescriptor)
        );
    }
}
