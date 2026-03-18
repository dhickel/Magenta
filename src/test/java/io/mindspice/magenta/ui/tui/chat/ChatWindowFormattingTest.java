package io.mindspice.magenta.ui.tui.chat;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWindowFormattingTest {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Test
    void formatBlockLinesUsesGlyphBordersAndTimestampHeader() {
        Instant timestamp = Instant.parse("2026-03-17T07:22:30Z");
        List<String> lines = ChatWindow.formatBlockLines(
                "magenta",
                List.of("Magenta. What can I do for you?"),
                true,
                timestamp,
                80
        );

        assertThat(lines).containsExactly(
                "┌─ [" + TS_FORMAT.format(timestamp) + "] magenta",
                "│ Magenta. What can I do for you?"
        );
    }

    @Test
    void formatBlockLinesWrapsContinuationLinesWithGlyphBorder() {
        List<String> lines = ChatWindow.formatBlockLines(
                "assistant",
                List.of("abcdefghij"),
                false,
                Instant.parse("2026-03-17T03:22:30Z"),
                6
        );

        assertThat(lines).containsExactly(
                "┌─ assistant",
                "│ abcd",
                "│ efgh",
                "│ ij"
        );
    }

    @Test
    void commitStreamingClearsTransientStateBeforePersistingFinalBlock() {
        ChatWindow.StreamingCommit commit = ChatWindow.commitStreaming("assistant", "assistant", "Hello");

        assertThat(commit.nextStreamingTitle()).isNull();
        assertThat(commit.nextStreamingStyleKey()).isNull();
        assertThat(commit.nextStreamingText()).isEmpty();
        assertThat(commit.block()).isEqualTo(new ChatWindow.TranscriptBlock("assistant", "assistant", List.of("Hello")));
    }

    @Test
    void stripTrailingLineBreaksRemovesComposerSubmitNewlineWithoutTouchingBody() {
        assertThat(ChatWindowController.stripTrailingLineBreaks("Hello\n")).isEqualTo("Hello");
        assertThat(ChatWindowController.stripTrailingLineBreaks("Hello\n\n")).isEqualTo("Hello");
        assertThat(ChatWindowController.stripTrailingLineBreaks("Hello\nWorld")).isEqualTo("Hello\nWorld");
    }
}
