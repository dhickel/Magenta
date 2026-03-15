package io.mindspice.magenta.runtime.persistence;

import io.mindspice.magenta.runtime.context.ContextElement;

import java.util.List;

public sealed interface SessionContextResult permits CommonCommandResults.Success,
        CommonCommandResults.Failure,
        SessionContextResult.ActiveContextLoaded,
        SessionContextResult.ContextMessageLoaded,
        SessionContextResult.CompactionStateLoaded {

    record ActiveContextLoaded(
            List<ContextElement> messages,
            int sysPromptAmount,
            int nextMessageId,
            List<Integer> droppedMessageIds
    ) implements SessionContextResult {
        public ActiveContextLoaded {
            messages = messages == null ? List.of() : List.copyOf(messages);
            sysPromptAmount = Math.max(sysPromptAmount, 0);
            nextMessageId = Math.max(nextMessageId, 0);
            droppedMessageIds = droppedMessageIds == null ? List.of() : List.copyOf(droppedMessageIds);
        }
    }

    record ContextMessageLoaded(int messageId, ContextElement message, boolean dropped) implements SessionContextResult {
    }

    record CompactionStateLoaded(
            List<SessionContextResult.CompactionToolMessage> recentToolMessages,
            List<SessionContextResult.CompactionTodoItem> todos,
            int openTodoCount,
            String activeTodoId,
            SessionContextResult.CompactionSnapshot latestSnapshot
    ) implements SessionContextResult {
        public CompactionStateLoaded {
            recentToolMessages = recentToolMessages == null ? List.of() : List.copyOf(recentToolMessages);
            todos = todos == null ? List.of() : List.copyOf(todos);
            openTodoCount = Math.max(openTodoCount, 0);
            activeTodoId = activeTodoId == null ? "" : activeTodoId;
        }
    }

    record CompactionToolMessage(
            int messageId,
            String toolCallId,
            String toolName,
            String content,
            String rawContent,
            boolean contentTruncated,
            long createdAtMs
    ) {
        public CompactionToolMessage {
            messageId = Math.max(messageId, 0);
            toolCallId = toolCallId == null ? "" : toolCallId;
            toolName = toolName == null ? "" : toolName;
            content = content == null ? "" : content;
            rawContent = rawContent == null ? "" : rawContent;
            createdAtMs = Math.max(createdAtMs, 0L);
        }
    }

    record CompactionTodoItem(
            String todoId,
            String title,
            String details,
            String status,
            long createdAtMs,
            long updatedAtMs
    ) {
        public CompactionTodoItem {
            todoId = todoId == null ? "" : todoId;
            title = title == null ? "" : title;
            details = details == null ? "" : details;
            status = status == null ? "" : status;
            createdAtMs = Math.max(createdAtMs, 0L);
            updatedAtMs = Math.max(updatedAtMs, 0L);
        }
    }

    record CompactionSnapshot(
            int snapshotId,
            int summaryMessageId,
            List<Integer> replacementMessageIds,
            String manifestText,
            long createdAtMs
    ) {
        public CompactionSnapshot {
            snapshotId = Math.max(snapshotId, 0);
            summaryMessageId = Math.max(summaryMessageId, 0);
            replacementMessageIds = replacementMessageIds == null ? List.of() : List.copyOf(replacementMessageIds);
            manifestText = manifestText == null ? "" : manifestText;
            createdAtMs = Math.max(createdAtMs, 0L);
        }
    }
}
