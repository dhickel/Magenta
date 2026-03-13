package io.mindspice.magenta.runtime.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
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
    private static final String STATUS_OPEN = "open";

    private final Path workspaceRoot;
    private final Path dbPath;
    private final SimplyJDBC simplyJDBC = new SimplyJDBC();

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

        long now = Instant.now().toEpochMilli();
        String todoId = UUID.randomUUID().toString();

        try (Connection connection = openConnection()) {
            SjResult<Integer> insert = simplyJDBC.executeUpdate(
                    connection,
                    """
                            INSERT INTO todos(todo_id, session_id, title, details, status, created_at_ms, updated_at_ms)
                            VALUES (?, ?, ?, ?, ?, ?, ?)
                            """,
                    List.of(todoId, command.sessionId().trim(), command.title().trim(), command.details(), STATUS_OPEN, now, now)
            );
            if (insert.isFailure()) {
                return dbFailure("Todo insert failed", insert.error().orElse(null));
            }

            return new ToolCommandResult.TodoCreated(
                    dbPath,
                    new ToolCommandResult.TodoItem(todoId, command.sessionId().trim(), command.title().trim(), command.details(), STATUS_OPEN, now, now)
            );
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
        sql.append(" ORDER BY updated_at_ms DESC LIMIT ?");
        params.add(limit + 1);

        try (Connection connection = openConnection()) {
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

            return new ToolCommandResult.TodoListed(
                    dbPath,
                    output,
                    limit,
                    truncated,
                    filterStatus ? status : null
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
            Optional<TodoRow> existing = findTodo(connection, command.sessionId().trim(), command.todoId().trim());
            if (existing.isEmpty()) {
                return new CommonCommandResults.Failure("not_found", "Todo not found: " + command.todoId().trim());
            }

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
                    List.of(title, details, status, now, command.sessionId().trim(), command.todoId().trim())
            );
            if (update.isFailure()) {
                return dbFailure("Todo update failed", update.error().orElse(null));
            }

            return new ToolCommandResult.TodoUpdated(
                    dbPath,
                    new ToolCommandResult.TodoItem(current.todoId(), current.sessionId(), title, details, status, current.createdAtMs(), now)
            );
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
            SjResult<Integer> delete = simplyJDBC.executeUpdate(
                    connection,
                    "DELETE FROM todos WHERE session_id = ? AND todo_id = ?",
                    List.of(command.sessionId().trim(), command.todoId().trim())
            );
            if (delete.isFailure()) {
                return dbFailure("Todo delete failed", delete.error().orElse(null));
            }
            int rows = delete.first().orElse(0);
            if (rows == 0) {
                return new CommonCommandResults.Failure("not_found", "Todo not found: " + command.todoId().trim());
            }

            return new ToolCommandResult.TodoDeleted(dbPath, command.todoId().trim());
        } catch (Exception e) {
            return new CommonCommandResults.Failure("db_error", "Failed to delete todo: " + e.getMessage());
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
                                INSERT INTO sessions(session_id, agent_id, alias, sys_prompt_amount, next_message_id, dropped_message_ids_json, created_at_ms, updated_at_ms)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        List.of(
                                command.sessionId().trim(),
                                command.agentId().trim(),
                                command.alias().trim(),
                                Math.max(command.sysPromptAmount(), 0),
                                nextMessageId,
                                dropped,
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

                int nextMessageId = appendMessagesInternal(connection, command.sessionId().trim(), session.nextMessageId(), command.replacement());
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
                return new SessionContextResult.CompactionStateLoaded(List.of(), List.of());
            }
            Set<Integer> dropped = Set.copyOf(parseDroppedIds(session.get().droppedMessageIdsJson()));

            SjResult<CompactionToolRow> toolRows = simplyJDBC.query(
                    connection,
                    """
                            SELECT message_id, tool_call_id, tool_name, content, created_at_ms
                            FROM context_messages
                            WHERE session_id = ? AND element_type = 'tool'
                            ORDER BY message_id DESC
                            LIMIT ?
                            """,
                    List.of(sessionId, MAX_COMPACTION_TOOL_SCAN_LIMIT),
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
                            ORDER BY updated_at_ms DESC
                            LIMIT ?
                            """,
                    List.of(sessionId, todoLimit),
                    TodoRow.class
            );
            if (todoRows.isFailure()) {
                return dbFailure("Compaction todo-state query failed", todoRows.error().orElse(null));
            }

            List<SessionContextResult.CompactionToolMessage> tools = toolRows.rows().stream()
                    .filter(row -> !dropped.contains(row.messageId()))
                    .limit(toolScanLimit)
                    .map(row -> new SessionContextResult.CompactionToolMessage(
                            row.messageId(),
                            row.toolCallId(),
                            row.toolName(),
                            row.content(),
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
            return new SessionContextResult.CompactionStateLoaded(tools, todos);
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
                                session_id, message_id, element_type, content, source_tag, tool_call_id, tool_name,
                                input_domain, input_kind, source_id, correlation_id, metadata_json, tool_calls_json, created_at_ms
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    List.of(
                            stored.sessionId(),
                            stored.messageId(),
                            stored.elementType(),
                            stored.content(),
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

    private Optional<SessionRow> findSession(Connection connection, String sessionId) {
        SjResult<SessionRow> result = simplyJDBC.query(
                connection,
                """
                        SELECT session_id, agent_id, alias, sys_prompt_amount, next_message_id, dropped_message_ids_json,
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
                        INSERT INTO sessions(session_id, agent_id, alias, sys_prompt_amount, next_message_id, dropped_message_ids_json, created_at_ms, updated_at_ms)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                List.of(sessionId, "", "", 0, 0, "[]", now, now)
        );
        if (insert.isFailure()) {
            throw new IllegalStateException("Session shell insert failed: " + errorMessage(insert.error().orElse(null)));
        }
        return new SessionRow(sessionId, "", "", 0, 0, "[]", now, now);
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

    private ContextElement toContextElement(ContextMessageRow row) {
        return switch (row.elementType()) {
            case "system" -> new ContextElement.SystemMsg(row.content());
            case "user" -> new ContextElement.UserMsg(row.content());
            case "assistant" -> new ContextElement.AssistantMsg(row.content(), readToolCalls(row.toolCallsJson()));
            case "tool" -> new ContextElement.ToolMsg(row.toolCallId(), row.toolName(), row.content());
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

    private Connection openConnection() throws Exception {
        Path parent = dbPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Connection connection = sqliteDataSource(dbPath).getConnection();
        ensureSchema(connection);
        return connection;
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
                            created_at_ms INTEGER NOT NULL,
                            updated_at_ms INTEGER NOT NULL
                        )
                        """);

        executeSchemaUpdate(connection,
                """
                        CREATE TABLE IF NOT EXISTS context_messages(
                            session_id TEXT NOT NULL,
                            message_id INTEGER NOT NULL,
                            element_type TEXT NOT NULL,
                            content TEXT NOT NULL,
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
        executeSchemaUpdate(connection,
                "CREATE INDEX IF NOT EXISTS idx_context_messages_session ON context_messages(session_id, message_id)");
    }

    private void executeSchemaUpdate(Connection connection, String sql) {
        SjResult<Integer> result = simplyJDBC.executeUpdate(connection, sql, SimplyJDBC.NO_PARAMS);
        if (result.isFailure()) {
            throw new IllegalStateException("Schema update failed: " + sql + " -> " + errorMessage(result.error().orElse(null)));
        }
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
                case ContextElement.SystemMsg ignored -> "system";
                case ContextElement.UserMsg ignored -> "user";
                case ContextElement.AssistantMsg ignored -> "assistant";
                case ContextElement.ToolMsg ignored -> "tool";
                case ContextElement.SummaryMsg ignored -> "summary";
                case ContextElement.InboundMsg ignored -> "inbound";
            };

            String sourceTag = "";
            String toolCallId = "";
            String toolName = "";
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
            @SjColumn("created_at_ms") long createdAtMs
    ) {
    }

    private record SessionRow(
            @SjColumn("session_id") String sessionId,
            @SjColumn("agent_id") String agentId,
            @SjColumn("alias") String alias,
            @SjColumn("sys_prompt_amount") int sysPromptAmount,
            @SjColumn("next_message_id") int nextMessageId,
            @SjColumn("dropped_message_ids_json") String droppedMessageIdsJson,
            @SjColumn("created_at_ms") long createdAtMs,
            @SjColumn("updated_at_ms") long updatedAtMs
    ) {
    }

    private record ContextMessageRow(
            @SjColumn("session_id") String sessionId,
            @SjColumn("message_id") int messageId,
            @SjColumn("element_type") String elementType,
            @SjColumn("content") String content,
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
