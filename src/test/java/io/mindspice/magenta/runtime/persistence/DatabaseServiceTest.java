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
}
