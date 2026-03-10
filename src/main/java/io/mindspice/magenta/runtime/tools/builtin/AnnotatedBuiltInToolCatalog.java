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
            "Reads the content of a UTF-8 encoded file within the workspace, returning text content along with a snapshotId and stable line:hh anchors.",
            "Always use this tool before attempting to edit a file with search_replace to ensure you have the latest snapshot and correct line hashes.",
            "Input requires a 'path' (workspace-relative or absolute). Optional 'startLine' and 'endLine' (1-based, inclusive) allow reading specific ranges, which is essential for large files to avoid hitting runtime output limits.",
            "The returned snapshotId must be passed to subsequent write or edit operations to prevent state corruption. Success payloads include the requested text range and metadata; failures occur if the file is missing, unreadable, or exceeds system size limits."
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
            "Lists the contents of a specified directory, providing names, types (file/dir), and sizes for all entries without reading file content.",
            "This is the primary tool for workspace exploration and project structure discovery. Use it to locate source code, configuration files, and documentation.",
            "Optional parameters include 'path' (defaults to '.'), 'maxEntries' to limit the response size, and 'includeHidden' to toggle visibility of dot-prefixed files.",
            "If a directory is extremely large, the output may be truncated based on runtime safety limits. This tool is read-only and will fail if the path does not exist or is not a directory."
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
            "Retrieves detailed metadata for a single file or directory, including size, timestamps, permissions, and its current snapshotId.",
            "Use this tool when you need to check if a file exists, verify its size, or obtain a snapshotId for a write/delete operation without needing to read the actual file content.",
            "Requires a 'path' parameter. This is a lightweight alternative to read_file and is ideal for verifying the state of large assets or binary files.",
            "Returns a structured JSON payload containing filesystem attributes; fails if the path is invalid or inaccessible due to security constraints."
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
            "Recursively searches through files in the workspace for lines matching a literal string or regular expression, returning matches with their line numbers and hashes.",
            "This is the most efficient way to locate specific symbols, patterns, or code snippets across multiple files without reading each file individually.",
            "Parameters: 'pattern' (search string), 'rootPath' (starting directory, defaults to '.'), 'regex' (enables regex mode), 'caseSensitive' (enables case sensitivity), 'maxMatches' (limits results), and 'filePattern' (glob filter for file names like '**/*.java' or basename filters such as '*.md').",
            "The returned payload includes 'matches' containing line text and 'line:hh' anchors which are compatible with search_replace. rootPath is optional. If too many matches are found, file contents and matches will be truncated for performance."
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
            "Applies highly precise, deterministic edits to a file using stable 'line:hh' anchors obtained from a prior read_file or grep_files call.",
            "This is the safest and most efficient method for modifying code, as it ensures changes are applied to the correct logical blocks even if file line numbers shift slightly.",
            "Requires 'path' (the target file), 'snapshotId' (to prevent editing stale file states), and a list of 'edits' containing: 'startAnchor' (the beginning line and its hash), 'endAnchor' (the ending line and its hash), and 'replacement' (the new text).",
            "Optional 'expectedText' can be included within an edit as an additional verification layer to ensure the content between anchors exactly matches your assumptions.",
            "This tool is strictly guarded; it will fail if the anchors are invalid, if the snapshotId is outdated, or if the expected text does not match. Use it to ensure surgical accuracy and prevent unintended side effects; do not invent anchors."
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
            "Writes the provided UTF-8 text content to a file at the specified path, creating the file if it doesn't exist or overwriting it if it does.",
            "Use this for creating new files or replacing the entire content of existing ones. For partial edits, prefer search_replace for greater precision and safety.",
            "Parameters: 'path' (target file), 'content' (raw text to write), 'overwrite' (boolean, must be true to replace existing files), and 'expectedSnapshotId' (guard to ensure the file hasn't changed since your last read).",
            "This operation is atomic and requires write permissions. Success returns confirmation of the write; failures occur if the path is invalid, permissions are denied, or the snapshot check fails."
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
            "Removes a single file from the workspace filesystem permanently. This action is irreversible, so use it with caution.",
            "Always verify that the file is no longer needed before calling this tool. For added safety, provide an 'expectedSnapshotId' to ensure you're deleting the exact state you just inspected.",
            "Requires a 'path' parameter (relative or absolute). If the file is a directory, this tool will fail; use recursive shell commands if directory deletion is necessary.",
            "Success returns a confirmation message; failures occur if the file is missing, the path points to a directory, or the snapshotId does not match."
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
            "Executes a single command invocation (non-interactive, via 'bash -lc') within the workspace root directory, capturing the resulting output and error streams.",
            "Use this tool for running build scripts, executing unit tests, managing dependencies, or performing filesystem operations that are not covered by the specialized file tools.",
            "Security constraints are strictly enforced: operators/chaining (e.g., '|', ';', '&&', '>', '`', etc.) are blocked to prevent unauthorized access or command injection.",
            "Parameters: 'cmd' (the raw command string to run) and 'timeoutMs' (optional duration after which the process is forcibly terminated).",
            "This tool provides direct access to the system's capabilities; always verify the command's safety and expected behavior before execution.",
            "Returns a structured result including 'exitCode', 'stdout', and 'stderr'. Use the exitCode (0 for success) to determine the outcome of the command."
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
            "Executes a single read-only SQL SELECT statement against a SQLite database file and returns the resulting rows as a structured JSON array.",
            "Use this tool for data discovery, state inspection, or analytical queries on workspace-resident databases without modifying the data.",
            "Parameters: 'dbPath' (path to the .sqlite or .db file) and 'sql' (a single, valid SELECT statement). Multiple statements are not supported here.",
            "This tool is strictly read-only and will fail if the SQL statement attempts to modify the database state. If the query returns a large volume of data, the result set will be truncated."
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
            "Executes one or more mutating SQL statements against a SQLite database file, allowing you to create tables, insert/update/delete rows, or modify the database schema.",
            "Use this tool for persistence, state management, or data transformations within workspace-resident databases.",
            "Parameters: 'dbPath' (path to the target database file), 'sql' (one or more SQL statements to execute), and 'transactional' (optional boolean, defaults to true, which wraps all statements in a single transaction).",
            "This tool handles both single-statement mutations and complex multi-step operations efficiently. Success returns the number of affected rows and the resulting database state metadata.",
            "Security constraints prevent access to databases outside the authorized scope; this tool will fail if the provided database path is invalid or if the SQL syntax is incorrect."
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
            "Creates a new todo item in the current session's tracker as the first step in the todo lifecycle (create -> list -> update -> delete).",
            "Use this tool to break down a larger user request into manageable steps, providing visibility into progress and upcoming actions.",
            "Parameters: 'title' (a short summary of the task) and 'details' (optional notes, dependencies, or success criteria).",
            "Each todo is assigned a unique ID used by update/delete calls. Todos are persisted in the runtime state DB and scoped by sessionId; use todo_list to read current state."
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
            "Reads todo state for the current session by listing active and/or completed items.",
            "Use this as the canonical todo read operation to verify progress, identify pending actions, and recover state after interruption.",
            "Optional parameters include 'status' (filter by 'open' or 'done') and 'limit' (to bound the result count for sessions with many tasks).",
            "Returns structured todo objects including todoId, title, details, status, createdAtMs, and updatedAtMs."
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
            "Updates an existing todo item by todoId (title, details, and/or status).",
            "Call this immediately when task state changes, especially to mark completion with status='done'.",
            "Requires a 'todoId'. Optional parameters include 'title' (rename the task), 'details' (add additional context), and 'status' ('open' or 'done').",
            "This operation is atomic and will fail if the provided todoId does not exist in the current session."
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
            "Deletes an existing todo item from the current session by todoId.",
            "Use this only to remove obsolete or incorrectly created items after confirming they should not remain in plan state.",
            "Requires a 'todoId' parameter. Deletion is immediate and irreversible for the current session.",
            "Success returns a confirmation message; fails if the todoId is not found or the session is inactive."
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
            "Provides a list of all configured agents currently available in the runtime, along with their IDs, capabilities, and status.",
            "Call this tool before attempting to use delegate_agent to discover valid delegation targets and their associated metadata (e.g., modelId, tool count).",
            "Accepts an optional 'includeDisabled' boolean to show agents that are currently inactive. The output is sorted alphabetically by agentId for consistency.",
            "Success returns a list of agent descriptors; use this to understand the specialized expertise available for complex sub-tasks or parallel execution."
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
                    agentNode.put("taskCount", agent.tasks().size());
                    agentNode.put("workflowCount", agent.workflows().size());
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
            "Delegates a complex prompt or specific task to another configured agent, returning the final response as a structured ToolResult.",
            "Use this tool for parallelizing investigation, leveraging specialized agent expertise, or executing tasks that require a separate session context.",
            "Delegation is synchronous: it spawns an ephemeral child session, executes the prompt using the target agent's specific model and tools, and then returns the outcome to you.",
            "Requires 'targetAgentId' (which should be verified first with list_agents) and 'prompt' (the instruction to execute). Optional 'timeoutMs' to limit child session duration.",
            "This is a powerful orchestration tool for building multi-agent workflows. Success returns the delegated agent's response payload; failures occur if the target agent is missing, disabled, or if the child session times out."
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
