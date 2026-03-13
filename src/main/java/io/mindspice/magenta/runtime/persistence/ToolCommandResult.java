package io.mindspice.magenta.runtime.persistence;

import java.nio.file.Path;
import java.util.List;

public sealed interface ToolCommandResult permits CommonCommandResults.Success,
        CommonCommandResults.Failure,
        ToolCommandResult.TodoCreated,
        ToolCommandResult.TodoListed,
        ToolCommandResult.TodoUpdated,
        ToolCommandResult.TodoDeleted {

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

    record TodoCreated(Path dbPath, TodoItem todo, boolean created) implements ToolCommandResult {
    }

    record TodoListed(
            Path dbPath,
            List<TodoItem> todos,
            int limit,
            boolean truncated,
            String statusOrNull
    ) implements ToolCommandResult {
        public TodoListed {
            todos = todos == null ? List.of() : List.copyOf(todos);
        }
    }

    record TodoUpdated(Path dbPath, TodoItem todo) implements ToolCommandResult {
    }

    record TodoDeleted(Path dbPath, String todoId) implements ToolCommandResult {
        public TodoDeleted {
            todoId = todoId == null ? "" : todoId;
        }
    }
}
