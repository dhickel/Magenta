package io.mindspice.magenta.runtime.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.persistence.DatabaseService;
import io.mindspice.magenta.runtime.persistence.SessionContextCommand;
import io.mindspice.magenta.runtime.tools.builtin.AnnotatedBuiltInToolCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ToolManagerBuiltInsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @TempDir
    Path tempDir;

    @Test
    void readFileReturnsAnchorsAndSnapshot() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("read_file", "{\"path\":\"sample.txt\"}"));

        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("snapshotId").asText()).isNotBlank();
        assertThat(payload.path("data").path("lines")).hasSize(2);
        assertThat(payload.path("data").path("lines").get(0).path("anchor").asText()).startsWith("1:");
    }

    @Test
    void searchReplaceFailsOnSnapshotMismatch() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        String args = """
                {
                  "path": "sample.txt",
                  "snapshotId": "bad",
                  "edits": [
                    {"startAnchor":"1:00","endAnchor":"1:00","replacement":"alpha"}
                  ]
                }
                """;

        ToolResult result = manager.execute(request("search_replace", args));
        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("snapshot_mismatch");
    }

    @Test
    void searchReplaceFailsWhenSnapshotIdMissing() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        String args = """
                {
                  "path": "sample.txt",
                  "edits": [
                    {"startAnchor":"1:00","endAnchor":"1:00","replacement":"alpha"}
                  ]
                }
                """;

        ToolResult result = manager.execute(request("search_replace", args));
        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
        assertThat(payload.path("message").asText()).isEqualTo("Missing required argument: snapshotId");
    }

    @Test
    void searchReplaceRejectsLegacyLiteralArguments() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\nbeta\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        String args = """
                {
                  "path": "sample.txt",
                  "snapshotId": "any",
                  "search": "beta",
                  "replace": "omega"
                }
                """;

        ToolResult result = manager.execute(request("search_replace", args));
        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
        assertThat(payload.path("message").asText()).isEqualTo("Missing required argument: edits");
    }

    @Test
    void searchReplaceAppliesAnchorBasedEdit() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\n");

        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult read = manager.execute(request("read_file", "{\"path\":\"sample.txt\"}"));
        JsonNode readPayload = MAPPER.readTree(read.content());

        String snapshotId = readPayload.path("data").path("snapshotId").asText();
        String anchor = readPayload.path("data").path("lines").get(0).path("anchor").asText();

        String args = MAPPER.writeValueAsString(Map.of(
                "path", "sample.txt",
                "snapshotId", snapshotId,
                "edits", List.of(Map.of(
                        "startAnchor", anchor,
                        "endAnchor", anchor,
                        "replacement", "omega"
                ))
        ));

        ToolResult replace = manager.execute(request("search_replace", args));
        JsonNode replacePayload = MAPPER.readTree(replace.content());

        assertThat(replacePayload.path("status").asText()).isEqualTo("ok");
        assertThat(Files.readString(tempDir.resolve("sample.txt"))).isEqualTo("omega");
    }

    @Test
    void sqliteExecAndQueryRoundTrip() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));

        String execArgs = MAPPER.writeValueAsString(Map.of(
                "dbPath", "test.db",
                "sql", "CREATE TABLE t(id INTEGER PRIMARY KEY, name TEXT); INSERT INTO t(name) VALUES ('a'); INSERT INTO t(name) VALUES ('b');",
                "transactional", true
        ));
        ToolResult exec = manager.execute(request("sqlite_exec", execArgs));
        JsonNode execPayload = MAPPER.readTree(exec.content());
        assertThat(execPayload.path("status").asText())
                .withFailMessage(exec.content())
                .isEqualTo("ok");

        String queryArgs = MAPPER.writeValueAsString(Map.of(
                "dbPath", "test.db",
                "sql", "SELECT id, name FROM t ORDER BY id"
        ));
        ToolResult query = manager.execute(request("sqlite_query", queryArgs));
        JsonNode queryPayload = MAPPER.readTree(query.content());

        assertThat(queryPayload.path("status").asText())
                .withFailMessage(query.content())
                .isEqualTo("ok");
        assertThat(queryPayload.path("data").path("result").path("rows")).hasSize(2);
        assertThat(queryPayload.path("data").path("result").path("rows").get(0).path("name").asText()).isEqualTo("a");
    }

    @Test
    void shellCommandCapturesOutput() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));

        ToolResult result = manager.execute(request("shell_command", "{\"cmd\":\"echo hello\"}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("stdout").asText()).contains("hello");
    }

    @Test
    void todoCrudRoundTripIsSessionScoped() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        String sessionA = UUID.randomUUID().toString();
        String sessionB = UUID.randomUUID().toString();

        ToolResult created = manager.execute(request(sessionA, "todo_create", "{\"title\":\"ship\"}"));
        JsonNode createdPayload = MAPPER.readTree(created.content());
        assertThat(createdPayload.path("status").asText()).isEqualTo("ok");
        String todoId = createdPayload.path("data").path("focus").path("todoId").asText();
        assertThat(todoId).isNotBlank();

        ToolResult listedA = manager.execute(request(sessionA, "todo_list", "{}"));
        ToolResult listedB = manager.execute(request(sessionB, "todo_list", "{}"));
        JsonNode listedAPayload = MAPPER.readTree(listedA.content());
        JsonNode listedBPayload = MAPPER.readTree(listedB.content());

        assertThat(listedAPayload.path("data").path("items")).hasSize(1);
        assertThat(listedAPayload.path("data").path("items").get(0).path("todoId").asText()).isEqualTo(todoId);
        assertThat(listedBPayload.path("data").path("items")).isEmpty();

        ToolResult updated = manager.execute(request(
                sessionA,
                "todo_update",
                "{\"todoId\":\"" + todoId + "\",\"status\":\"done\"}"
        ));
        JsonNode updatedPayload = MAPPER.readTree(updated.content());
        assertThat(updatedPayload.path("status").asText()).isEqualTo("ok");
        assertThat(updatedPayload.path("data").path("focus").path("status").asText()).isEqualTo("done");

        ToolResult deleted = manager.execute(request(sessionA, "todo_delete", "{\"todoId\":\"" + todoId + "\"}"));
        JsonNode deletedPayload = MAPPER.readTree(deleted.content());
        assertThat(deletedPayload.path("status").asText()).isEqualTo("ok");
        assertThat(deletedPayload.path("data").path("deletedTodoId").asText()).isEqualTo(todoId);
    }

    @Test
    void listAgentsReturnsEnabledByDefaultAndIncludesDisabledWhenRequested() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfigWithAgents(tempDir));

        ToolResult enabledOnly = manager.execute(request("list_agents", "{}"));
        ToolResult withDisabled = manager.execute(request("list_agents", "{\"includeDisabled\":true}"));

        JsonNode enabledOnlyPayload = MAPPER.readTree(enabledOnly.content());
        JsonNode withDisabledPayload = MAPPER.readTree(withDisabled.content());

        assertThat(enabledOnlyPayload.path("status").asText()).isEqualTo("ok");
        assertThat(enabledOnlyPayload.path("data").path("agents")).hasSize(1);
        assertThat(enabledOnlyPayload.path("data").path("agents").get(0).path("agentId").asText()).isEqualTo("agent-enabled");

        assertThat(withDisabledPayload.path("status").asText()).isEqualTo("ok");
        assertThat(withDisabledPayload.path("data").path("agents")).hasSize(2);
    }

    @Test
    void todoCreateMissingTitleFailsValidation() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("todo_create", "{}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void delegateAgentInvokesDelegationSupport() throws Exception {
        RuntimeConfig config = runtimeConfigWithAgents(tempDir);
        ToolManager manager = ToolManager.withBuiltIns(
                config,
                (request, targetAgentId, prompt, timeoutMs) -> {
                    JsonNode args;
                    try {
                        args = MAPPER.readTree(request.toolCall().argumentsJson());
                    } catch (Exception e) {
                        throw new IllegalStateException("Failed to parse delegate_agent args", e);
                    }
                    ObjectNode data = MAPPER.createObjectNode();
                    data.put("targetAgentId", targetAgentId);
                    data.put("prompt", prompt);
                    data.put("timeoutMs", timeoutMs == null ? -1 : timeoutMs);
                    data.put("requestTarget", args.path("targetAgentId").asText());
                    return ToolPayloads.success(request, "Delegation completed", data);
                }
        );

        ToolResult result = manager.execute(request(
                "delegate_agent",
                "{\"targetAgentId\":\"agent-enabled\",\"prompt\":\"review this\",\"timeoutMs\":1234}"
        ));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("ok");
        assertThat(payload.path("data").path("targetAgentId").asText()).isEqualTo("agent-enabled");
        assertThat(payload.path("data").path("prompt").asText()).isEqualTo("review this");
        assertThat(payload.path("data").path("timeoutMs").asInt()).isEqualTo(1234);
        assertThat(payload.path("data").path("requestTarget").asText()).isEqualTo("agent-enabled");
    }

    @Test
    void delegateAgentFailsAsUnsupportedWithoutDelegationSupportBridge() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfigWithAgents(tempDir));
        ToolResult result = manager.execute(request(
                "delegate_agent",
                "{\"targetAgentId\":\"agent-enabled\",\"prompt\":\"review this\"}"
        ));

        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("unsupported");
    }

    @Test
    void outputIsBoundedToConfiguredMax() throws Exception {
        ToolManager manager = new ToolManager(Map.of(
                "oversized", request -> ToolResult.handled(
                        request.toolCall().id(),
                        request.toolCall().name(),
                        "x".repeat(100_000)
                )
        ));

        ToolResult result = manager.execute(request("oversized", "{}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("output_too_large");
    }

    @Test
    void toolSpecificationsAreFilteredByRequestedToolIds() {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        List<ToolSpecification> specifications = manager.toolSpecificationsFor(List.of(
                "read_file",
                "essence_create",
                "shell_command",
                "read_file"
        ));

        assertThat(specifications).extracting(ToolSpecification::name)
                .containsExactly("read_file", "shell_command");
    }

    @Test
    void invalidJsonArgumentsFailValidationBeforeInvocation() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("read_file", "{bad json"));

        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
    }

    @Test
    void discoveredBuiltInToolSpecificationsCoverExpectedSurface() {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        List<ToolSpecification> specifications = manager.toolSpecificationsFor(null);

        assertThat(specifications).extracting(ToolSpecification::name).containsExactlyInAnyOrder(
                "read_file",
                "list_directory",
                "file_metadata",
                "grep_files",
                "search_replace",
                "write_file",
                "delete_file",
                "shell_command",
                "sqlite_query",
                "sqlite_exec",
                "todo_create",
                "todo_list",
                "todo_update",
                "todo_delete",
                "history_meta_lookup",
                "history_raw_lookup",
                "list_agents",
                "delegate_agent"
        );
    }

    @Test
    void annotationGeneratedParameterNamesUseSemanticNamesNotArgIndexes() {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolSpecification readFile = manager.toolSpecificationsFor(List.of("read_file")).getFirst();

        assertThat(readFile.parameters()).isNotNull();
        assertThat(readFile.parameters().properties().keySet()).containsExactly("path", "startLine", "endLine");
        assertThat(readFile.parameters().required()).containsExactly("path");
        assertThat(readFile.parameters().properties().keySet()).doesNotContain("arg0", "arg1", "arg2");
    }

    @Test
    void annotationBindingRejectsWrongArgumentType() throws Exception {
        Files.writeString(tempDir.resolve("sample.txt"), "alpha\n");
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("read_file", "{\"path\":\"sample.txt\",\"startLine\":\"one\"}"));

        JsonNode payload = MAPPER.readTree(result.content());
        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
        assertThat(payload.path("message").asText()).contains("startLine");
    }

    @Test
    void searchReplaceSchemaIncludesTypedEditObject() {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolSpecification searchReplace = manager.toolSpecificationsFor(List.of("search_replace")).getFirst();

        JsonSchemaElement editsElement = searchReplace.parameters().properties().get("edits");
        assertThat(editsElement).isInstanceOf(JsonArraySchema.class);
        JsonSchemaElement itemSchema = ((JsonArraySchema) editsElement).items();
        assertThat(itemSchema).isInstanceOf(JsonObjectSchema.class);
        JsonObjectSchema editObject = (JsonObjectSchema) itemSchema;
        assertThat(editObject.properties().keySet()).contains("startAnchor", "endAnchor", "replacement", "expectedText");
        assertThat(editObject.required()).contains("startAnchor", "endAnchor", "replacement");
    }

    @Test
    void grepAndSearchReplaceDescriptionsIncludeUsageGuardrails() {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolSpecification grep = manager.toolSpecificationsFor(List.of("grep_files")).getFirst();
        ToolSpecification shell = manager.toolSpecificationsFor(List.of("shell_command")).getFirst();
        ToolSpecification searchReplace = manager.toolSpecificationsFor(List.of("search_replace")).getFirst();

        assertThat(grep.description()).contains("optional rootPath");
        assertThat(grep.description()).contains("filePattern");
        assertThat(grep.description()).contains("does not return snapshotId");
        assertThat(grep.description()).contains("read_file");
        assertThat(shell.description()).contains("single command invocation");
        assertThat(shell.description()).contains("operators/chaining");
        assertThat(searchReplace.description()).contains("startAnchor");
        assertThat(searchReplace.description().toLowerCase(java.util.Locale.ROOT)).contains("do not invent anchors");
    }

    @Test
    void historyMetaAndRawLookupRoundTrip() throws Exception {
        DatabaseService databaseService = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();
        databaseService.execute(new SessionContextCommand.InitializeSession(
                sessionId,
                "agent-default",
                "history",
                1,
                List.of(new ContextElement.SystemCoreMsg("system prompt"))
        ));
        databaseService.execute(new SessionContextCommand.AppendMessages(
                sessionId,
                List.of(
                        new ContextElement.UserMsg("run shell"),
                        new ContextElement.AssistantMsg(
                                "",
                                List.of(new ContextElement.ToolCall("call-1", "shell_command", "{\"cmd\":\"echo hello\"}"))
                        ),
                        new ContextElement.ToolMsg(
                                "call-1",
                                "shell_command",
                                "{\"status\":\"ok\",\"code\":\"ok\",\"data\":{\"command\":\"echo hello\"}}",
                                "{\"status\":\"ok\",\"code\":\"ok\",\"data\":{\"command\":\"echo hello\",\"stdout\":\"hello\\n\"}}",
                                false
                        )
                )
        ));

        ToolManager manager = ToolManager.withBuiltIns(
                runtimeConfig(tempDir),
                databaseService::execute,
                AnnotatedBuiltInToolCatalog.DelegationSupport.unsupported()
        );
        ToolResult meta = manager.execute(request(sessionId, "history_meta_lookup", "{\"limit\":10}"));
        JsonNode metaPayload = MAPPER.readTree(meta.content());

        assertThat(metaPayload.path("status").asText()).isEqualTo("ok");
        assertThat(metaPayload.path("data").path("rows")).isNotEmpty();
        int toolMessageId = metaPayload.path("data").path("rows").get(0).path("messageId").asInt();

        ToolResult raw = manager.execute(request(
                sessionId,
                "history_raw_lookup",
                "{\"messageId\":" + toolMessageId + ",\"startChar\":0,\"maxChars\":100}"
        ));
        JsonNode rawPayload = MAPPER.readTree(raw.content());

        assertThat(rawPayload.path("status").asText()).isEqualTo("ok");
        assertThat(rawPayload.path("data").path("messageId").asInt()).isEqualTo(toolMessageId);
        assertThat(rawPayload.path("data").path("rawContent").asText()).contains("command");
    }

    @Test
    void historyRawLookupRequiresMessageId() throws Exception {
        ToolManager manager = ToolManager.withBuiltIns(runtimeConfig(tempDir));
        ToolResult result = manager.execute(request("history_raw_lookup", "{\"startChar\":0}"));
        JsonNode payload = MAPPER.readTree(result.content());

        assertThat(payload.path("status").asText()).isEqualTo("failed");
        assertThat(payload.path("code").asText()).isEqualTo("validation_error");
        assertThat(payload.path("message").asText()).contains("messageId");
    }

    private RuntimeConfig runtimeConfig(Path workspaceRoot) {
        return new RuntimeConfig(
                workspaceRoot,
                workspaceRoot,
                "agent-default",
                "agent-compaction",
                8,
                64,
                32_768,
                200,
                500,
                Map.of(),
                Map.of(),
                Map.of(),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );
    }

    private RuntimeConfig runtimeConfigWithAgents(Path workspaceRoot) {
        RuntimeConfig.AgentConfig enabled = new RuntimeConfig.AgentConfig(
                "agent-enabled",
                "model-default",
                List.of("base.system"),
                "",
                List.of(),
                List.of(),
                List.of("delegate_agent"),
                true
        );
        RuntimeConfig.AgentConfig disabled = new RuntimeConfig.AgentConfig(
                "agent-disabled",
                "model-default",
                List.of("base.system"),
                "",
                List.of(),
                List.of(),
                List.of("delegate_agent"),
                false
        );
        return new RuntimeConfig(
                workspaceRoot,
                workspaceRoot,
                "agent-enabled",
                "agent-enabled",
                8,
                64,
                32_768,
                200,
                500,
                Map.of(),
                Map.of(
                        enabled.id(), enabled,
                        disabled.id(), disabled
                ),
                Map.of(),
                RuntimeConfig.SecurityPolicyConfig.defaults(),
                RuntimeConfig.TerminalConfig.defaults()
        );
    }

    private ToolRequest request(String toolName, String argsJson) {
        return request(UUID.randomUUID().toString(), toolName, argsJson);
    }

    private ToolRequest request(String sessionId, String toolName, String argsJson) {
        return new ToolRequest(
                sessionId,
                "agent-default",
                new ContextElement.ToolCall("call-1", toolName, argsJson)
        );
    }
}
