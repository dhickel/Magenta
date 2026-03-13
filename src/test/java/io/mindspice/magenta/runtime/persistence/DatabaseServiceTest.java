package io.mindspice.magenta.runtime.persistence;

import io.mindspice.magenta.runtime.context.ContextElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void initializeAppendLoadAndMessageLookupRoundTrip() {
        DatabaseService service = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();

        SessionContextResult initialized = service.execute(new SessionContextCommand.InitializeSession(
                sessionId,
                "agent-default",
                "alpha",
                2,
                List.of(
                        new ContextElement.SystemMsg("Base prompt"),
                        new ContextElement.SystemMsg("Agent prompt")
                )
        ));
        assertThat(initialized).isInstanceOf(CommonCommandResults.Success.class);

        SessionContextResult appendResult = service.execute(new SessionContextCommand.AppendMessages(
                sessionId,
                List.of(
                        new ContextElement.UserMsg("hello"),
                        new ContextElement.AssistantMsg("world", List.of())
                )
        ));
        assertThat(appendResult).isInstanceOf(CommonCommandResults.Success.class);

        SessionContextResult loaded = service.execute(new SessionContextCommand.LoadActiveContext(sessionId));
        assertThat(loaded).isInstanceOf(SessionContextResult.ActiveContextLoaded.class);

        SessionContextResult.ActiveContextLoaded active = (SessionContextResult.ActiveContextLoaded) loaded;
        assertThat(active.sysPromptAmount()).isEqualTo(2);
        assertThat(active.nextMessageId()).isEqualTo(4);
        assertThat(active.messages()).containsExactly(
                new ContextElement.SystemMsg("Base prompt"),
                new ContextElement.SystemMsg("Agent prompt"),
                new ContextElement.UserMsg("hello"),
                new ContextElement.AssistantMsg("world", List.of())
        );

        SessionContextResult message = service.execute(new SessionContextCommand.GetMessageById(sessionId, 2));
        assertThat(message).isInstanceOf(SessionContextResult.ContextMessageLoaded.class);
        SessionContextResult.ContextMessageLoaded loadedMessage = (SessionContextResult.ContextMessageLoaded) message;
        assertThat(loadedMessage.dropped()).isFalse();
        assertThat(loadedMessage.message()).isEqualTo(new ContextElement.UserMsg("hello"));
    }

    @Test
    void replaceActiveContextDropsPriorIdsAndAppendsReplacementIds() {
        DatabaseService service = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();

        service.execute(new SessionContextCommand.InitializeSession(
                sessionId,
                "agent-default",
                "alpha",
                1,
                List.of(
                        new ContextElement.SystemMsg("system"),
                        new ContextElement.UserMsg("u1"),
                        new ContextElement.UserMsg("u2")
                )
        ));

        SessionContextResult replaceResult = service.execute(new SessionContextCommand.ReplaceActiveContext(
                sessionId,
                List.of(
                        new ContextElement.SystemMsg("system"),
                        new ContextElement.SummaryMsg("summary", "session:" + sessionId)
                ),
                1
        ));
        assertThat(replaceResult).isInstanceOf(CommonCommandResults.Success.class);

        SessionContextResult loaded = service.execute(new SessionContextCommand.LoadActiveContext(sessionId));
        SessionContextResult.ActiveContextLoaded active = (SessionContextResult.ActiveContextLoaded) loaded;

        assertThat(active.nextMessageId()).isEqualTo(5);
        assertThat(active.droppedMessageIds()).containsExactly(0, 1, 2);
        assertThat(active.messages()).containsExactly(
                new ContextElement.SystemMsg("system"),
                new ContextElement.SummaryMsg("summary", "session:" + sessionId)
        );

        SessionContextResult oldMessage = service.execute(new SessionContextCommand.GetMessageById(sessionId, 0));
        SessionContextResult.ContextMessageLoaded droppedMessage = (SessionContextResult.ContextMessageLoaded) oldMessage;
        assertThat(droppedMessage.dropped()).isTrue();

        SessionContextResult newMessage = service.execute(new SessionContextCommand.GetMessageById(sessionId, 3));
        SessionContextResult.ContextMessageLoaded activeMessage = (SessionContextResult.ContextMessageLoaded) newMessage;
        assertThat(activeMessage.dropped()).isFalse();
        assertThat(activeMessage.message()).isEqualTo(new ContextElement.SystemMsg("system"));
    }

    @Test
    void loadCompactionStateReturnsBoundedRecentToolRowsAndTodos() {
        DatabaseService service = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();

        service.execute(new SessionContextCommand.InitializeSession(
                sessionId,
                "agent-default",
                "alpha",
                1,
                List.of(new ContextElement.SystemMsg("system"))
        ));
        service.execute(new SessionContextCommand.AppendMessages(
                sessionId,
                List.of(
                        new ContextElement.ToolMsg("call-1", "read_file", "{\"status\":\"ok\",\"data\":{\"path\":\"a.txt\",\"snapshotId\":\"snap-1\"}}"),
                        new ContextElement.ToolMsg("call-2", "todo_create", "{\"status\":\"ok\",\"data\":{\"todo\":{\"todoId\":\"todo-1\",\"title\":\"First\",\"status\":\"open\",\"updatedAtMs\":1000}}}"),
                        new ContextElement.ToolMsg("call-3", "write_file", "{\"status\":\"ok\",\"data\":{\"path\":\"a.txt\",\"snapshotId\":\"snap-2\"}}"),
                        new ContextElement.ToolMsg("call-4", "todo_update", "{\"status\":\"ok\",\"data\":{\"todo\":{\"todoId\":\"todo-2\",\"title\":\"Second\",\"status\":\"done\",\"updatedAtMs\":2000}}}"),
                        new ContextElement.ToolMsg("call-5", "shell_command", "{\"status\":\"ok\",\"data\":{\"command\":\"pwd\"}}"),
                        new ContextElement.ToolMsg("call-6", "todo_delete", "{\"status\":\"ok\",\"data\":{\"todoId\":\"todo-3\"}}")
                )
        ));

        ToolCommandResult.TodoCreated createdA = (ToolCommandResult.TodoCreated) service.execute(
                new ToolCommand.TodoCreate(sessionId, "A", "")
        );
        ToolCommandResult.TodoCreated createdB = (ToolCommandResult.TodoCreated) service.execute(
                new ToolCommand.TodoCreate(sessionId, "B", "")
        );
        service.execute(new ToolCommand.TodoUpdate(
                sessionId,
                createdA.todo().todoId(),
                false,
                "",
                false,
                "",
                true,
                "done"
        ));

        SessionContextResult stateResult = service.execute(new SessionContextCommand.LoadCompactionState(sessionId, 4, 2));
        assertThat(stateResult).isInstanceOf(SessionContextResult.CompactionStateLoaded.class);

        SessionContextResult.CompactionStateLoaded loaded = (SessionContextResult.CompactionStateLoaded) stateResult;
        assertThat(loaded.recentToolMessages()).hasSize(4);
        assertThat(loaded.recentToolMessages().get(0).toolCallId()).isEqualTo("call-6");
        assertThat(loaded.recentToolMessages().get(0).messageId())
                .isGreaterThan(loaded.recentToolMessages().get(1).messageId());
        assertThat(loaded.todos()).hasSize(2);
        assertThat(loaded.todos().get(0).todoId()).isEqualTo(createdA.todo().todoId());
        assertThat(loaded.todos().stream().map(SessionContextResult.CompactionTodoItem::todoId))
                .contains(createdA.todo().todoId(), createdB.todo().todoId());
    }

    @Test
    void loadCompactionStateHandlesMissingSessionAsEmptyState() {
        DatabaseService service = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();

        SessionContextResult stateResult = service.execute(new SessionContextCommand.LoadCompactionState(sessionId, 40, 50));
        assertThat(stateResult).isInstanceOf(SessionContextResult.CompactionStateLoaded.class);

        SessionContextResult.CompactionStateLoaded loaded = (SessionContextResult.CompactionStateLoaded) stateResult;
        assertThat(loaded.recentToolMessages()).isEmpty();
        assertThat(loaded.todos()).isEmpty();
    }

    @Test
    void loadCompactionStateExcludesDroppedToolMessagesAfterReplace() {
        DatabaseService service = new DatabaseService(tempDir);
        String sessionId = UUID.randomUUID().toString();

        service.execute(new SessionContextCommand.InitializeSession(
                sessionId,
                "agent-default",
                "alpha",
                1,
                List.of(new ContextElement.SystemMsg("system"))
        ));
        service.execute(new SessionContextCommand.AppendMessages(
                sessionId,
                List.of(
                        new ContextElement.ToolMsg("call-old-1", "todo_update", "{\"status\":\"ok\"}"),
                        new ContextElement.ToolMsg("call-old-2", "todo_update", "{\"status\":\"ok\"}")
                )
        ));

        service.execute(new SessionContextCommand.ReplaceActiveContext(
                sessionId,
                List.of(
                        new ContextElement.SystemMsg("system"),
                        new ContextElement.ToolMsg("call-new-1", "todo_update", "{\"status\":\"ok\"}"),
                        new ContextElement.ToolMsg("call-new-2", "write_file", "{\"status\":\"ok\"}")
                ),
                1
        ));

        SessionContextResult stateResult = service.execute(new SessionContextCommand.LoadCompactionState(sessionId, 10, 10));
        assertThat(stateResult).isInstanceOf(SessionContextResult.CompactionStateLoaded.class);

        SessionContextResult.CompactionStateLoaded loaded = (SessionContextResult.CompactionStateLoaded) stateResult;
        assertThat(loaded.recentToolMessages())
                .extracting(SessionContextResult.CompactionToolMessage::toolCallId)
                .containsExactly("call-new-2", "call-new-1");
    }
}
