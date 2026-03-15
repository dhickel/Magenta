package io.mindspice.magenta.runtime.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.sjbdc.SimplyJDBC;
import io.mindspice.sjbdc.SjColumn;
import io.mindspice.sjbdc.SjResult;
import org.sqlite.SQLiteDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DatabaseService {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final int DEFAULT_LIST_LIMIT = 100;
    private static final int MAX_COMPACTION_TOOL_SCAN_LIMIT = 200;
    private static final int MAX_COMPACTION_TODO_LIMIT = 200;
    private static final int MAX_HISTORY_META_LIMIT = 200;
    private static final int MAX_HISTORY_RAW_CHARS = 16_000;
    private static final int PREVIEW_MAX_CHARS = 200;
    private static final String STATUS_OPEN = "open";
    private static final String STATUS_DONE = "done";

    private final Path workspaceRoot;
    private final Path dbPath;
    private final SimplyJDBC simplyJDBC = new SimplyJDBC();
    private boolean schemaInitialized = false;

    public DatabaseService(Path workspaceRoot) {
        this(workspaceRoot, null);
    }

    public DatabaseService(Path workspaceRoot, Path overrideDbPathOrNull) {
        this.workspaceRoot = workspaceRoot == null
                ? Path.of("").toAbsolutePath().normalize()
                : workspaceRoot.toAbsolutePath().normalize();
        this.dbPath = resolveDbPath(this.workspaceRoot, overrideDbPathOrNull);
    }

    public Path dbPath() {
        return dbPath;
    }

    public ToolCommandResult execute(ToolCommand command) {
        if (command == null) {
            return new CommonCommandResults.Failure("validation_error", "Missing tool command");
        }
        return switch (command) {
            case ToolCommand.TodoCreate create -> todoCreate(create);
            case ToolCommand.TodoList list -> todoList(list);
            case ToolCommand.TodoUpdate update -> todoUpdate(update);
            case ToolCommand.TodoDelete delete -> todoDelete(delete);
            case ToolCommand.HistoryMetaLookup lookup -> historyMetaLookup(lookup);
            case ToolCommand.HistoryRawLookup lookup -> historyRawLookup(lookup);
        };
    }

    public SessionContextResult execute(SessionContextCommand command) {
        if (command == null) {
            return new CommonCommandResults.Failure("validation_error", "Missing session context command");
        }
        return switch (command) {
            case SessionContextCommand.InitializeSession initialize -> initializeSession(initialize);
            case SessionContextCommand.AppendMessage append -> appendMessages(new SessionContextCommand.AppendMessages(
                    append.sessionId(),
                    List.of(append.message())
            ));
            case SessionContextCommand.AppendMessages append -> appendMessages(append);
            case SessionContextCommand.ReplaceActiveContext replace -> replaceActiveContext(replace);
            case SessionContextCommand.UpsertStateSystemMessage upsert -> upsertStateSystemMessage(upsert);
            case SessionContextCommand.LoadActiveContext load -> loadActiveContext(load);
            case SessionContextCommand.GetMessageById get -> getMessageById(get);
            case SessionContextCommand.LoadCompactionState load -> loadCompactionState(load);
        };
    }

    private ToolCommandResult todoCreate(ToolCommand.TodoCreate command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (isBlank(command.title())) {
            return new CommonCommandResults.Failure("validation_error", "Missing required argument: title");
        }

        String sessionId = command.sessionId().trim();
        String title = command.title().trim();
        long now = Instant.now().toEpochMilli();
        String todoId = UUID.randomUUID().toString();

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
            Optional<TodoRow> existingOpen = findOpenTodoByNormalizedTitle(connection, sessionId, title);
            if (existingOpen.isPresent()) {
                    TodoRow existing = existingOpen.get();
                    setActiveTodoId(connection, sessionId, existing.todoId(), now);
                    int openCount = countTodosByStatus(connection, sessionId, STATUS_OPEN);
                    connection.commit();
                    return new ToolCommandResult.TodoCreated(
                            dbPath,
                            toTodoItem(existing),
                            false,
                            existing.todoId(),
                            openCount
                    );
            }
            SjResult<Integer> insert = simplyJDBC.executeUpdate(
                    connection,
                    """
                            INSERT INTO todos(todo_id, session_id, title, details, status, created_at_ms, updated_at_ms)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    List.of(todoId, sessionId, title, command.details(), STATUS_OPEN, now, now)
            );
            if (insert.isFailure()) {
                    connection.rollback();
                    return dbFailure("Todo insert failed", insert.error().orElse(null));
            }

                setActiveTodoId(connection, sessionId, todoId, now);
                int openCount = countTodosByStatus(connection, sessionId, STATUS_OPEN);
                connection.commit();
                return new ToolCommandResult.TodoCreated(
                    dbPath,
                    new ToolCommandResult.TodoItem(todoId, sessionId, title, command.details(), STATUS_OPEN, now, now),
                    true,
                    todoId,
                    openCount
            );
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to create todo: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to create todo: " + e.getMessage());
        }
    }

    private ToolCommandResult todoList(ToolCommand.TodoList command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        int limit = command.limit() <= 0 ? DEFAULT_LIST_LIMIT : command.limit();
        String status = command.status() == null ? "" : command.status().trim();
        boolean filterStatus = !status.isEmpty();

        StringBuilder sql = new StringBuilder(
                "SELECT todo_id, session_id, title, details, status, created_at_ms, updated_at_ms FROM todos WHERE session_id = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(command.sessionId().trim());
        if (filterStatus) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY CASE WHEN status = ? THEN 0 ELSE 1 END, updated_at_ms DESC LIMIT ?");
        params.add(STATUS_OPEN);
        params.add(limit + 1);

        try (Connection connection = openConnection()) {
            String sessionId = command.sessionId().trim();
            SjResult<TodoRow> rows = simplyJDBC.query(connection, sql.toString(), params, TodoRow.class);
            if (rows.isFailure()) {
                return dbFailure("Todo list query failed", rows.error().orElse(null));
            }

            boolean truncated = rows.rows().size() > limit;
            int effectiveSize = Math.min(rows.rows().size(), limit);
            List<ToolCommandResult.TodoItem> output = new ArrayList<>(effectiveSize);
            for (int i = 0; i < effectiveSize; i++) {
                output.add(toTodoItem(rows.rows().get(i)));
            }

            int openCount = countTodosByStatus(connection, sessionId, STATUS_OPEN);
            int doneCount = countTodosByStatus(connection, sessionId, STATUS_DONE);
            String activeTodoId = findSession(connection, sessionId).map(SessionRow::activeTodoId).orElse("");

            return new ToolCommandResult.TodoListed(
                    dbPath,
                    output,
                    limit,
                    truncated,
                    filterStatus ? status : null,
                    activeTodoId,
                    openCount,
                    doneCount
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to list todos: " + e.getMessage());
        }
    }

    private ToolCommandResult todoUpdate(ToolCommand.TodoUpdate command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (isBlank(command.todoId())) {
            return new CommonCommandResults.Failure("validation_error", "Missing required argument: todoId");
        }
        if (!command.updateTitle() && !command.updateDetails() && !command.updateStatus()) {
            return new CommonCommandResults.Failure(
                    "validation_error",
                    "At least one update field is required: title, details, or status"
            );
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String sessionId = command.sessionId().trim();
                String todoId = command.todoId().trim();
                Optional<TodoRow> existing = findTodo(connection, sessionId, todoId);
                if (existing.isEmpty()) {
                    connection.rollback();
                    return new CommonCommandResults.Failure("not_found", "Todo not found: " + todoId);
                }

                String previousFocusTodoId = findSession(connection, sessionId)
                        .map(SessionRow::activeTodoId)
                        .orElse("");
                TodoRow current = existing.get();
                String title = command.updateTitle() ? command.title().trim() : current.title();
                String details = command.updateDetails() ? command.details() : current.details();
                String status = command.updateStatus() ? command.status() : current.status();
                long now = Instant.now().toEpochMilli();

                SjResult<Integer> update = simplyJDBC.executeUpdate(
                        connection,
                        """
                                UPDATE todos
                                SET title = ?, details = ?, status = ?, updated_at_ms = ?
                                WHERE session_id = ? AND todo_id = ?
                                """,
                        List.of(title, details, status, now, sessionId, todoId)
                );
                if (update.isFailure()) {
                    connection.rollback();
                    return dbFailure("Todo update failed", update.error().orElse(null));
                }

                String nextActiveTodoId = todoId;
                String action = "updated";
                if (STATUS_DONE.equalsIgnoreCase(status)) {
                    Optional<TodoRow> nextOpen = findMostRecentOpenTodo(connection, sessionId);
                    nextActiveTodoId = nextOpen.map(TodoRow::todoId).orElse("");
                    action = "completed_and_advanced";
                } else if (STATUS_OPEN.equalsIgnoreCase(status) && STATUS_DONE.equalsIgnoreCase(current.status())) {
                    action = "reopened";
                }
                setActiveTodoId(connection, sessionId, nextActiveTodoId, now);
                connection.commit();
                return new ToolCommandResult.TodoUpdated(
                        dbPath,
                        new ToolCommandResult.TodoItem(current.todoId(), current.sessionId(), title, details, status, current.createdAtMs(), now),
                        action,
                        nextActiveTodoId,
                        previousFocusTodoId
                );
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to update todo: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to update todo: " + e.getMessage());
        }
    }

    private ToolCommandResult todoDelete(ToolCommand.TodoDelete command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (isBlank(command.todoId())) {
            return new CommonCommandResults.Failure("validation_error", "Missing required argument: todoId");
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
            String sessionId = command.sessionId().trim();
            String todoId = command.todoId().trim();
            String previousActiveTodoId = findSession(connection, sessionId).map(SessionRow::activeTodoId).orElse("");
            SjResult<Integer> delete = simplyJDBC.executeUpdate(
                    connection,
                    "DELETE FROM todos WHERE session_id = ? AND todo_id = ?",
                    List.of(sessionId, todoId)
            );
            if (delete.isFailure()) {
                    connection.rollback();
                    return dbFailure("Todo delete failed", delete.error().orElse(null));
            }
            int rows = delete.first().orElse(0);
            if (rows == 0) {
                    connection.rollback();
                    return new CommonCommandResults.Failure("not_found", "Todo not found: " + todoId);
            }

                ToolCommandResult.TodoItem nextFocus = null;
                String activeTodoId = previousActiveTodoId;
                if (previousActiveTodoId.equals(todoId)) {
                    Optional<TodoRow> nextOpen = findMostRecentOpenTodo(connection, sessionId);
                    if (nextOpen.isPresent()) {
                        nextFocus = toTodoItem(nextOpen.get());
                        activeTodoId = nextFocus.todoId();
                    } else {
                        activeTodoId = "";
                    }
                    setActiveTodoId(connection, sessionId, activeTodoId, Instant.now().toEpochMilli());
                }
                connection.commit();
                return new ToolCommandResult.TodoDeleted(dbPath, todoId, activeTodoId, nextFocus);
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to delete todo: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to delete todo: " + e.getMessage());
        }
    }

    private ToolCommandResult historyMetaLookup(ToolCommand.HistoryMetaLookup command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        String elementTypeFilter = command.elementTypeFilter() == null ? "" : command.elementTypeFilter().trim().toLowerCase();
        if (!elementTypeFilter.isBlank()
            && !Set.of("system", "user", "assistant", "tool", "summary", "inbound").contains(elementTypeFilter)) {
            return new CommonCommandResults.Failure("validation_error", "Unsupported elementTypeFilter");
        }

        String toolNameFilter = command.toolNameFilter() == null ? "" : command.toolNameFilter().trim();
        int limit = Math.min(Math.max(command.limit(), 1), MAX_HISTORY_META_LIMIT);
        int beforeMessageId = command.beforeMessageId() == null || command.beforeMessageId() <= 0
                ? Integer.MAX_VALUE
                : command.beforeMessageId();

        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                """
                        SELECT session_id, message_id, element_type, content, raw_content, content_truncated,
                               source_tag, tool_call_id, tool_name,
                               input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json,
                               created_at_ms
                        FROM context_messages
                        WHERE session_id = ? AND message_id < ?
                        """
        );
        params.add(command.sessionId().trim());
        params.add(beforeMessageId);
        if (!elementTypeFilter.isBlank()) {
            sql.append(" AND element_type = ?");
            params.add(elementTypeFilter);
        }
        if (!toolNameFilter.isBlank()) {
            sql.append(" AND tool_name = ?");
            params.add(toolNameFilter);
        }
        sql.append(" ORDER BY message_id DESC LIMIT ?");
        params.add(limit + 1);

        try (Connection connection = openConnection()) {
            Optional<SessionRow> session = findSession(connection, command.sessionId().trim());
            if (session.isEmpty()) {
                return new ToolCommandResult.HistoryMetaListed(
                        dbPath, List.of(), limit, false, 0, elementTypeFilter, toolNameFilter, command.includeDropped()
                );
            }
            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));

            SjResult<ContextMessageRow> rows = simplyJDBC.query(connection, sql.toString(), params, ContextMessageRow.class);
            if (rows.isFailure()) {
                return dbFailure("History meta query failed", rows.error().orElse(null));
            }

            List<ToolCommandResult.HistoryMetaItem> items = new ArrayList<>(limit + 1);
            for (ContextMessageRow row : rows.rows()) {
                boolean droppedFlag = dropped.contains(row.messageId());
                if (!command.includeDropped() && droppedFlag) {
                    continue;
                }
                items.add(new ToolCommandResult.HistoryMetaItem(
                        row.messageId(),
                        row.elementType(),
                        row.toolCallId(),
                        row.toolName(),
                        payloadField(row.content(), "status"),
                        payloadField(row.content(), "code"),
                        compactPreview(row.content(), PREVIEW_MAX_CHARS),
                        row.createdAtMs(),
                        droppedFlag
                ));
            }

            boolean truncated = items.size() > limit;
            int nextBeforeMessageId = 0;
            if (truncated) {
                ToolCommandResult.HistoryMetaItem removed = items.remove(limit);
                nextBeforeMessageId = removed.messageId();
            }
            return new ToolCommandResult.HistoryMetaListed(
                    dbPath,
                    items,
                    limit,
                    truncated,
                    nextBeforeMessageId,
                    elementTypeFilter,
                    toolNameFilter,
                    command.includeDropped()
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to load history meta: " + e.getMessage());
        }
    }

    private ToolCommandResult historyRawLookup(ToolCommand.HistoryRawLookup command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (command.messageId() <= 0) {
            return new CommonCommandResults.Failure("validation_error", "messageId must be > 0");
        }

        int startChar = Math.max(0, command.startChar());
        int maxChars = Math.min(Math.max(command.maxChars(), 1), MAX_HISTORY_RAW_CHARS);

        try (Connection connection = openConnection()) {
            Optional<SessionRow> session = findSession(connection, command.sessionId().trim());
            if (session.isEmpty()) {
                return new CommonCommandResults.Failure("not_found", "Session not found");
            }

            SjResult<ContextMessageRow> rows = simplyJDBC.query(
                    connection,
                    """
                            SELECT session_id, message_id, element_type, content, raw_content, content_truncated,
                                   source_tag, tool_call_id, tool_name,
                                   input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json,
                                   created_at_ms
                            FROM context_messages
                            WHERE session_id = ? AND message_id = ?
                            LIMIT 1
                            """,
                    List.of(command.sessionId().trim(), command.messageId()),
                    ContextMessageRow.class
            );
            if (rows.isFailure()) {
                return dbFailure("History raw lookup query failed", rows.error().orElse(null));
            }
            if (rows.rows().isEmpty()) {
                return new CommonCommandResults.Failure("not_found", "Message not found");
            }

            ContextMessageRow row = rows.rows().getFirst();
            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));
            boolean droppedFlag = dropped.contains(row.messageId());
            String raw = "tool".equals(row.elementType()) ? row.rawContent() : row.content();
            if (raw == null) {
                raw = "";
            }
            int totalChars = raw.length();
            int safeStart = Math.min(startChar, totalChars);
            int endExclusive = Math.min(totalChars, safeStart + maxChars);
            String slice = raw.substring(safeStart, endExclusive);

            return new ToolCommandResult.HistoryRawLoaded(
                    dbPath,
                    row.messageId(),
                    row.elementType(),
                    row.toolCallId(),
                    row.toolName(),
                    slice,
                    safeStart,
                    slice.length(),
                    totalChars,
                    endExclusive < totalChars,
                    droppedFlag,
                    row.createdAtMs()
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to load history raw: " + e.getMessage());
        }
    }

    private SessionContextResult initializeSession(SessionContextCommand.InitializeSession command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<SessionRow> existing = findSession(connection, command.sessionId().trim());
                if (existing.isPresent()) {
                    connection.commit();
                    return new CommonCommandResults.Success("session already initialized");
                }

                long now = Instant.now().toEpochMilli();
                String dropped = "[]";
                int nextMessageId = 0;
                SjResult<Integer> insertSession = simplyJDBC.executeUpdate(
                        connection,
                        """
                                INSERT INTO sessions(
                                    session_id, agent_id, alias, sys_prompt_amount, next_message_id,
                                    dropped_message_ids_json, active_todo_id, created_at_ms, updated_at_ms
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        List.of(
                                command.sessionId().trim(),
                                command.agentId().trim(),
                                command.alias().trim(),
                                Math.max(command.sysPromptAmount(), 0),
                                nextMessageId,
                                dropped,
                                "",
                                now,
                                now
                        )
                );
                if (insertSession.isFailure()) {
                    connection.rollback();
                    return dbFailure("Session insert failed", insertSession.error().orElse(null));
                }

                nextMessageId = appendMessagesInternal(connection, command.sessionId().trim(), nextMessageId, command.initialContext());
                SjResult<Integer> updateSession = simplyJDBC.executeUpdate(
                        connection,
                        "UPDATE sessions SET next_message_id = ?, updated_at_ms = ? WHERE session_id = ?",
                        List.of(nextMessageId, now, command.sessionId().trim())
                );
                if (updateSession.isFailure()) {
                    connection.rollback();
                    return dbFailure("Session next id update failed", updateSession.error().orElse(null));
                }

                connection.commit();
                return new CommonCommandResults.Success("session initialized");
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to initialize session context: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to initialize session context: " + e.getMessage());
        }
    }

    private SessionContextResult appendMessages(SessionContextCommand.AppendMessages command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (command.messages().isEmpty()) {
            return new CommonCommandResults.Success("no messages appended");
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                SessionRow session = findSession(connection, command.sessionId().trim())
                        .orElseGet(() -> createSessionShell(connection, command.sessionId().trim()));
                int nextMessageId = appendMessagesInternal(connection, command.sessionId().trim(), session.nextMessageId(), command.messages());
                long now = Instant.now().toEpochMilli();

                SjResult<Integer> updateSession = simplyJDBC.executeUpdate(
                        connection,
                        "UPDATE sessions SET next_message_id = ?, updated_at_ms = ? WHERE session_id = ?",
                        List.of(nextMessageId, now, command.sessionId().trim())
                );
                if (updateSession.isFailure()) {
                    connection.rollback();
                    return dbFailure("Session next id update failed", updateSession.error().orElse(null));
                }

                connection.commit();
                return new CommonCommandResults.Success("messages appended");
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to append context messages: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to append context messages: " + e.getMessage());
        }
    }

    private SessionContextResult replaceActiveContext(SessionContextCommand.ReplaceActiveContext command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                SessionRow session = findSession(connection, command.sessionId().trim())
                        .orElseGet(() -> createSessionShell(connection, command.sessionId().trim()));

                LinkedHashSet<Integer> droppedIds = new LinkedHashSet<>(parseDroppedIds(session.droppedMessageIdsJson()));
                List<Integer> allIds = listAllMessageIds(connection, command.sessionId().trim());
                List<Integer> activeIds = allIds.stream().filter(id -> !droppedIds.contains(id)).toList();

                int firstReplacementMessageId = session.nextMessageId();
                int nextMessageId = appendMessagesInternal(
                        connection,
                        command.sessionId().trim(),
                        firstReplacementMessageId,
                        command.replacement()
                );
                droppedIds.addAll(activeIds);
                long now = Instant.now().toEpochMilli();

                SjResult<Integer> updateSession = simplyJDBC.executeUpdate(
                        connection,
                        """
                                UPDATE sessions
                                SET next_message_id = ?, dropped_message_ids_json = ?, sys_prompt_amount = ?, updated_at_ms = ?
                                WHERE session_id = ?
                                """,
                        List.of(
                                nextMessageId,
                                writeDroppedIds(droppedIds),
                                command.sysPromptAmount(),
                                now,
                                command.sessionId().trim()
                        )
                );
                if (updateSession.isFailure()) {
                    connection.rollback();
                    return dbFailure("Session replace update failed", updateSession.error().orElse(null));
                }

                connection.commit();
                return new CommonCommandResults.Success("context replaced");
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to replace active context: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to replace active context: " + e.getMessage());
        }
    }

    private SessionContextResult upsertStateSystemMessage(SessionContextCommand.UpsertStateSystemMessage command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }
        if (isBlank(command.stateJson())) {
            return new CommonCommandResults.Failure("validation_error", "State message content is required");
        }

        try (Connection connection = openConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                String sessionId = command.sessionId().trim();
                SessionRow session = findSession(connection, sessionId)
                        .orElseGet(() -> createSessionShell(connection, sessionId));
                LinkedHashSet<Integer> droppedIds = new LinkedHashSet<>(parseDroppedIds(session.droppedMessageIdsJson()));
                long now = Instant.now().toEpochMilli();

                List<Integer> activeStateIds = listActiveStateSystemMessageIds(connection, sessionId, droppedIds);
                if (!activeStateIds.isEmpty()) {
                    int newestId = activeStateIds.getFirst();
                    SjResult<Integer> updateMessage = simplyJDBC.executeUpdate(
                            connection,
                            """
                                    UPDATE context_messages
                                    SET content = ?, raw_content = ?, content_truncated = 0, created_at_ms = ?
                                    WHERE session_id = ? AND message_id = ?
                                    """,
                            List.of(command.stateJson(), command.stateJson(), now, sessionId, newestId)
                    );
                    if (updateMessage.isFailure()) {
                        connection.rollback();
                        return dbFailure("State message update failed", updateMessage.error().orElse(null));
                    }
                    if (activeStateIds.size() > 1) {
                        for (int i = 1; i < activeStateIds.size(); i++) {
                            droppedIds.add(activeStateIds.get(i));
                        }
                    }
                    SjResult<Integer> updateSession = simplyJDBC.executeUpdate(
                            connection,
                            """
                                    UPDATE sessions
                                    SET dropped_message_ids_json = ?, updated_at_ms = ?
                                    WHERE session_id = ?
                                    """,
                            List.of(writeDroppedIds(droppedIds), now, sessionId)
                    );
                    if (updateSession.isFailure()) {
                        connection.rollback();
                        return dbFailure("Session update failed after state upsert", updateSession.error().orElse(null));
                    }
                    connection.commit();
                    return new CommonCommandResults.Success("state message updated");
                }

                int nextMessageId = appendMessagesInternal(
                        connection,
                        sessionId,
                        session.nextMessageId(),
                        List.of(new ContextElement.SystemStateMsg(command.stateJson()))
                );
                SjResult<Integer> updateSession = simplyJDBC.executeUpdate(
                        connection,
                        """
                                UPDATE sessions
                                SET next_message_id = ?, updated_at_ms = ?
                                WHERE session_id = ?
                                """,
                        List.of(nextMessageId, now, sessionId)
                );
                if (updateSession.isFailure()) {
                    connection.rollback();
                    return dbFailure("Session update failed after state insert", updateSession.error().orElse(null));
                }
                connection.commit();
                return new CommonCommandResults.Success("state message inserted");
            } catch (Exception e) {
                connection.rollback();
                return new CommonCommandResults.Failure("db_error", "Failed to upsert state message: " + e.getMessage());
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to upsert state message: " + e.getMessage());
        }
    }

    private SessionContextResult loadActiveContext(SessionContextCommand.LoadActiveContext command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        try (Connection connection = openConnection()) {
            Optional<SessionRow> session = findSession(connection, command.sessionId().trim());
            if (session.isEmpty()) {
                return new SessionContextResult.ActiveContextLoaded(List.of(), 0, 0, List.of());
            }

            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));
            SjResult<ContextMessageRow> result = simplyJDBC.query(
                    connection,
                    """
                            SELECT session_id, message_id, element_type, content, source_tag, tool_call_id, tool_name,
                                   raw_content, content_truncated,
                                   input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json,
                                   created_at_ms
                            FROM context_messages
                            WHERE session_id = ?
                            ORDER BY message_id
                            """,
                    List.of(command.sessionId().trim()),
                    ContextMessageRow.class
            );
            if (result.isFailure()) {
                return dbFailure("Context load failed", result.error().orElse(null));
            }

            List<ContextElement> active = new ArrayList<>();
            for (ContextMessageRow row : result.rows()) {
                if (dropped.contains(row.messageId())) {
                    continue;
                }
                active.add(toContextElement(row));
            }

            List<Integer> droppedList = dropped.stream().sorted().toList();
            return new SessionContextResult.ActiveContextLoaded(
                    active,
                    session.get().sysPromptAmount(),
                    session.get().nextMessageId(),
                    droppedList
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to load active context: " + e.getMessage());
        }
    }

    private SessionContextResult getMessageById(SessionContextCommand.GetMessageById command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        try (Connection connection = openConnection()) {
            Optional<SessionRow> session = findSession(connection, command.sessionId().trim());
            if (session.isEmpty()) {
                return new CommonCommandResults.Failure("not_found", "Session not found: " + command.sessionId().trim());
            }

            SjResult<ContextMessageRow> query = simplyJDBC.query(
                    connection,
                    """
                            SELECT session_id, message_id, element_type, content, source_tag, tool_call_id, tool_name,
                                   raw_content, content_truncated,
                                   input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json,
                                   created_at_ms
                            FROM context_messages
                            WHERE session_id = ? AND message_id = ?
                            LIMIT 1
                            """,
                    List.of(command.sessionId().trim(), command.messageId()),
                    ContextMessageRow.class
            );
            if (query.isFailure()) {
                return dbFailure("Message lookup failed", query.error().orElse(null));
            }
            if (query.rows().isEmpty()) {
                return new CommonCommandResults.Failure(
                        "not_found",
                        "Message not found: " + command.sessionId().trim() + "/" + command.messageId()
                );
            }

            ContextMessageRow row = query.rows().getFirst();
            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));
            return new SessionContextResult.ContextMessageLoaded(
                    row.messageId(),
                    toContextElement(row),
                    dropped.contains(row.messageId())
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to lookup context message: " + e.getMessage());
        }
    }

    private SessionContextResult loadCompactionState(SessionContextCommand.LoadCompactionState command) {
        if (isBlank(command.sessionId())) {
            return new CommonCommandResults.Failure("validation_error", "Invalid session id");
        }

        String sessionId = command.sessionId().trim();
        int toolScanLimit = Math.min(Math.max(command.toolScanLimit(), 1), MAX_COMPACTION_TOOL_SCAN_LIMIT);
        int todoLimit = Math.min(Math.max(command.todoLimit(), 1), MAX_COMPACTION_TODO_LIMIT);

        try (Connection connection = openConnection()) {
            Optional<SessionRow> session = findSession(connection, sessionId);
            if (session.isEmpty()) {
                return new SessionContextResult.CompactionStateLoaded(List.of(), List.of(), 0, "");
            }
            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));

            SjResult<CompactionToolRow> toolRows = simplyJDBC.query(
                    connection,
                    """
                            SELECT message_id, tool_call_id, tool_name, content, raw_content, content_truncated, created_at_ms
                            FROM context_messages
                            WHERE session_id = ? AND element_type = 'tool'
                            ORDER BY message_id DESC
                            LIMIT ?
                            """,
                    List.of(sessionId, toolScanLimit),
                    CompactionToolRow.class
            );
            if (toolRows.isFailure()) {
                return dbFailure("Compaction tool-state query failed", toolRows.error().orElse(null));
            }

            SjResult<TodoRow> todoRows = simplyJDBC.query(
                    connection,
                    """
                            SELECT todo_id, session_id, title, details, status, created_at_ms, updated_at_ms
                            FROM todos
                            WHERE session_id = ?
                            ORDER BY CASE WHEN status = ? THEN 0 ELSE 1 END, updated_at_ms DESC
                            LIMIT ?
                            """,
                    List.of(sessionId, STATUS_OPEN, todoLimit),
                    TodoRow.class
            );
            if (todoRows.isFailure()) {
                return dbFailure("Compaction todo-state query failed", todoRows.error().orElse(null));
            }

            SjResult<CountRow> openTodoCountRows = simplyJDBC.query(
                    connection,
                    "SELECT COUNT(*) AS count FROM todos WHERE session_id = ? AND status = ?",
                    List.of(sessionId, STATUS_OPEN),
                    CountRow.class
            );
            if (openTodoCountRows.isFailure()) {
                return dbFailure("Compaction open todo count query failed", openTodoCountRows.error().orElse(null));
            }

            List<SessionContextResult.CompactionToolMessage> tools = toolRows.rows().stream()
                    .filter(row -> !dropped.contains(row.messageId()))
                    .limit(toolScanLimit)
                    .map(row -> new SessionContextResult.CompactionToolMessage(
                            row.messageId(),
                            row.toolCallId(),
                            row.toolName(),
                            row.content(),
                            row.rawContent(),
                            row.contentTruncated(),
                            row.createdAtMs()
                    ))
                    .toList();
            List<SessionContextResult.CompactionTodoItem> todos = todoRows.rows().stream()
                    .map(row -> new SessionContextResult.CompactionTodoItem(
                            row.todoId(),
                            row.title(),
                            row.details(),
                            row.status(),
                            row.createdAtMs(),
                            row.updatedAtMs()
                    ))
                    .toList();
            int openTodoCount = openTodoCountRows.first().map(CountRow::count).orElse(0);
            return new SessionContextResult.CompactionStateLoaded(
                    tools,
                    todos,
                    openTodoCount,
                    session.get().activeTodoId()
            );
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to load compaction state: " + e.getMessage());
        }
    }

    private int appendMessagesInternal(
            Connection connection,
            String sessionId,
            int startMessageId,
            List<ContextElement> messages
    ) {
        int next = Math.max(startMessageId, 0);
        long now = Instant.now().toEpochMilli();
        for (ContextElement message : messages) {
            StoredContextMessage stored = StoredContextMessage.from(sessionId, next, message, now);
            SjResult<Integer> insert = simplyJDBC.executeUpdate(
                    connection,
                    """
                            INSERT INTO context_messages(
                                session_id, message_id, element_type, content, raw_content, content_truncated,
                                source_tag, tool_call_id, tool_name,
                                input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json, created_at_ms
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    List.of(
                            stored.sessionId(),
                            stored.messageId(),
                            stored.elementType(),
                            stored.content(),
                            stored.rawContent(),
                            stored.contentTruncated() ? 1 : 0,
                            stored.sourceTag(),
                            stored.toolCallId(),
                            stored.toolName(),
                            stored.inputDomain(),
                            stored.inputKind(),
                            stored.sourceId(),
                            stored.correlationId(),
                            stored.metadataJson(),
                            stored.toolCallsJson(),
                            stored.createdAtMs()
                    )
            );
            if (insert.isFailure()) {
                throw new IllegalStateException("Context insert failed: " + errorMessage(insert.error().orElse(null)));
            }
            next++;
        }
        return next;
    }

    private Optional<TodoRow> findTodo(Connection connection, String sessionId, String todoId) {
        SjResult<TodoRow> result = simplyJDBC.query(
                connection,
                """
                        SELECT todo_id, session_id, title, details, status, created_at_ms, updated_at_ms
                        FROM todos
                        WHERE session_id = ? AND todo_id = ?
                        LIMIT 1
                        """,
                List.of(sessionId, todoId),
                TodoRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Todo lookup failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.first();
    }

    private Optional<TodoRow> findOpenTodoByNormalizedTitle(Connection connection, String sessionId, String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        SjResult<TodoRow> result = simplyJDBC.query(
                connection,
                """
                        SELECT todo_id, session_id, title, details, status, created_at_ms, updated_at_ms
                        FROM todos
                        WHERE session_id = ? AND status = ?
                          AND lower(trim(title)) = lower(trim(?))
                        ORDER BY updated_at_ms DESC
                        LIMIT 1
                        """,
                List.of(sessionId, STATUS_OPEN, title),
                TodoRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Todo dedup lookup failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.first();
    }

    private Optional<TodoRow> findMostRecentOpenTodo(Connection connection, String sessionId) {
        SjResult<TodoRow> result = simplyJDBC.query(
                connection,
                """
                        SELECT todo_id, session_id, title, details, status, created_at_ms, updated_at_ms
                        FROM todos
                        WHERE session_id = ? AND status = ?
                        ORDER BY updated_at_ms DESC
                        LIMIT 1
                        """,
                List.of(sessionId, STATUS_OPEN),
                TodoRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Open todo focus lookup failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.first();
    }

    private int countTodosByStatus(Connection connection, String sessionId, String status) {
        SjResult<CountRow> result = simplyJDBC.query(
                connection,
                "SELECT COUNT(*) AS count FROM todos WHERE session_id = ? AND status = ?",
                List.of(sessionId, status),
                CountRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Todo count lookup failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.first().map(CountRow::count).orElse(0);
    }

    private void setActiveTodoId(Connection connection, String sessionId, String activeTodoId, long now) {
        SjResult<Integer> update = simplyJDBC.executeUpdate(
                connection,
                "UPDATE sessions SET active_todo_id = ?, updated_at_ms = ? WHERE session_id = ?",
                List.of(activeTodoId == null ? "" : activeTodoId, now, sessionId)
        );
        if (update.isFailure()) {
            throw new IllegalStateException("Failed to update active todo focus: " + errorMessage(update.error().orElse(null)));
        }
    }

    private Optional<SessionRow> findSession(Connection connection, String sessionId) {
        SjResult<SessionRow> result = simplyJDBC.query(
                connection,
                """
                        SELECT session_id, agent_id, alias, sys_prompt_amount, next_message_id, dropped_message_ids_json, active_todo_id,
                               created_at_ms, updated_at_ms
                        FROM sessions
                        WHERE session_id = ?
                        LIMIT 1
                        """,
                List.of(sessionId),
                SessionRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Session lookup failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.first();
    }

    private SessionRow createSessionShell(Connection connection, String sessionId) {
        long now = Instant.now().toEpochMilli();
        SjResult<Integer> insert = simplyJDBC.executeUpdate(
                connection,
                """
                        INSERT INTO sessions(
                            session_id, agent_id, alias, sys_prompt_amount, next_message_id,
                            dropped_message_ids_json, active_todo_id, created_at_ms, updated_at_ms
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                List.of(sessionId, "", "", 0, 0, "[]", "", now, now)
        );
        if (insert.isFailure()) {
            throw new IllegalStateException("Session shell insert failed: " + errorMessage(insert.error().orElse(null)));
        }
        return new SessionRow(sessionId, "", "", 0, 0, "[]", "", now, now);
    }

    private List<Integer> listAllMessageIds(Connection connection, String sessionId) {
        SjResult<MessageIdRow> result = simplyJDBC.query(
                connection,
                "SELECT message_id FROM context_messages WHERE session_id = ? ORDER BY message_id",
                List.of(sessionId),
                MessageIdRow.class
        );
        if (result.isFailure()) {
            throw new IllegalStateException("Message id listing failed: " + errorMessage(result.error().orElse(null)));
        }
        return result.rows().stream().map(MessageIdRow::messageId).toList();
    }

    private List<Integer> listActiveStateSystemMessageIds(
            Connection connection,
            String sessionId,
            Set<Integer> droppedIds
    ) {
        SjResult<StateSystemRow> rows = simplyJDBC.query(
                connection,
                """
                        SELECT message_id
                        FROM context_messages
                        WHERE session_id = ? AND element_type = 'system_state'
                        ORDER BY message_id DESC
                        """,
                List.of(sessionId),
                StateSystemRow.class
        );
        if (rows.isFailure()) {
            throw new IllegalStateException("State-system message lookup failed: " + errorMessage(rows.error().orElse(null)));
        }

        List<Integer> active = new ArrayList<>();
        Set<Integer> dropped = droppedIds == null ? Set.of() : Set.copyOf(droppedIds);
        for (StateSystemRow row : rows.rows()) {
            if (dropped.contains(row.messageId())) {
                continue;
            }
            active.add(row.messageId());
        }
        return active;
    }

    private ToolCommandResult.TodoItem toTodoItem(TodoRow row) {
        return new ToolCommandResult.TodoItem(
                row.todoId(),
                row.sessionId(),
                row.title(),
                row.details(),
                row.status(),
                row.createdAtMs(),
                row.updatedAtMs()
        );
    }

    private String payloadField(String payload, String field) {
        if (isBlank(payload) || isBlank(field)) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(payload);
            return node.path(field).asText("");
        } catch (Exception ignored) {
            return "";
        }
    }

    private String compactPreview(String content, int maxChars) {
        String safe = content == null ? "" : content.replace('\n', ' ').replace('\r', ' ').trim();
        int capped = Math.max(16, maxChars);
        if (safe.length() <= capped) {
            return safe;
        }
        if (capped <= 3) {
            return safe.substring(0, capped);
        }
        return safe.substring(0, capped - 3) + "...";
    }

    private ContextElement toContextElement(ContextMessageRow row) {
        return switch (row.elementType()) {
            case "system_core" -> new ContextElement.SystemCoreMsg(row.content());
            case "system_agent" -> new ContextElement.SystemAgentMsg(row.content());
            case "system_task" -> new ContextElement.SystemTaskMsg(row.content());
            case "system_state" -> new ContextElement.SystemStateMsg(row.content());
            case "user" -> new ContextElement.UserMsg(row.content());
            case "assistant" -> new ContextElement.AssistantMsg(row.content(), readToolCalls(row.toolCallsJson()));
            case "tool" -> new ContextElement.ToolMsg(
                    row.toolCallId(),
                    row.toolName(),
                    row.content(),
                    row.rawContent(),
                    row.contentTruncatedInt() != 0
            );
            case "summary" -> new ContextElement.SummaryMsg(row.content(), row.sourceTag());
            case "inbound" -> new ContextElement.InboundMsg(
                    row.inputDomain(),
                    row.inputKind(),
                    row.sourceId(),
                    row.content(),
                    row.correlationId(),
                    readMetadata(row.metadataJson())
            );
            default -> new ContextElement.UserMsg(row.content());
        };
    }

    private List<ContextElement.ToolCall> readToolCalls(String json) {
        if (isBlank(json)) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<ContextElement.ToolCall>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private java.util.Map<String, String> readMetadata(String json) {
        if (isBlank(json)) {
            return java.util.Map.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<java.util.Map<String, String>>() {
            });
        } catch (Exception ignored) {
            return java.util.Map.of();
        }
    }

    private List<Integer> parseDroppedIds(String droppedIdsJson) {
        if (isBlank(droppedIdsJson)) {
            return List.of();
        }
        try {
            List<Integer> ids = MAPPER.readValue(droppedIdsJson, new TypeReference<List<Integer>>() {
            });
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<Integer> deduped = new LinkedHashSet<>();
            for (Integer id : ids) {
                if (id != null && id >= 0) {
                    deduped.add(id);
                }
            }
            return List.copyOf(deduped);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeDroppedIds(Set<Integer> droppedIds) {
        try {
            return MAPPER.writeValueAsString(droppedIds == null ? List.of() : droppedIds.stream().sorted().toList());
        } catch (Exception ignored) {
            return "[]";
        }
    }

    private CommonCommandResults.Failure dbFailure(String message, Throwable throwable) {
        return new CommonCommandResults.Failure("db_error", message + ": " + errorMessage(throwable));
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return "unknown database error";
        }
        return throwable.getMessage();
    }

    public synchronized void initializeSchema() throws Exception {
        if (schemaInitialized) {
            return;
        }
        try (Connection connection = openConnectionInternal()) {
            ensureSchema(connection);
            schemaInitialized = true;
        }
    }

    private Connection openConnection() throws Exception {
        if (!schemaInitialized) {
            initializeSchema();
        }
        return openConnectionInternal();
    }

    private Connection openConnectionInternal() throws Exception {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return sqliteDataSource(dbPath).getConnection();
    }

    private void ensureSchema(Connection connection) {
        executeSchemaUpdate(connection,
                """
                        CREATE TABLE IF NOT EXISTS todos(
                            todo_id TEXT PRIMARY KEY,
                            session_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            details TEXT NOT NULL DEFAULT '',
                            status TEXT NOT NULL CHECK(status IN ('open', 'done')),
                            created_at_ms INTEGER NOT NULL,
                            updated_at_ms INTEGER NOT NULL
                        )
                        """);
        executeSchemaUpdate(connection,
                "CREATE INDEX IF NOT EXISTS idx_todos_session_updated ON todos(session_id, updated_at_ms DESC)");

        executeSchemaUpdate(connection,
                """
                        CREATE TABLE IF NOT EXISTS sessions(
                            session_id TEXT PRIMARY KEY,
                            agent_id TEXT NOT NULL,
                            alias TEXT NOT NULL,
                            sys_prompt_amount INTEGER NOT NULL,
                            next_message_id INTEGER NOT NULL,
                            dropped_message_ids_json TEXT NOT NULL DEFAULT '[]',
                            active_todo_id TEXT NOT NULL DEFAULT '',
                            created_at_ms INTEGER NOT NULL,
                            updated_at_ms INTEGER NOT NULL
                        )
                        """);
        ensureColumnExists(
                connection,
                "sessions",
                "active_todo_id",
                "ALTER TABLE sessions ADD COLUMN active_todo_id TEXT NOT NULL DEFAULT ''"
        );

        executeSchemaUpdate(connection,
                """
                        CREATE TABLE IF NOT EXISTS context_messages(
                            session_id TEXT NOT NULL,
                            message_id INTEGER NOT NULL,
                            element_type TEXT NOT NULL,
                            content TEXT NOT NULL,
                            raw_content TEXT NOT NULL DEFAULT '',
                            content_truncated INTEGER NOT NULL DEFAULT 0,
                            source_tag TEXT NOT NULL DEFAULT '',
                            tool_call_id TEXT NOT NULL DEFAULT '',
                            tool_name TEXT NOT NULL DEFAULT '',
                            input_domain TEXT NOT NULL DEFAULT '',
                            input_kind TEXT NOT NULL DEFAULT '',
                            source_id TEXT NOT NULL DEFAULT '',
                            correlation_id TEXT NOT NULL DEFAULT '',
                            metadata_json TEXT NOT NULL DEFAULT '{}',
                            tool_calls_json TEXT NOT NULL DEFAULT '[]',
                            created_at_ms INTEGER NOT NULL,
                            PRIMARY KEY (session_id, message_id)
                        )
                        """);
        ensureColumnExists(
                connection,
                "context_messages",
                "raw_content",
                "ALTER TABLE context_messages ADD COLUMN raw_content TEXT NOT NULL DEFAULT ''"
        );
        ensureColumnExists(
                connection,
                "context_messages",
                "content_truncated",
                "ALTER TABLE context_messages ADD COLUMN content_truncated INTEGER NOT NULL DEFAULT 0"
        );
        executeSchemaUpdate(connection,
                "CREATE INDEX IF NOT EXISTS idx_context_messages_session ON context_messages(session_id, message_id)");
        executeSchemaUpdate(connection, "DROP TABLE IF EXISTS compaction_snapshots");
    }

    private void executeSchemaUpdate(Connection connection, String sql) {
        SjResult<Integer> result = simplyJDBC.executeUpdate(connection, sql, SimplyJDBC.NO_PARAMS);
        if (result.isFailure()) {
            throw new IllegalStateException("Schema update failed: " + sql + " -> " + errorMessage(result.error().orElse(null)));
        }
    }

    private void ensureColumnExists(Connection connection, String tableName, String columnName, String alterSql) {
        if (columnExists(connection, tableName, columnName)) {
            return;
        }
        executeSchemaUpdate(connection, alterSql);
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) {
        String safeTable = tableName == null ? "" : tableName.trim();
        String safeColumn = columnName == null ? "" : columnName.trim();
        if (safeTable.isEmpty() || safeColumn.isEmpty()) {
            return false;
        }
        SjResult<TableInfoRow> result = simplyJDBC.query(
                connection,
                "PRAGMA table_info(" + safeTable + ")",
                SimplyJDBC.NO_PARAMS,
                TableInfoRow.class
        );
        if (result.isFailure()) {
            return false;
        }
        return result.rows().stream().anyMatch(row -> safeColumn.equalsIgnoreCase(row.name()));
    }

    private SQLiteDataSource sqliteDataSource(Path dbFilePath) {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + dbFilePath.toAbsolutePath().normalize());
        return dataSource;
    }

    private Path resolveDbPath(Path root, Path overrideDbPathOrNull) {
        Path base = overrideDbPathOrNull == null || overrideDbPathOrNull.toString().isBlank()
                ? Path.of(".magenta", "state.db")
                : overrideDbPathOrNull;
        if (!base.isAbsolute()) {
            base = root.resolve(base);
        }
        return base.toAbsolutePath().normalize();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record StoredContextMessage(
            String sessionId,
            int messageId,
            String elementType,
            String content,
            String rawContent,
            boolean contentTruncated,
            String sourceTag,
            String toolCallId,
            String toolName,
            String inputDomain,
            String inputKind,
            String sourceId,
            String correlationId,
            String metadataJson,
            String toolCallsJson,
            long createdAtMs
    ) {
        static StoredContextMessage from(String sessionId, int messageId, ContextElement message, long createdAtMs) {
            String elementType = switch (message) {
                case ContextElement.SystemCoreMsg ignored -> "system_core";
                case ContextElement.SystemAgentMsg ignored -> "system_agent";
                case ContextElement.SystemTaskMsg ignored -> "system_task";
                case ContextElement.SystemStateMsg ignored -> "system_state";
                case ContextElement.UserMsg ignored -> "user";
                case ContextElement.AssistantMsg ignored -> "assistant";
                case ContextElement.ToolMsg ignored -> "tool";
                case ContextElement.SummaryMsg ignored -> "summary";
                case ContextElement.InboundMsg ignored -> "inbound";
            };

            String sourceTag = "";
            String toolCallId = "";
            String toolName = "";
            String rawContent = message.content();
            boolean contentTruncated = false;
            String inputDomain = "";
            String inputKind = "";
            String sourceId = "";
            String correlationId = "";
            String metadataJson = "{}";
            String toolCallsJson = "[]";

            switch (message) {
                case ContextElement.AssistantMsg assistant -> toolCallsJson = toJson(assistant.toolCalls(), "[]");
                case ContextElement.ToolMsg tool -> {
                    toolCallId = tool.toolCallId();
                    toolName = tool.toolName();
                    rawContent = tool.rawContent();
                    contentTruncated = tool.contentTruncated();
                }
                case ContextElement.SummaryMsg summary -> sourceTag = summary.sourceTag();
                case ContextElement.InboundMsg inbound -> {
                    inputDomain = inbound.inputDomain();
                    inputKind = inbound.inputKind();
                    sourceId = inbound.sourceId();
                    correlationId = inbound.correlationId();
                    metadataJson = toJson(inbound.metadata(), "{}");
                }
                default -> {
                }
            }

            return new StoredContextMessage(
                    sessionId,
                    messageId,
                    elementType,
                    message.content(),
                    rawContent,
                    contentTruncated,
                    sourceTag,
                    toolCallId,
                    toolName,
                    inputDomain,
                    inputKind,
                    sourceId,
                    correlationId,
                    metadataJson,
                    toolCallsJson,
                    createdAtMs
            );
        }

        private static String toJson(Object value, String fallback) {
            try {
                return MAPPER.writeValueAsString(value);
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }

    private record MessageIdRow(@SjColumn("message_id") Integer messageId) {
    }

    private record StateSystemRow(@SjColumn("message_id") int messageId) {
    }

    private record TodoRow(
            @SjColumn("todo_id") String todoId,
            @SjColumn("session_id") String sessionId,
            @SjColumn("title") String title,
            @SjColumn("details") String details,
            @SjColumn("status") String status,
            @SjColumn("created_at_ms") long createdAtMs,
            @SjColumn("updated_at_ms") long updatedAtMs
    ) {
    }

    private record CompactionToolRow(
            @SjColumn("message_id") int messageId,
            @SjColumn("tool_call_id") String toolCallId,
            @SjColumn("tool_name") String toolName,
            @SjColumn("content") String content,
            @SjColumn("raw_content") String rawContent,
            @SjColumn("content_truncated") int contentTruncatedInt,
            @SjColumn("created_at_ms") long createdAtMs
    ) {
        boolean contentTruncated() {
            return contentTruncatedInt != 0;
        }
    }

    private record CountRow(@SjColumn("count") int count) {
    }

    private record TableInfoRow(@SjColumn("name") String name) {
    }

    private record SessionRow(
            @SjColumn("session_id") String sessionId,
            @SjColumn("agent_id") String agentId,
            @SjColumn("alias") String alias,
            @SjColumn("sys_prompt_amount") int sysPromptAmount,
            @SjColumn("next_message_id") int nextMessageId,
            @SjColumn("dropped_message_ids_json") String droppedMessageIdsJson,
            @SjColumn("active_todo_id") String activeTodoId,
            @SjColumn("created_at_ms") long createdAtMs,
            @SjColumn("updated_at_ms") long updatedAtMs
    ) {
    }

    private record ContextMessageRow(
            @SjColumn("session_id") String sessionId,
            @SjColumn("message_id") int messageId,
            @SjColumn("element_type") String elementType,
            @SjColumn("content") String content,
            @SjColumn("raw_content") String rawContent,
            @SjColumn("content_truncated") int contentTruncatedInt,
            @SjColumn("source_tag") String sourceTag,
            @SjColumn("tool_call_id") String toolCallId,
            @SjColumn("tool_name") String toolName,
            @SjColumn("input_domain") String inputDomain,
            @SjColumn("input_kind") String inputKind,
            @SjColumn("source_id") String sourceId,
            @SjColumn("correlation_id") String correlationId,
            @SjColumn("metadata_json") String metadataJson,
            @SjColumn("tool_calls_json") String toolCallsJson,
            @SjColumn("created_at_ms") long createdAtMs
    ) {
    }
}
