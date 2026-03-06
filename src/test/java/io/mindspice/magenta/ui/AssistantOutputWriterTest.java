package io.mindspice.magenta.ui;

import io.mindspice.magenta.runtime.context.ContextElement;
import io.mindspice.magenta.runtime.session.SessionOutput;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantOutputWriterTest {

    @Test
    void streamedResponsePrintsPrefixChunksAndCompletesLine() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target);

        writer.onOutput(new SessionOutput.StreamedOutput("Hel"));
        writer.onOutput(new SessionOutput.StreamedOutput("lo"));
        writer.onOutput(new SessionOutput.FinalOutput("Hello"));

        assertThat(target.assistantTokens).containsExactly("assistant> ", "Hel", "lo");
        assertThat(target.finishedStreamLines).isEqualTo(1);
        assertThat(target.fallbackNotices).isEmpty();
        assertThat(target.assistantFinals).isEmpty();
    }

    @Test
    void finalOnlyResponsePrintsFallbackAndFinalText() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target, true);

        writer.onOutput(new SessionOutput.FinalOutput("Hello"));

        assertThat(target.fallbackNotices).singleElement()
                .isEqualTo("No streamed chunks were received for this response.");
        assertThat(target.assistantFinals).singleElement().isEqualTo("assistant> Hello");
        assertThat(target.finishedStreamLines).isZero();
    }

    @Test
    void finalOnlyResponseCanSuppressFallbackNotice() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target, false);

        writer.onOutput(new SessionOutput.FinalOutput("Hello"));

        assertThat(target.fallbackNotices).isEmpty();
        assertThat(target.assistantFinals).singleElement().isEqualTo("assistant> Hello");
    }

    @Test
    void toolMessageOutputPrintsToolResult() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target);

        writer.onOutput(new SessionOutput.ToolMessageOutput(new ContextElement.ToolMsg("call-1", "tool-a", "result")));

        assertThat(target.toolResults).containsExactly("tool-a|false=result");
    }

    @Test
    void toolCallOutputPrintsToolRequestSummary() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target);

        writer.onOutput(new SessionOutput.ToolCallOutput(new ContextElement.ToolCall("call-1", "shell_command", "{\"command\":\"ls\"}")));

        assertThat(target.toolCalls).containsExactly("shell_command={\"command\":\"ls\"}");
    }

    @Test
    void blankFinalOutputDoesNotPrintFallbackOrFinal() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target, true);

        writer.onOutput(new SessionOutput.FinalOutput(""));

        assertThat(target.fallbackNotices).isEmpty();
        assertThat(target.assistantFinals).isEmpty();
    }

    @Test
    void failedToolPayloadUsesFailedFlag() {
        RecordingTarget target = new RecordingTarget();
        AssistantOutputWriter writer = new AssistantOutputWriter(target);

        writer.onOutput(new SessionOutput.ToolMessageOutput(new ContextElement.ToolMsg(
                "call-1",
                "shell_command",
                "{\"status\":\"failed\",\"code\":\"denied\"}"
        )));

        assertThat(target.toolResults).containsExactly("shell_command|true={\"status\":\"failed\",\"code\":\"denied\"}");
    }

    private static final class RecordingTarget implements AssistantOutputTarget {
        private final List<String> assistantTokens = new ArrayList<>();
        private final List<String> assistantFinals = new ArrayList<>();
        private final List<String> toolCalls = new ArrayList<>();
        private final List<String> toolResults = new ArrayList<>();
        private final List<String> fallbackNotices = new ArrayList<>();
        private int finishedStreamLines = 0;

        @Override
        public void printAssistantToken(String token) {
            assistantTokens.add(token);
        }

        @Override
        public void finishAssistantStreamLine() {
            finishedStreamLines++;
        }

        @Override
        public void printAssistantFinal(String text) {
            assistantFinals.add(text);
        }

        @Override
        public void printToolCall(String toolName, String argumentsJson) {
            toolCalls.add(toolName + "=" + argumentsJson);
        }

        @Override
        public void printToolResult(String toolName, String content, boolean failed) {
            toolResults.add(toolName + "|" + failed + "=" + content);
        }

        @Override
        public void printStreamFallbackNotice(String reason) {
            fallbackNotices.add(reason);
        }
    }
}
