package io.mindspice.magenta.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalLayoutPrimitivesTest {

    @Test
    void composerLayoutHardWrapsAcrossAvailableWidth() {
        ComposerInput.WrappedLayout layout = ComposerInput.layoutFor("abcdefghij", 4);

        assertThat(layout.lines())
                .extracting(ComposerInput.WrappedLine::text)
                .containsExactly("abcd", "efgh", "ij");
        assertThat(layout.positionOf(6)).isEqualTo(new ComposerInput.CaretPosition(1, 2));
        assertThat(layout.indexAt(2, 1)).isEqualTo(9);
    }

    @Test
    void composerLayoutPreservesExplicitBlankLines() {
        ComposerInput.WrappedLayout layout = ComposerInput.layoutFor("ab\n\ncd", 8);

        assertThat(layout.lines())
                .extracting(ComposerInput.WrappedLine::text)
                .containsExactly("ab", "", "cd");
        assertThat(layout.positionOf(3)).isEqualTo(new ComposerInput.CaretPosition(1, 0));
    }

    @Test
    void splitAllocationHonorsMinimumPaneSizes() {
        FillSplitPanel.Allocation allocation = FillSplitPanel.allocate(79, 0.95d, 56, 24);

        assertThat(allocation.first()).isEqualTo(56);
        assertThat(allocation.second()).isEqualTo(23);
    }

    @Test
    void splitAllocationTracksRequestedRatioWhenSpaceAllows() {
        FillSplitPanel.Allocation allocation = FillSplitPanel.allocate(99, 0.75d, 10, 10);

        assertThat(allocation.first()).isEqualTo(74);
        assertThat(allocation.second()).isEqualTo(25);
    }
}
