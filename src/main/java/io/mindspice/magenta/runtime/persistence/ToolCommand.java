package io.mindspice.magenta.runtime.persistence;

public sealed interface ToolCommand permits ToolCommand.TodoCreate,
        ToolCommand.TodoList,
        ToolCommand.TodoUpdate,
        ToolCommand.TodoDelete,
        ToolCommand.HistoryMetaLookup,
        ToolCommand.HistoryRawLookup {

    record TodoCreate(String sessionId, String title, String details) implements ToolCommand {
        public TodoCreate {
            sessionId = sessionId == null ? "" : sessionId;
            title = title == null ? "" : title;
            details = details == null ? "" : details;
        }
    }

    record TodoList(String sessionId, String status, int limit) implements ToolCommand {
        public TodoList {
            sessionId = sessionId == null ? "" : sessionId;
            status = status == null ? "" : status;
            limit = Math.max(limit, 1);
        }
    }

    record TodoUpdate(
            String sessionId,
            String todoId,
            boolean updateTitle,
            String title,
            boolean updateDetails,
            String details,
            boolean updateStatus,
            String status
    ) implements ToolCommand {
        public TodoUpdate {
            sessionId = sessionId == null ? "" : sessionId;
            todoId = todoId == null ? "" : todoId;
            title = title == null ? "" : title;
            details = details == null ? "" : details;
            status = status == null ? "" : status;
        }
    }

    record TodoDelete(String sessionId, String todoId) implements ToolCommand {
        public TodoDelete {
            sessionId = sessionId == null ? "" : sessionId;
            todoId = todoId == null ? "" : todoId;
        }
    }

    record HistoryMetaLookup(
            String sessionId,
            int limit,
            Integer beforeMessageId,
            String elementTypeFilter,
            String toolNameFilter,
            boolean includeDropped
    ) implements ToolCommand {
        public HistoryMetaLookup {
            sessionId = sessionId == null ? "" : sessionId;
            limit = Math.max(limit, 1);
            elementTypeFilter = elementTypeFilter == null ? "" : elementTypeFilter;
            toolNameFilter = toolNameFilter == null ? "" : toolNameFilter;
        }
    }

    record HistoryRawLookup(
            String sessionId,
            int messageId,
            int startChar,
            int maxChars
    ) implements ToolCommand {
        public HistoryRawLookup {
            sessionId = sessionId == null ? "" : sessionId;
            messageId = Math.max(messageId, 0);
            startChar = Math.max(startChar, 0);
            maxChars = Math.max(maxChars, 1);
        }
    }
}
