package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.security.ToolSecurityDescriptor;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Annotation-first executable tool surface used for LangChain4j tool schema generation and runtime dispatch.
 */
public final class AnnotatedBuiltInToolCatalog {

    private static final String READ_FILE = "read_file";
    private static final String LIST_DIRECTORY = "list_directory";
    private static final String FILE_METADATA = "file_metadata";
    private static final String GREP_FILES = "grep_files";
    private static final String SEARCH_REPLACE = "search_replace";
    private static final String WRITE_FILE = "write_file";
    private static final String DELETE_FILE = "delete_file";
    private static final String SHELL_COMMAND = "shell_command";
    private static final String SQLITE_QUERY = "sqlite_query";
    private static final String SQLITE_EXEC = "sqlite_exec";
    private static final String TODO_CREATE = "todo_create";
    private static final String TODO_LIST = "todo_list";
    private static final String TODO_UPDATE = "todo_update";
    private static final String TODO_DELETE = "todo_delete";
    private static final String LIST_AGENTS = "list_agents";
    private static final String DELEGATE_AGENT = "delegate_agent";

    private final FileTools fileTools;
    private final ShellTools shellTools;
    private final SqliteTools sqliteTools;
    private final TodoTools todoTools;
    private final Map<String, RuntimeConfig.AgentConfig> agentsById;
    private final DelegationSupport delegationSupport;

    public AnnotatedBuiltInToolCatalog(
            FileTools fileTools,
            ShellTools shellTools,
            SqliteTools sqliteTools,
            TodoTools todoTools,
            Map<String, RuntimeConfig.AgentConfig> agentsById,
            DelegationSupport delegationSupport
    ) {
        this.fileTools = Objects.requireNonNull(fileTools, "fileTools");
        this.shellTools = Objects.requireNonNull(shellTools, "shellTools");
        this.sqliteTools = Objects.requireNonNull(sqliteTools, "sqliteTools");
        this.todoTools = Objects.requireNonNull(todoTools, "todoTools");
        this.agentsById = agentsById == null ? Map.of() : Map.copyOf(agentsById);
        this.delegationSupport = delegationSupport == null
                ? DelegationSupport.unsupported()
                : delegationSupport;
    }

    @Tool(name = READ_FILE, value = {
            "Read a UTF-8 file with bounded line output and stable hashline anchors.",
            "Use this before edits to obtain snapshotId and anchors in line:hh format."
    })
    public ToolResult readFile(
            @ToolMemoryId ToolRequest request,
            @P("Workspace-relative or absolute file path") String path,
            @P(value = "1-based inclusive start line. Defaults to 1.", required = false) Integer startLine,
            @P(value = "1-based inclusive end line. Defaults to EOF capped by runtime limits.", required = false) Integer endLine
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        putIntIfPresent(args, "startLine", startLine);
        putIntIfPresent(args, "endLine", endLine);
        return fileTools.readFile(rewriteRequest(request, READ_FILE, args));
    }

