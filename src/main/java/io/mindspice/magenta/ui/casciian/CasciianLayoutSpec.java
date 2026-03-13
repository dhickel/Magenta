package io.mindspice.magenta.ui.casciian;

public record CasciianLayoutSpec(
        double horizontalRatio,
        double verticalRatio,
        int minLeftCols,
        int minRightCols,
        int minTopRows,
        int minBottomRows
) {

    public static CasciianLayoutSpec defaults() {
        return new CasciianLayoutSpec(0.78d, 0.84d, 56, 24, 12, 6);
    }

    public Allocation allocateColumns(int totalWidth) {
        int usable = Math.max(1, totalWidth - 1);
        return allocate(usable, horizontalRatio, minLeftCols, minRightCols);
    }

    public Allocation allocateRows(int totalHeight) {
        int usable = Math.max(1, totalHeight - 1);
        return allocate(usable, verticalRatio, minTopRows, minBottomRows);
    }

    static Allocation allocate(int total, double ratio, int minPrimary, int minSecondary) {
        int boundedTotal = Math.max(1, total);
        double boundedRatio = Math.max(0.0d, Math.min(1.0d, ratio));
        int requestedPrimary = (int) Math.floor(boundedTotal * boundedRatio);
        int primary = Math.max(1, requestedPrimary);
        int secondary = Math.max(1, boundedTotal - primary);

        if (primary < minPrimary) {
            primary = Math.min(boundedTotal - 1, Math.max(1, minPrimary));
            secondary = boundedTotal - primary;
        }
        if (secondary < minSecondary) {
            secondary = Math.min(boundedTotal - 1, Math.max(1, minSecondary));
            primary = boundedTotal - secondary;
        }
        if (primary + secondary != boundedTotal) {
            secondary = Math.max(1, boundedTotal - primary);
            if (primary + secondary > boundedTotal) {
                primary = boundedTotal - secondary;
            }
        }
        return new Allocation(Math.max(1, primary), Math.max(1, secondary));
    }

    public record Allocation(int primary, int secondary) {
    }
}
