package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    void composerAltEnterInsertsNewlineWithoutSubmitting() {
        AtomicInteger submitCalls = new AtomicInteger();
        ComposerInput composer = new ComposerInput(text -> {
            submitCalls.incrementAndGet();
            return false;
        }, () -> {});
        composer.setSize(new TerminalSize(8, 4));
        composer.setText("hello");

        Interactable.Result result = composer.handleKeyStroke(new KeyStroke(KeyType.Enter, false, true));

        assertThat(result).isEqualTo(Interactable.Result.HANDLED);
        assertThat(submitCalls).hasValue(0);
        assertThat(composer.getText()).isEqualTo("hello\n");
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