    @Tool(name = LIST_DIRECTORY, value = {
            "List directory entries with bounded output.",
            "Use for project discovery without reading file contents."
    })
    public ToolResult listDirectory(
            @ToolMemoryId ToolRequest request,
            @P(value = "Directory path to list. Defaults to '.'", required = false) String path,
            @P(value = "Maximum entries to return. Defaults to runtime bound.", required = false) Integer maxEntries,
            @P(value = "Include dot-prefixed hidden files when true. Defaults to false.", required = false) Boolean includeHidden
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path == null ? "." : path);
        putIntIfPresent(args, "maxEntries", maxEntries);
        putBooleanIfPresent(args, "includeHidden", includeHidden);
        return fileTools.listDirectory(rewriteRequest(request, LIST_DIRECTORY, args));
    }

    @Tool(name = FILE_METADATA, value = {
            "Inspect one file or directory without reading full content."
    })
    public ToolResult fileMetadata(
            @ToolMemoryId ToolRequest request,
            @P("Target file or directory path") String path
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        return fileTools.fileMetadata(rewriteRequest(request, FILE_METADATA, args));
    }

    @Tool(name = GREP_FILES, value = {
            "Search files recursively for a literal or regex pattern with bounded output.",
            "pattern applies to file contents (line text), not file names.",
            "rootPath is optional and defaults to '.'. It must resolve to an existing directory.",
            "filePattern supports basename filters (for example 'fractal.lisp') and glob filters (for example '**/*.lisp').",
            "Returns matched lines with anchors for deterministic follow-up edits."
    })
    public ToolResult grepFiles(
            @ToolMemoryId ToolRequest request,
            @P("Text or regex pattern to search for") String pattern,
            @P(value = "Directory root path for recursive search. Defaults to '.'", required = false) String rootPath,
            @P(value = "When true, treat pattern as regular expression", required = false) Boolean regex,
            @P(value = "When false, perform case-insensitive matching", required = false) Boolean caseSensitive,
            @P(value = "Maximum matches to return. Defaults to runtime bound.", required = false) Integer maxMatches,
            @P(value = "Optional glob file pattern filter (for example '**/*.java')", required = false) String filePattern
    ) {
        ObjectNode args = objectArgs();
        args.put("pattern", pattern);
        args.put("rootPath", rootPath == null ? "." : rootPath);
        putBooleanIfPresent(args, "regex", regex);
        putBooleanIfPresent(args, "caseSensitive", caseSensitive);
        putIntIfPresent(args, "maxMatches", maxMatches);
        putTextIfPresent(args, "filePattern", filePattern);
        return fileTools.grepFiles(rewriteRequest(request, GREP_FILES, args));
    }

    @Tool(name = SEARCH_REPLACE, value = {
            "Apply deterministic anchored edits to a file.",
            "Requires path, snapshotId, and edits[] entries with startAnchor/endAnchor/replacement.",
            "Anchors must exactly match line:hh values returned by read_file or grep_files; do not invent anchors."
    })
    public ToolResult searchReplace(
            @ToolMemoryId ToolRequest request,
            @P("Target file path") String path,
            @P("Snapshot id returned by read_file for staleness protection") String snapshotId,
            @P("Array of edit objects with startAnchor, endAnchor, replacement, and optional expectedText")
            List<SearchReplaceEdit> edits
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        args.put("snapshotId", snapshotId);
        args.set("edits", ToolPayloads.mapper().valueToTree(edits == null ? List.of() : edits));
        return fileTools.searchReplace(rewriteRequest(request, SEARCH_REPLACE, args));
    }

    @Tool(name = WRITE_FILE, value = {
            "Write UTF-8 text to a file with overwrite and optional snapshot guard controls."
    })
    public ToolResult writeFile(
            @ToolMemoryId ToolRequest request,
            @P("Target file path") String path,
            @P("File content to write") String content,
            @P(value = "Allow overwrite when target exists. Defaults to false.", required = false) Boolean overwrite,
            @P(value = "Optional snapshot id required to match current file state before write", required = false) String expectedSnapshotId
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        args.put("content", content);
        putBooleanIfPresent(args, "overwrite", overwrite);
        putTextIfPresent(args, "expectedSnapshotId", expectedSnapshotId);
        return fileTools.writeFile(rewriteRequest(request, WRITE_FILE, args));
    }

    @Tool(name = DELETE_FILE, value = {
            "Delete one file with optional snapshot guard validation."
    })
    public ToolResult deleteFile(
            @ToolMemoryId ToolRequest request,
            @P("Target file path") String path,
            @P(value = "Optional snapshot id required to match current file state before delete", required = false) String expectedSnapshotId
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        putTextIfPresent(args, "expectedSnapshotId", expectedSnapshotId);
        return fileTools.deleteFile(rewriteRequest(request, DELETE_FILE, args));
    }

    @Tool(name = SHELL_COMMAND, value = {
            "Execute a shell command in the configured workspace root with timeout and bounded output capture.",
            "Use a single command invocation; shell operators/chaining (for example '|', ';', '&&', redirects) are blocked by security."
    })
    public ToolResult shellCommand(
            @ToolMemoryId ToolRequest request,
            @P("Shell command string to execute via 'bash -lc'") String cmd,
            @P(value = "Timeout in milliseconds. Defaults to runtime value.", required = false) Integer timeoutMs
    ) {
        ObjectNode args = objectArgs();
        args.put("cmd", cmd);
        putIntIfPresent(args, "timeoutMs", timeoutMs);
        return shellTools.shellCommand(rewriteRequest(request, SHELL_COMMAND, args));
    }

    @Tool(name = SQLITE_QUERY, value = {
            "Run one read-only SQLite SQL statement and return rows as structured JSON."
    })
    public ToolResult sqliteQuery(
            @ToolMemoryId ToolRequest request,
            @P("SQLite database path") String dbPath,
            @P("Read-only SQL statement (single statement)") String sql
    ) {
        ObjectNode args = objectArgs();
        args.put("dbPath", dbPath);
        args.put("sql", sql);
        return sqliteTools.sqliteQuery(rewriteRequest(request, SQLITE_QUERY, args));
    }

    @Tool(name = SQLITE_EXEC, value = {
            "Execute one or more mutating SQLite SQL statements with optional transaction wrapping."
    })
    public ToolResult sqliteExec(
            @ToolMemoryId ToolRequest request,
            @P("SQLite database path") String dbPath,
            @P("SQL statement(s) to execute") String sql,
            @P(value = "Execute all statements in one transaction. Defaults to true.", required = false) Boolean transactional
    ) {
        ObjectNode args = objectArgs();
        args.put("dbPath", dbPath);
        args.put("sql", sql);
        putBooleanIfPresent(args, "transactional", transactional);
        return sqliteTools.sqliteExec(rewriteRequest(request, SQLITE_EXEC, args));
    }

    @Tool(name = TODO_CREATE, value = {
            "Create a todo item for the current session.",
            "Todos are scoped to the calling session id."
    })
    public ToolResult todoCreate(
            @ToolMemoryId ToolRequest request,
            @P("Short todo title") String title,
            @P(value = "Optional details/notes for this todo", required = false) String details
    ) {
        ObjectNode args = objectArgs();
        args.put("title", title);
        putTextIfPresent(args, "details", details);
        return todoTools.todoCreate(rewriteRequest(request, TODO_CREATE, args));
    }

    @Tool(name = TODO_LIST, value = {
            "List todos for the current session.",
            "Optional status filter accepts 'open' or 'done'."
    })
    public ToolResult todoList(
            @ToolMemoryId ToolRequest request,
            @P(value = "Optional status filter: open|done", required = false) String status,
            @P(value = "Maximum todos to return. Defaults to runtime bound.", required = false) Integer limit
    ) {
        ObjectNode args = objectArgs();
        putTextIfPresent(args, "status", status);
        putIntIfPresent(args, "limit", limit);
        return todoTools.todoList(rewriteRequest(request, TODO_LIST, args));
    }

    @Tool(name = TODO_UPDATE, value = {
            "Update one todo for the current session by id.",
            "Provide at least one of: title, details, status."
    })
    public ToolResult todoUpdate(
            @ToolMemoryId ToolRequest request,
            @P("Todo id") String todoId,
            @P(value = "Optional replacement title", required = false) String title,
            @P(value = "Optional replacement details", required = false) String details,
            @P(value = "Optional status: open|done", required = false) String status
    ) {
        ObjectNode args = objectArgs();
        args.put("todoId", todoId);
        putTextIfPresent(args, "title", title);
        putTextIfPresent(args, "details", details);
        putTextIfPresent(args, "status", status);
        return todoTools.todoUpdate(rewriteRequest(request, TODO_UPDATE, args));
    }

    @Tool(name = TODO_DELETE, value = {
            "Delete one todo for the current session by id."
    })
    public ToolResult todoDelete(
            @ToolMemoryId ToolRequest request,
            @P("Todo id") String todoId
    ) {
        ObjectNode args = objectArgs();
        args.put("todoId", todoId);
        return todoTools.todoDelete(rewriteRequest(request, TODO_DELETE, args));
    }

    @Tool(name = LIST_AGENTS, value = {
            "List available configured agents by id.",
            "Use this before delegate_agent to discover valid targets."
    })
    public ToolResult listAgents(
            @ToolMemoryId ToolRequest request,
            @P(value = "Include disabled agents when true", required = false) Boolean includeDisabled
    ) {
        ObjectNode args = objectArgs();
        putBooleanIfPresent(args, "includeDisabled", includeDisabled);
        ToolRequest rewritten = rewriteRequest(request, LIST_AGENTS, args);

        boolean includeDisabledAgents = Boolean.TRUE.equals(includeDisabled);
        ArrayNode agents = ToolPayloads.mapper().createArrayNode();

        agentsById.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    RuntimeConfig.AgentConfig agent = entry.getValue();
                    if (!includeDisabledAgents && !agent.enabled()) {
                        return;
                    }
                    ObjectNode agentNode = ToolPayloads.mapper().createObjectNode();
                    agentNode.put("agentId", agent.id());
                    agentNode.put("modelId", agent.modelId());
                    agentNode.put("enabled", agent.enabled());
                    agentNode.put("promptCount", agent.promptIds().size());
                    agentNode.put("taskCount", agent.taskIds().size());
                    agentNode.put("workflowCount", agent.workflowIds().size());
                    agentNode.put("toolCount", agent.toolIds().size());
                    agents.add(agentNode);
                });

        ObjectNode data = ToolPayloads.mapper().createObjectNode();
        data.put("count", agents.size());
        data.put("includeDisabled", includeDisabledAgents);
        data.set("agents", agents);
        return ToolPayloads.success(rewritten, "Agents listed", data);
    }

    @Tool(name = DELEGATE_AGENT, value = {
            "Delegate a prompt to another configured agent and return its final response.",
            "Delegation is synchronous and runs in an ephemeral child session."
    })
    public ToolResult delegateAgent(
            @ToolMemoryId ToolRequest request,
            @P("Target agent id") String targetAgentId,
            @P("Prompt/task to execute in the delegated session") String prompt,
            @P(value = "Optional timeout in milliseconds", required = false) Integer timeoutMs
    ) {
        ObjectNode args = objectArgs();
        args.put("targetAgentId", targetAgentId);
        args.put("prompt", prompt);
        putIntIfPresent(args, "timeoutMs", timeoutMs);
        ToolRequest rewritten = rewriteRequest(request, DELEGATE_AGENT, args);
        return delegationSupport.delegate(rewritten, targetAgentId, prompt, timeoutMs);
    }

    private ToolRequest rewriteRequest(ToolRequest baseRequest, String toolName, ObjectNode args) {
        ToolRequest safe = ensureRequest(baseRequest, toolName);
        ContextElement.ToolCall baseCall = safe.toolCall();
        ContextElement.ToolCall rewritten = new ContextElement.ToolCall(
                baseCall.id(),
                toolName,
                args.toString()
        );
        return new ToolRequest(safe.sessionId(), safe.agentId(), rewritten);
    }

    private ToolRequest ensureRequest(ToolRequest request, String toolName) {
        if (request != null && request.toolCall() != null) {
            return request;
        }
        return new ToolRequest(
                "",
                "",
                new ContextElement.ToolCall("", toolName, "{}")
        );
    }

    private ObjectNode objectArgs() {
        return ToolPayloads.mapper().createObjectNode();
    }

    private void putTextIfPresent(ObjectNode node, String key, String value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private void putIntIfPresent(ObjectNode node, String key, Integer value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    private void putBooleanIfPresent(ObjectNode node, String key, Boolean value) {
        if (value != null) {
            node.put(key, value);
        }
    }

    public Map<String, ToolSecurityDescriptor> securityDescriptorsByName() {
        ToolSecurityDescriptor pathDescriptor = ToolSecurityDescriptor.path(List.of("path", "filePath", "targetPath"), true);
        ToolSecurityDescriptor defaultDirectoryPathDescriptor = ToolSecurityDescriptor.path(
                List.of("path", "filePath", "targetPath", "rootPath"),
                true,
                "."
        );
        ToolSecurityDescriptor grepDescriptor = ToolSecurityDescriptor.path(List.of("rootPath", "path"), true, ".");
        ToolSecurityDescriptor sqlitePathDescriptor = ToolSecurityDescriptor.path(List.of("dbPath", "path"), true);
        ToolSecurityDescriptor shellDescriptor = ToolSecurityDescriptor.command(List.of("cmd", "command"), true);

        return Map.ofEntries(
                Map.entry(READ_FILE, pathDescriptor),
                Map.entry(LIST_DIRECTORY, defaultDirectoryPathDescriptor),
                Map.entry(FILE_METADATA, pathDescriptor),
                Map.entry(GREP_FILES, grepDescriptor),
                Map.entry(SEARCH_REPLACE, pathDescriptor),
                Map.entry(WRITE_FILE, pathDescriptor),
                Map.entry(DELETE_FILE, pathDescriptor),
                Map.entry(SHELL_COMMAND, shellDescriptor),
                Map.entry(SQLITE_QUERY, sqlitePathDescriptor),
                Map.entry(SQLITE_EXEC, sqlitePathDescriptor),
                Map.entry(TODO_CREATE, ToolSecurityDescriptor.path(List.of(), false)),
                Map.entry(TODO_LIST, ToolSecurityDescriptor.path(List.of(), false)),
                Map.entry(TODO_UPDATE, ToolSecurityDescriptor.path(List.of(), false)),
                Map.entry(TODO_DELETE, ToolSecurityDescriptor.path(List.of(), false))
        );
    }

    public record SearchReplaceEdit(
            @P("Start line anchor in line:hh format") String startAnchor,
            @P("End line anchor in line:hh format") String endAnchor,
            @P("Replacement text for the anchored range") String replacement,
            @P(value = "Optional exact text expected in anchored range", required = false) String expectedText
    ) {
    }

    @FunctionalInterface
    public interface DelegationSupport {
        ToolResult delegate(ToolRequest request, String targetAgentId, String prompt, Integer timeoutMs);

        static DelegationSupport unsupported() {
            return (request, targetAgentId, prompt, timeoutMs) -> ToolPayloads.failure(
                    request,
                    "unsupported",
                    "delegate_agent is not configured in this runtime",
                    null,
                    true
            );
        }
    }
}
