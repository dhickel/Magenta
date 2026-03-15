package io.mindspice.magenta.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.BasicTextImage;
import com.googlecode.lanterna.graphics.TextImage;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.graphics.ThemeStyle;
import com.googlecode.lanterna.gui2.Component;
import com.googlecode.lanterna.gui2.ImageComponent;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.LayoutManager;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.util.List;
import java.util.Objects;

final class FillSplitPanel extends Panel {
    enum Orientation {
        HORIZONTAL,
        VERTICAL
    }

    private static final int DIVIDER_THICKNESS = 1;

    private final Component first;
    private final Component second;
    private final Divider divider;
    private final Orientation orientation;

    private double ratio;
    private int minimumFirst = 1;
    private int minimumSecond = 1;

    static FillSplitPanel horizontal(Component first, Component second, double ratio) {
        return new FillSplitPanel(first, second, Orientation.HORIZONTAL, ratio);
    }

    static FillSplitPanel vertical(Component first, Component second, double ratio) {
        return new FillSplitPanel(first, second, Orientation.VERTICAL, ratio);
    }

    FillSplitPanel(Component first, Component second, Orientation orientation, double ratio) {
        this.first = Objects.requireNonNull(first, "first");
        this.second = Objects.requireNonNull(second, "second");
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        this.divider = new Divider();
        this.ratio = clampRatio(ratio);
        setLayoutManager(new FillSplitLayout());
        addComponent(first);
        addComponent(divider);
        addComponent(second);
    }

    void setMinimumPrimarySizes(int firstMinimum, int secondMinimum) {
        this.minimumFirst = Math.max(1, firstMinimum);
        this.minimumSecond = Math.max(1, secondMinimum);
        invalidate();
    }

    void setRatio(double ratio) {
        this.ratio = clampRatio(ratio);
        invalidate();
    }

    void adjustBy(int delta) {
        int available = availablePrimarySpace();
        Allocation allocation = allocate(available, ratio, minimumFirst, minimumSecond);
        int updatedFirst = Math.max(minimumFirst, Math.min(available - minimumSecond, allocation.first() + delta));
        if (available < minimumFirst + minimumSecond) {
            updatedFirst = Math.max(1, Math.min(available - 1, allocation.first() + delta));
        }
        setRatio(available <= 0 ? ratio : (double) updatedFirst / (double) available);
    }

    static Allocation allocate(int availablePrimary, double ratio, int minimumFirst, int minimumSecond) {
        int available = Math.max(0, availablePrimary);
        if (available <= 1) {
            return new Allocation(Math.max(0, available), 0);
        }
        int firstMin = Math.max(1, minimumFirst);
        int secondMin = Math.max(1, minimumSecond);
        int first = (int) Math.round(available * clampRatio(ratio));
        first = Math.max(firstMin, Math.min(available - secondMin, first));
        if (available < firstMin + secondMin) {
            first = Math.max(1, Math.min(available - 1, first));
        }
        int second = Math.max(1, available - first);
        return new Allocation(first, second);
    }

    private int availablePrimarySpace() {
        TerminalSize size = getSize();
        int primary = orientation == Orientation.HORIZONTAL ? size.getColumns() : size.getRows();
        return Math.max(0, primary - DIVIDER_THICKNESS);
    }

