package io.mindspice.magenta.runtime.tools.builtin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.tools.ToolPayloads;
import io.mindspice.magenta.runtime.tools.ToolRequest;
import io.mindspice.magenta.runtime.tools.ToolResult;

/**
 * Annotation-first executable tool surface used for LangChain4j tool schema generation and runtime dispatch.
 */
public final class AnnotatedBuiltInToolCatalog {

    private static final String READ_FILE = "read_file";
    private static final String GREP_FILES = "grep_files";
    private static final String SEARCH_REPLACE = "search_replace";
    private static final String WRITE_FILE = "write_file";
    private static final String DELETE_FILE = "delete_file";
    private static final String SHELL_COMMAND = "shell_command";
    private static final String SQLITE_QUERY = "sqlite_query";
    private static final String SQLITE_EXEC = "sqlite_exec";

    private final FileTools fileTools;
    private final ShellTools shellTools;
    private final SqliteTools sqliteTools;

    public AnnotatedBuiltInToolCatalog(FileTools fileTools, ShellTools shellTools, SqliteTools sqliteTools) {
        this.fileTools = fileTools;
        this.shellTools = shellTools;
        this.sqliteTools = sqliteTools;
    }

    @Tool(name = READ_FILE, value = {
            "Read a UTF-8 file with bounded line output and stable hashline anchors.",
            "Use this before edits to obtain snapshotId and line anchors."
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

    @Tool(name = GREP_FILES, value = {
            "Search files recursively for a literal or regex pattern with bounded output.",
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
        putTextIfPresent(args, "rootPath", rootPath);
        putBooleanIfPresent(args, "regex", regex);
        putBooleanIfPresent(args, "caseSensitive", caseSensitive);
        putIntIfPresent(args, "maxMatches", maxMatches);
        putTextIfPresent(args, "filePattern", filePattern);
        return fileTools.grepFiles(rewriteRequest(request, GREP_FILES, args));
    }

    @Tool(name = SEARCH_REPLACE, value = {
            "Apply deterministic anchored edits to a file.",
            "Requires path, snapshotId, and edits[] entries with startAnchor/endAnchor/replacement."
    })
    public ToolResult searchReplace(
            @ToolMemoryId ToolRequest request,
            @P("Target file path") String path,
            @P("Snapshot id returned by read_file for staleness protection") String snapshotId,
            @P("Array of edit objects with startAnchor, endAnchor, replacement, and optional expectedText") JsonNode edits
    ) {
        ObjectNode args = objectArgs();
        args.put("path", path);
        args.put("snapshotId", snapshotId);
        args.set("edits", edits == null ? ToolPayloads.mapper().createArrayNode() : edits);
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
            "Execute a shell command in the configured workspace root with timeout and bounded output capture."
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
}
