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

        assertThat(tag).isEqualTo("┌─" + TS_FORMAT.format(timestamp) + " [magenta]");
    }

    @Test
    void transcriptTagFallsBackToLabelOnlyWhenTimestampMissing() {
        String tag = CasciianTerminalUiRuntime.formatTranscriptTag("user", null, 16);

        assertThat(tag).isEqualTo("┌─[user]");
    }

    @Test
    void transcriptBodyKeepsVerticalPrefixOnEveryWrappedLine() {
        List<String> lines = CasciianTerminalUiRuntime.formatTranscriptBodyLines(
                List.of("hello there", "final"),
                10
        );

        assertThat(lines).containsExactly(
                "│ hello",
                "│ there",
                "│ final"
        );
    }

    @Test
    void conversationLayoutUsesTopRowAndTightHeaderSeparatorStack() {
        CasciianTerminalUiRuntime.ConversationLayout layout =
                CasciianTerminalUiRuntime.conversationLayoutFor(80, 20, 80, 8);

        assertThat(layout.headerPrimaryY()).isZero();
        assertThat(layout.headerSecondaryY()).isZero();
        assertThat(layout.headerSeparatorY()).isEqualTo(1);
        assertThat(layout.transcriptY()).isEqualTo(2);
        assertThat(layout.footerSeparatorY()).isEqualTo(18);
        assertThat(layout.footerY()).isEqualTo(19);
    }

    @Test
    void conversationLayoutLeavesFooterOnLastTranscriptRowAndExpandsTranscriptArea() {
        CasciianTerminalUiRuntime.ConversationLayout layout =
                CasciianTerminalUiRuntime.conversationLayoutFor(64, 12, 64, 6);

        assertThat(layout.transcriptRows()).isEqualTo(8);
        assertThat(layout.footerSeparatorY()).isEqualTo(10);
        assertThat(layout.footerY()).isEqualTo(11);
        assertThat(layout.composerRows()).isEqualTo(6);
    }
}
