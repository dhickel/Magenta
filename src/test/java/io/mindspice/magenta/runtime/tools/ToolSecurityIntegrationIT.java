package io.mindspice.magenta.runtime.tools;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolSecurityIntegrationIT {

    @TempDir
    Path tempDir;

    @Test
    void allowedFileWriteExecutesAfterAuthorization() throws Exception {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "write_file", "{\"path\":\"allowed.txt\",\"content\":\"ok\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("write_file"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
        assertThat(Files.readString(tempDir.resolve("allowed.txt"))).isEqualTo("ok");
    }

    @Test
    void deniedFileWriteDoesNotExecuteSideEffects() {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("./configs"),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "write_file", "{\"path\":\"blocked.txt\",\"content\":\"nope\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("write_file"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.decision().code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(execution.result()).isNull();
        assertThat(Files.exists(tempDir.resolve("blocked.txt"))).isFalse();
    }

    @Test
    void deniedShellCommandDoesNotRunWhenCommandNotAllowed() {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of("echo")
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "shell_command", "{\"cmd\":\"ls -la\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("shell_command"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.decision().reason()).contains("allowedCommands");
        assertThat(execution.result()).isNull();
    }

    @Test
    void deniedWhenToolNotInAgentToolSetDoesNotExecute() {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "write_file", "{\"path\":\"agent-denied.txt\",\"content\":\"blocked\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("read_file"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.decision().reason()).contains("agent settings");
        assertThat(execution.result()).isNull();
        assertThat(Files.exists(tempDir.resolve("agent-denied.txt"))).isFalse();
    }

    @Test
    void allowedFileDeleteExecutesAfterAuthorization() throws Exception {
        Files.writeString(tempDir.resolve("remove.txt"), "delete me");
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "delete_file", "{\"path\":\"remove.txt\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("delete_file"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
        assertThat(Files.exists(tempDir.resolve("remove.txt"))).isFalse();
    }

    @Test
    void deniedFileDeleteDoesNotExecuteSideEffects() throws Exception {
        Files.writeString(tempDir.resolve("blocked-remove.txt"), "keep me");
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("./configs"),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "delete_file", "{\"path\":\"blocked-remove.txt\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("delete_file"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.decision().code()).isEqualTo(SecurityManager.DecisionCode.DENIED);
        assertThat(execution.result()).isNull();
        assertThat(Files.exists(tempDir.resolve("blocked-remove.txt"))).isTrue();
    }

    @Test
    void allowedSqliteExecWritesDatabaseInsideAllowedPath() throws Exception {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest execRequest = ToolTestSupport.request(
                sessionId,
                "sqlite_exec",
                "{\"dbPath\":\"allowed.db\",\"sql\":\"CREATE TABLE t(id INTEGER); INSERT INTO t(id) VALUES (1);\"}"
        );
        SecuredExecution exec = executeSecured(securityManager, toolManager, execRequest, Set.of("sqlite_exec", "sqlite_query"));

        assertThat(exec.decision().allowed()).isTrue();
        assertThat(exec.result()).isNotNull();
        assertThat(ToolTestSupport.payload(exec.result()).path("status").asText()).isEqualTo("ok");

        ToolRequest queryRequest = ToolTestSupport.request(
                sessionId,
                "sqlite_query",
                "{\"dbPath\":\"allowed.db\",\"sql\":\"SELECT COUNT(*) AS c FROM t\"}"
        );
        SecuredExecution query = executeSecured(securityManager, toolManager, queryRequest, Set.of("sqlite_exec", "sqlite_query"));

        assertThat(query.decision().allowed()).isTrue();
        assertThat(ToolTestSupport.payload(query.result()).path("data").path("rows").get(0).path("c").asInt()).isEqualTo(1);
    }

    private SecurityManager securityManager(Path workspaceRoot) {
        return new SecurityManager(RuntimeConfig.SecurityPolicyConfig.defaults(), workspaceRoot, null);
    }

    private SecurityManager.ToolPolicy toolPolicy(RuntimeConfig.SecurityMode mode, List<String> allowedPaths, Set<String> allowedCommands) {
        return new SecurityManager.ToolPolicy(
                mode,
                false,
                Set.of(),
                Set.of(),
                allowedPaths,
                allowedCommands,
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        );
    }

    private SecuredExecution executeSecured(
            SecurityManager securityManager,
            ToolManager toolManager,
            ToolRequest request,
            Set<String> agentToolIds
    ) {
        SecurityManager.Decision decision = securityManager.authorize(request, agentToolIds);
        if (!decision.allowed()) {
            return new SecuredExecution(decision, null);
        }
        return new SecuredExecution(decision, toolManager.execute(request));
    }

    private record SecuredExecution(SecurityManager.Decision decision, ToolResult result) {
    }
}
