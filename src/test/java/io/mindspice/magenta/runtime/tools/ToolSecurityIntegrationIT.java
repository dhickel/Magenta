package io.mindspice.magenta.runtime.tools;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.security.SecurityManager;
import io.mindspice.magenta.runtime.security.ToolSecurityDescriptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
    void allowedShellCommandExecutesAfterAuthorization() throws Exception {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "shell_command", "{\"cmd\":\"echo secure-shell\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("shell_command"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
        assertThat(ToolTestSupport.payload(execution.result()).path("data").path("stdout").asText()).contains("secure-shell");
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
    void allowedListDirectoryExecutesAfterAuthorization() throws Exception {
        Files.writeString(tempDir.resolve("visible.txt"), "ok");
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "list_directory", "{\"path\":\".\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("list_directory"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
    }

    @Test
    void listDirectoryWithoutPathUsesDefaultDirectoryAndStillPassesSecurity() throws Exception {
        Files.writeString(tempDir.resolve("visible.txt"), "ok");
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "list_directory", "{}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("list_directory"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
    }

    @Test
    void deniedFileMetadataOutsideApprovedRootsDoesNotExecute() throws Exception {
        Files.writeString(tempDir.resolve("meta.txt"), "keep");
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("./configs"),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "file_metadata", "{\"path\":\"meta.txt\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("file_metadata"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.result()).isNull();
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
        assertThat(ToolTestSupport.payload(query.result()).path("data").path("result").path("rows").get(0).path("c").asInt())
                .isEqualTo(1);
    }

    @Test
    void deniedSqliteExecOutsideApprovedRootsDoesNotExecuteSideEffects() {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("./configs"),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(
                sessionId,
                "sqlite_exec",
                "{\"dbPath\":\"blocked.db\",\"sql\":\"CREATE TABLE t(id INTEGER); INSERT INTO t(id) VALUES (1);\"}"
        );
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("sqlite_exec"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.result()).isNull();
        assertThat(Files.exists(tempDir.resolve("blocked.db"))).isFalse();
    }

    @Test
    void allowedTodoCreateWritesToDefaultTodoDbInsideAllowedPath() throws Exception {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("."),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "todo_create", "{\"title\":\"secure todo\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("todo_create"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
        assertThat(Files.exists(tempDir.resolve(".magenta/state.db"))).isTrue();
    }

    @Test
    void todoCreateIgnoresAllowedPathRootsWhenNotBlacklisted() throws Exception {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, toolPolicy(
                RuntimeConfig.SecurityMode.APPROVE_ALL,
                List.of("./configs"),
                Set.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "todo_create", "{\"title\":\"blocked\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("todo_create"));

        assertThat(execution.decision().allowed()).isTrue();
        assertThat(execution.result()).isNotNull();
        assertThat(ToolTestSupport.payload(execution.result()).path("status").asText()).isEqualTo("ok");
        assertThat(Files.exists(tempDir.resolve(".magenta/state.db"))).isTrue();
    }

    @Test
    void deniedTodoAliasBlacklistBlocksTodoTools() {
        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager securityManager = securityManager(tempDir);
        UUID sessionId = UUID.randomUUID();
        securityManager.initializePolicy(sessionId);
        securityManager.setToolPolicy(sessionId, new SecurityManager.ToolPolicy(
                RuntimeConfig.SecurityMode.BLACKLIST,
                false,
                Set.of(),
                Set.of("todo"),
                List.of("."),
                Set.of(),
                new SecurityManager.WebAccessPolicy(false, false),
                List.of()
        ));

        ToolRequest request = ToolTestSupport.request(sessionId, "todo_create", "{\"title\":\"blocked-by-alias\"}");
        SecuredExecution execution = executeSecured(securityManager, toolManager, request, Set.of("todo_create"));

        assertThat(execution.decision().allowed()).isFalse();
        assertThat(execution.result()).isNull();
    }

    @Test
    void outOfRootSymlinkPathRequiresApprovalAndExecutesOnlyWhenApproved() throws Exception {
        Path externalRoot = Files.createTempDirectory("magenta-security-external");
        Path externalFile = externalRoot.resolve("approved.txt");
        Path symlink = tempDir.resolve("external-link");
        try {
            Files.createSymbolicLink(symlink, externalRoot);
        } catch (UnsupportedOperationException e) {
            return; // platform does not support symlinks in test environment
        }

        ToolManager toolManager = ToolManager.withBuiltIns(ToolTestSupport.runtimeConfig(tempDir));
        SecurityManager denyingSecurity = securityManager(tempDir);
        SecurityManager approvingSecurity = securityManager(tempDir, ignored -> SecurityManager.ApprovalResponse.APPROVE);
        UUID denySession = UUID.randomUUID();
        UUID allowSession = UUID.randomUUID();
        denyingSecurity.initializePolicy(denySession);
        approvingSecurity.initializePolicy(allowSession);
        SecurityManager.ToolPolicy policy = toolPolicy(RuntimeConfig.SecurityMode.APPROVE_ALL, List.of("."), Set.of());
        denyingSecurity.setToolPolicy(denySession, policy);
        approvingSecurity.setToolPolicy(allowSession, policy);

        ToolRequest denyRequest = ToolTestSupport.request(denySession, "write_file", "{\"path\":\"external-link/approved.txt\",\"content\":\"blocked\"}");
        SecuredExecution denied = executeSecured(denyingSecurity, toolManager, denyRequest, Set.of("write_file"));
        assertThat(denied.decision().allowed()).isFalse();
        assertThat(Files.exists(externalFile)).isFalse();

        ToolRequest allowRequest = ToolTestSupport.request(allowSession, "write_file", "{\"path\":\"external-link/approved.txt\",\"content\":\"allowed\"}");
        SecuredExecution allowed = executeSecured(approvingSecurity, toolManager, allowRequest, Set.of("write_file"));
        assertThat(allowed.decision().allowed()).isTrue();
        assertThat(allowed.result()).isNotNull();
        assertThat(Files.readString(externalFile)).isEqualTo("allowed");
    }

    private SecurityManager securityManager(Path workspaceRoot) {
        return new SecurityManager(RuntimeConfig.SecurityPolicyConfig.defaults(), workspaceRoot, null, descriptors());
    }

    private SecurityManager securityManager(Path workspaceRoot, SecurityManager.ApprovalCallback callback) {
        return new SecurityManager(RuntimeConfig.SecurityPolicyConfig.defaults(), workspaceRoot, callback, descriptors());
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
