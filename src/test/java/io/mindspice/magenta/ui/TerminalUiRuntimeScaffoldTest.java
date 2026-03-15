package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalUiRuntimeScaffoldTest {

    @Test
    void composerLayoutWrapsLongLinesAndPreservesTrailingBlankLine() {
        ComposerInput.WrappedLayout layout = ComposerInput.layoutFor("abcdef\nxy\n", 3);

        assertThat(layout.lines())
                .extracting(ComposerInput.WrappedLine::text)
                .containsExactly("abc", "def", "xy", "");
        assertThat(layout.lines())
                .extracting(ComposerInput.WrappedLine::startIndex)
                .containsExactly(0, 3, 7, 10);
        assertThat(layout.lines())
                .extracting(ComposerInput.WrappedLine::endIndex)
                .containsExactly(3, 6, 9, 10);
    }

    @Test
    void composerCaretMappingTracksWrappedRowsBidirectionally() {
        ComposerInput.WrappedLayout layout = ComposerInput.layoutFor("abcdef", 3);

        assertThat(layout.positionOf(0)).isEqualTo(new ComposerInput.CaretPosition(0, 0));
        assertThat(layout.positionOf(4)).isEqualTo(new ComposerInput.CaretPosition(1, 1));
        assertThat(layout.positionOf(6)).isEqualTo(new ComposerInput.CaretPosition(1, 3));
        assertThat(layout.indexAt(1, 1)).isEqualTo(4);
        assertThat(layout.indexAt(1, 99)).isEqualTo(6);
    }

    @Test
    void composerAltEnterStillSubmits() {
        AtomicReference<String> submitted = new AtomicReference<>();
        ComposerInput composer = new ComposerInput(text -> {
            submitted.set(text);
            return true;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke(KeyType.Enter, false, true));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitted).hasValue("hello");
        assertThat(composer.getText()).isEmpty();
    }

    @Test
    void composerShiftedEnterStillSubmits() {
        AtomicReference<String> submitted = new AtomicReference<>();
        ComposerInput composer = new ComposerInput(text -> {
            submitted.set(text);
            return true;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke(KeyType.Enter, false, true));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitted).hasValue("hello");
        assertThat(composer.getText()).isEmpty();
    }

    @Test
    void composerNewlineCharacterStillSubmits() {
        AtomicReference<String> submitted = new AtomicReference<>();
        ComposerInput composer = new ComposerInput(text -> {
            submitted.set(text);
            return true;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke('\n', false, false, true));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitted).hasValue("hello");
        assertThat(composer.getText()).isEmpty();
    }

    @Test
    void composerEnterSubmitsAndClearsWhenAccepted() {
        AtomicReference<String> submitted = new AtomicReference<>();
        ComposerInput composer = new ComposerInput(text -> {
            submitted.set(text);
            return true;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("ship it");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke(KeyType.Enter));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitted).hasValue("ship it");
        assertThat(composer.getText()).isEmpty();
    }

    @Test
    void composerCtrlCInvokesAbortHandler() {
        AtomicInteger abortCalls = new AtomicInteger();
        ComposerInput composer = new ComposerInput(text -> false, abortCalls::incrementAndGet);

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke('c', true, false));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(abortCalls).hasValue(1);
    }

    @Test
    void composerCtrlNInsertsNewlineWithoutSubmitting() {
        AtomicInteger submitCalls = new AtomicInteger();
        ComposerInput composer = new ComposerInput(text -> {
            submitCalls.incrementAndGet();
            return false;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke('n', true, false));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitCalls).hasValue(0);
        assertThat(composer.getText()).isEqualTo("hello\n");
    }

    @Test
    void composerCtrlNControlCharacterInsertsNewlineWithoutSubmitting() {
        AtomicInteger submitCalls = new AtomicInteger();
        ComposerInput composer = new ComposerInput(text -> {
            submitCalls.incrementAndGet();
            return false;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke('\u000E', false, false));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitCalls).hasValue(0);
        assertThat(composer.getText()).isEqualTo("hello\n");
    }

    @Test
    void composerCtrlNewlineCharacterDoesNotSubmitOrInsert() {
        AtomicInteger submitCalls = new AtomicInteger();
        ComposerInput composer = new ComposerInput(text -> {
            submitCalls.incrementAndGet();
            return true;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke('\n', true, false));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitCalls).hasValue(0);
        assertThat(composer.getText()).isEqualTo("hello");
    }

    @Test
    void transcriptViewKeepsScrollAnchorAcrossRewrap() {
        TranscriptView view = new TranscriptView();
        view.setSize(new TerminalSize(14, 4));
        view.setBlocks(List.of(
                new TranscriptView.Block(1L, TextColor.ANSI.WHITE, TextColor.ANSI.BLACK, "one\ntwo\nthree\nfour"),
                new TranscriptView.Block(2L, TextColor.ANSI.WHITE, TextColor.ANSI.BLACK, "alpha beta gamma delta epsilon zeta")
        ));
        view.scrollBy(3);
        int anchoredTop = view.topRow();

        view.setSize(new TerminalSize(10, 4));
        view.refreshLayout();

        assertThat(view.topRow()).isGreaterThanOrEqualTo(anchoredTop);
    }

    @Test
    void transcriptViewAddsScrollbarColumnWhenContentExceedsViewport() {
        List<TranscriptView.RenderedLine> lines = TranscriptView.renderBlocks(
                List.of(new TranscriptView.Block(
                        1L,
                        TextColor.ANSI.WHITE,
                        TextColor.ANSI.BLACK,
                        "line one\nline two\nline three\nline four\nline five"
                )),
                new TerminalSize(12, 3)
        );

        assertThat(lines).hasSizeGreaterThan(3);
        assertThat(lines).allSatisfy(line -> assertThat(line.text().length()).isLessThanOrEqualTo(11));
    }

    @Test
    void transcriptBlockFormatUsesBracketTagAndNoBottomRule() {
        String block = TerminalUiRuntime.formatTranscriptBlock("user", List.of("hello", "world"), false);

        assertThat(block).isEqualTo("[user]\n│ hello\n│ world");
    }

    @Test
    void fillSplitAllocationHonorsMinimumsForHorizontalSpace() {
        FillSplitPanel.Allocation allocation = FillSplitPanel.allocate(99, 0.10d, 56, 24);

        assertThat(allocation.first()).isEqualTo(56);
        assertThat(allocation.second()).isEqualTo(43);
    }

    @Test
    void fillSplitAllocationFallsBackWhenSpaceCannotSatisfyBothMinimums() {
        FillSplitPanel.Allocation allocation = FillSplitPanel.allocate(20, 0.90d, 56, 24);

        assertThat(allocation.first()).isEqualTo(19);
        assertThat(allocation.second()).isEqualTo(1);
    }

    @Test
    void fillSplitAdjustByStopsBeforeViolatingSecondaryMinimum() throws Exception {
        FillSplitPanel split = FillSplitPanel.horizontal(new Panel(), new Panel(), 0.75d);
        split.setMinimumPrimarySizes(56, 24);
        split.setSize(new TerminalSize(100, 10));

        split.adjustBy(50);

        assertThat(readRatio(split)).isEqualTo(75d / 99d);
    }

    private static double readRatio(FillSplitPanel split) throws Exception {
        Field field = FillSplitPanel.class.getDeclaredField("ratio");
        field.setAccessible(true);
        return field.getDouble(split);
    }
}
