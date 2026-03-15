package io.mindspice.magenta.ui.casciian;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CasciianPrimitivesTest {

    @Test
    void splitAllocationHonorsMinimumsWhenRequestedRatioIsTooSmall() {
        CasciianLayoutSpec.Allocation allocation = CasciianLayoutSpec.allocate(79, 0.10d, 56, 24);

        assertThat(allocation.primary()).isEqualTo(55);
        assertThat(allocation.secondary()).isEqualTo(24);
    }

    @Test
    void splitAllocationTracksRatioWhenSpaceAllows() {
        CasciianLayoutSpec.Allocation allocation = CasciianLayoutSpec.allocate(99, 0.75d, 10, 10);

        assertThat(allocation.primary()).isEqualTo(74);
        assertThat(allocation.secondary()).isEqualTo(25);
    }

    @Test
    void messageFormattingUsesWordWrapAndRoleHeader() {
        String block = CasciianMessageFormatter.block("assistant", "alpha beta gamma delta epsilon", 18);

        assertThat(block).startsWith(" assistant ");
        assertThat(block).contains("alpha beta gamma");
        assertThat(block).contains("delta epsilon");
    }
}
