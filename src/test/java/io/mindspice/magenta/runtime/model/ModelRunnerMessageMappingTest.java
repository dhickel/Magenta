package io.mindspice.magenta.runtime.model;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import io.mindspice.magenta.runtime.context.ContextElement;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelRunnerMessageMappingTest {

    @Test
    @SuppressWarnings("unchecked")
    void summaryMessagesMapToUserRoleNotSystemRole() throws Exception {
        ModelRunner runner = new ModelRunner(new OllamaClient());
        Method mapper = ModelRunner.class.getDeclaredMethod("toChatMessages", List.class);
        mapper.setAccessible(true);

        List<ChatMessage> messages = (List<ChatMessage>) mapper.invoke(
                runner,
                List.of(
                        new ContextElement.SystemCoreMsg("system"),
                        new ContextElement.SummaryMsg("session summary", "session:test")
                )
        );

        assertThat(messages).hasSize(2);
        assertThat(messages.get(1)).isInstanceOf(UserMessage.class);
        UserMessage summary = (UserMessage) messages.get(1);
        assertThat(summary.singleText()).startsWith("[Context Summary]");
    }

    @Test
    void toolPayloadTruncationForContextCompactsOversizedPayload() throws Exception {
        ModelRunner runner = new ModelRunner(new OllamaClient());
        Method truncator = ModelRunner.class.getDeclaredMethod("truncateToolContentForContext", String.class);
        truncator.setAccessible(true);

        String oversized = "x".repeat(5000);
        String truncated = (String) truncator.invoke(runner, oversized);

        assertThat(truncated.length()).isLessThan(oversized.length());
        assertThat(truncated).endsWith("...");
    }
}
