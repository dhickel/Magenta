package io.mindspice.magenta2.ai.chat.tool;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;

class ToolTranscriptServiceTest {
    private final ToolTranscriptService service = new ToolTranscriptService(new ObjectMapper());

    @Test
    void truncatesLargeToolOutputAfterFourUserTurns() {
        SystemMessage toolResult = service.fullResult("call-1", "search_notes", "{\"query\":\"deploy\"}", "x".repeat(4_100));

        List<Message> rewritten = service.truncateExpiredLargeResults(List.of(
            toolResult,
            new UserMessage("one"),
            new UserMessage("two"),
            new UserMessage("three"),
            new UserMessage("four")
        ));

        Message rewrittenToolResult = rewritten.getFirst();
        assertThat(service.isSummaryToolTranscript(rewrittenToolResult)).isTrue();
        assertThat(service.renderForModel(rewrittenToolResult))
            .contains("Raw output: truncated")
            .doesNotContain("x".repeat(100));
    }

    @Test
    void keepsSmallToolOutputFullEvenAfterFourUserTurns() {
        SystemMessage toolResult = service.fullResult("call-1", "read_note", "{\"id\":\"1\"}", "small result");

        List<Message> rewritten = service.truncateExpiredLargeResults(List.of(
            toolResult,
            new UserMessage("one"),
            new UserMessage("two"),
            new UserMessage("three"),
            new UserMessage("four")
        ));

        assertThat(rewritten.getFirst()).isSameAs(toolResult);
        assertThat(service.renderForModel(rewritten.getFirst())).contains("small result");
    }

    @Test
    void rendersHistoryWithoutRawOutput() {
        SystemMessage toolResult = service.fullResult("call-1", "read_note", "{\"id\":\"1\"}", "sensitive raw result");

        assertThat(service.renderForHistory(toolResult))
            .contains("Tool read_note completed")
            .doesNotContain("sensitive raw result");
    }

    @Test
    void hardCapsStoredRawOutput() {
        SystemMessage toolResult = service.fullResult("call-1", "large_tool", "{}", "x".repeat(50_000));

        assertThat(toolResult.getText()).hasSizeLessThan(45_000);
        assertThat(service.renderForModel(toolResult))
            .contains("Raw output: truncated")
            .doesNotContain("x".repeat(1_000));
    }
}
