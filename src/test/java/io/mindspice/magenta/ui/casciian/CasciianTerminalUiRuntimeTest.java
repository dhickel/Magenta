package io.mindspice.magenta.ui.casciian;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CasciianTerminalUiRuntimeTest {
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Test
    void transcriptTagUsesConfiguredCornerAndTimestampSpacing() {
        Instant timestamp = Instant.parse("2026-03-13T19:03:42Z");
        String tag = CasciianTerminalUiRuntime.formatTranscriptTag(
                "magenta",
                timestamp,
                32
        );

        assertThat(tag).isEqualTo("┌─" + TS_FORMAT.format(timestamp) + " [magenta]"
                + " ".repeat(32 - ("┌─" + TS_FORMAT.format(timestamp) + " [magenta]").length()));
    }

    @Test
    void transcriptTagFallsBackToLabelOnlyWhenTimestampMissing() {
        String tag = CasciianTerminalUiRuntime.formatTranscriptTag("user", null, 16);

        assertThat(tag).isEqualTo("┌─[user]        ");
    }

    @Test
    void transcriptBodyKeepsVerticalPrefixOnEveryWrappedLine() {
        List<String> lines = CasciianTerminalUiRuntime.formatTranscriptBodyLines(
                List.of("hello there", "final"),
                10
        );

        assertThat(lines).containsExactly(
                "│ hello   ",
                "│ there   ",
                "│ final   "
        );
    }
}
