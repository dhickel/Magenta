package io.mindspice.magenta.runtime.persistence;

import java.nio.file.Path;
import java.util.List;

public sealed interface ToolCommandResult permits CommonCommandResults.Success,
        CommonCommandResults.Failure,
        ToolCommandResult.TodoCreated,
        ToolCommandResult.TodoListed,
        ToolCommandResult.TodoUpdated,
        ToolCommandResult.TodoDeleted,
        ToolCommandResult.HistoryMetaListed,
        ToolCommandResult.HistoryRawLoaded {

    record TodoItem(
            String todoId,
            String sessionId,
            String title,
            String details,
            String status,
            long createdAtMs,
            long updatedAtMs
    ) {
        public TodoItem {
            todoId = todoId == null ? "" : todoId;
            sessionId = sessionId == null ? "" : sessionId;
            title = title == null ? "" : title;
            details = details == null ? "" : details;
            status = status == null ? "" : status;
        }
    }

    record TodoCreated(
            Path dbPath,
            TodoItem todo,
            boolean created,
            String activeTodoId,
            int openCount
    ) implements ToolCommandResult {
        public TodoCreated {
            activeTodoId = activeTodoId == null ? "" : activeTodoId;
            openCount = Math.max(openCount, 0);
        }
    }

    record TodoListed(
            Path dbPath,
            List<TodoItem> todos,
            int limit,
            boolean truncated,
            String statusOrNull,
            String activeTodoId,
            int openCount,
            int doneCount
    ) implements ToolCommandResult {
        public TodoListed {
            todos = todos == null ? List.of() : List.copyOf(todos);
            statusOrNull = statusOrNull == null ? "" : statusOrNull;
            activeTodoId = activeTodoId == null ? "" : activeTodoId;
            openCount = Math.max(openCount, 0);
            doneCount = Math.max(doneCount, 0);
        }
    }

    record TodoUpdated(
            Path dbPath,
            TodoItem todo,
            String action,
            String activeTodoId,
            String previousFocusTodoId
    ) implements ToolCommandResult {
        public TodoUpdated {
            action = action == null ? "updated" : action;
            activeTodoId = activeTodoId == null ? "" : activeTodoId;
            previousFocusTodoId = previousFocusTodoId == null ? "" : previousFocusTodoId;
        }
    }

    record TodoDeleted(
            Path dbPath,
            String todoId,
            String activeTodoId,
            TodoItem nextFocus
    ) implements ToolCommandResult {
        public TodoDeleted {
            todoId = todoId == null ? "" : todoId;
            activeTodoId = activeTodoId == null ? "" : activeTodoId;
        }
    }

    record HistoryMetaItem(
            int messageId,
            String elementType,
            String toolCallId,
            String toolName,
            String status,
            String code,
            String preview,
            long createdAtMs,
            boolean dropped
    ) {
        public HistoryMetaItem {
            messageId = Math.max(messageId, 0);
            elementType = elementType == null ? "" : elementType;
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            status = status == null ? "" : status;
            code = code == null ? "" : code;
            preview = preview == null ? "" : preview;
            createdAtMs = Math.max(createdAtMs, 0L);
        }
    }

    record HistoryMetaListed(
            Path dbPath,
            List<HistoryMetaItem> items,
            int limit,
            boolean truncated,
            int nextBeforeMessageId,
            String elementTypeFilter,
            String toolNameFilter,
            boolean includeDropped
    ) implements ToolCommandResult {
        public HistoryMetaListed {
            items = items == null ? List.of() : List.copyOf(items);
            limit = Math.max(limit, 1);
            nextBeforeMessageId = Math.max(nextBeforeMessageId, 0);
            elementTypeFilter = elementTypeFilter == null ? "" : elementTypeFilter;
            toolNameFilter = toolNameFilter == null ? "" : toolNameFilter;
        }
    }

    record HistoryRawLoaded(
            Path dbPath,
            int messageId,
            String elementType,
            String toolCallId,
            String toolName,
            String rawContentSlice,
            int startChar,
            int returnedChars,
            int totalChars,
            boolean hasMore,
            boolean dropped,
            long createdAtMs
    ) implements ToolCommandResult {
        public HistoryRawLoaded {
            messageId = Math.max(messageId, 0);
            elementType = elementType == null ? "" : elementType;
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            rawContentSlice = rawContentSlice == null ? "" : rawContentSlice;
            startChar = Math.max(startChar, 0);
            returnedChars = Math.max(returnedChars, 0);
            totalChars = Math.max(totalChars, 0);
            createdAtMs = Math.max(createdAtMs, 0L);
        }
    }
}