    private static double clampRatio(double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return 0.5d;
        }
        return Math.max(0.05d, Math.min(0.95d, ratio));
    }

    record Allocation(int first, int second) {}

    private final class FillSplitLayout implements LayoutManager {
        @Override
        public TerminalSize getPreferredSize(List<Component> components) {
            TerminalSize firstSize = first.getPreferredSize();
            TerminalSize secondSize = second.getPreferredSize();
            return switch (orientation) {
                case HORIZONTAL -> new TerminalSize(
                        firstSize.getColumns() + DIVIDER_THICKNESS + secondSize.getColumns(),
                        Math.max(firstSize.getRows(), secondSize.getRows())
                );
                case VERTICAL -> new TerminalSize(
                        Math.max(firstSize.getColumns(), secondSize.getColumns()),
                        firstSize.getRows() + DIVIDER_THICKNESS + secondSize.getRows()
                );
            };
        }

        @Override
        public void doLayout(TerminalSize size, List<Component> components) {
            int width = Math.max(1, size.getColumns());
            int height = Math.max(1, size.getRows());
            if (orientation == Orientation.HORIZONTAL) {
                Allocation allocation = allocate(width - DIVIDER_THICKNESS, ratio, minimumFirst, minimumSecond);
                first.setPosition(TerminalPosition.TOP_LEFT_CORNER);
                first.setSize(new TerminalSize(Math.max(1, allocation.first()), height));
                divider.setPosition(new TerminalPosition(allocation.first(), 0));
                divider.setSize(new TerminalSize(DIVIDER_THICKNESS, height));
                second.setPosition(new TerminalPosition(allocation.first() + DIVIDER_THICKNESS, 0));
                second.setSize(new TerminalSize(Math.max(1, width - allocation.first() - DIVIDER_THICKNESS), height));
            } else {
                Allocation allocation = allocate(height - DIVIDER_THICKNESS, ratio, minimumFirst, minimumSecond);
                first.setPosition(TerminalPosition.TOP_LEFT_CORNER);
                first.setSize(new TerminalSize(width, Math.max(1, allocation.first())));
                divider.setPosition(new TerminalPosition(0, allocation.first()));
                divider.setSize(new TerminalSize(width, DIVIDER_THICKNESS));
                second.setPosition(new TerminalPosition(0, allocation.first() + DIVIDER_THICKNESS));
                second.setSize(new TerminalSize(width, Math.max(1, height - allocation.first() - DIVIDER_THICKNESS)));
            }
            divider.refreshImage();
        }

        @Override
        public boolean hasChanged() {
            return true;
        }
    }

    private final class Divider extends ImageComponent {
        private TerminalPosition dragStart;
        private double dragStartRatio;

        void refreshImage() {
            TerminalSize dividerSize = getSize();
            if (dividerSize.getColumns() <= 0 || dividerSize.getRows() <= 0) {
                return;
            }
            TextImage image = new BasicTextImage(dividerSize);
            ThemeDefinition definition = getThemeDefinition();
            ThemeStyle style = definition == null ? null : definition.getActive();
            char dividerChar = orientation == Orientation.HORIZONTAL ? '│' : '─';
            if (style != null) {
                image.setAll(new TextCharacter(dividerChar, style.getForeground(), style.getBackground()));
            } else {
                image.setAll(new TextCharacter(dividerChar));
            }
            setTextImage(image);
        }

        @Override
        public synchronized Interactable.Result handleKeyStroke(KeyStroke keyStroke) {
            if (!(keyStroke instanceof MouseAction mouseAction)) {
                return Interactable.Result.UNHANDLED;
            }
            MouseActionType type = mouseAction.getActionType();
            if (type == MouseActionType.CLICK_DOWN) {
                dragStart = mouseAction.getPosition();
                dragStartRatio = ratio;
                return Interactable.Result.HANDLED;
            }
            if (type == MouseActionType.DRAG && dragStart != null) {
                TerminalPosition delta = mouseAction.getPosition().minus(dragStart);
                int available = availablePrimarySpace();
                int primaryDelta = orientation == Orientation.HORIZONTAL ? delta.getColumn() : delta.getRow();
                int baseline = (int) Math.round(dragStartRatio * available);
                int updated = baseline + primaryDelta;
                if (available > 0) {
                    setRatio((double) updated / (double) available);
                }
                return Interactable.Result.HANDLED;
            }
            if (type == MouseActionType.CLICK_RELEASE) {
                dragStart = null;
                return Interactable.Result.HANDLED;
            }
            return Interactable.Result.UNHANDLED;
        }
    }
}
