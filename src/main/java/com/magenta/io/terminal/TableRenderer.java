package com.magenta.io.terminal;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Table rendering utility using ASCII-table library.
 * Provides Magenta-specific table formatting with borders, alignment, and helper utilities.
 */
public class TableRenderer {

    // Default column width constraints
    private static final int DEFAULT_MIN_WIDTH = 0;
    private static final int DEFAULT_MAX_WIDTH = 80;
    private static final OverflowBehaviour DEFAULT_OVERFLOW = OverflowBehaviour.CLIP_LEFT;

    /**
     * Render a simple table with columns.
     *
     * @param items Table rows
     * @param columns Column definitions
     * @return Rendered ASCII table
     */
    public static <T> String renderTable(List<T> items, List<ColumnDef<T>> columns) {
        List<ColumnData<T>> columnData = columns.stream()
            .map(col -> createColumn(col.header(), col.extractor(), col.align(), col.minWidth(), col.maxWidth()))
            .toList();

        return AsciiTable.getTable(items, columnData);
    }

    /**
     * Render table with border and title.
     *
     * @param title Table title (shown in top border)
     * @param items Table rows
     * @param columns Column definitions
     * @return Rendered table with border and title
     */
    public static <T> String renderTableWithBorder(String title, List<T> items,
                                                   List<ColumnDef<T>> columns) {
        String table = renderTable(items, columns);
        return addTableHeader(title, table);
    }

    /**
     * Create indexed table (Index | Data columns).
     * Useful for numbered lists where user can reference by index.
     *
     * @param title Table title
     * @param dataHeader Header for data column
     * @param items Table items
     * @param dataFunc Extract display string from item
     * @return Rendered indexed table with border
     */
    public static <T> String renderIndexedTable(String title, String dataHeader,
                                                List<T> items, Function<T, String> dataFunc) {
        List<ColumnDef<IndexedItem<T>>> columns = List.of(
            new ColumnDef<>("Index", item -> String.valueOf(item.index()), ColumnDef.Align.RIGHT, 5, 8),
            new ColumnDef<>(dataHeader, item -> dataFunc.apply(item.value()), ColumnDef.Align.LEFT)
        );

        List<IndexedItem<T>> indexed = IntStream.range(0, items.size())
            .mapToObj(i -> new IndexedItem<>(i + 1, items.get(i)))
            .toList();

        return renderTableWithBorder(title, indexed, columns);
    }

    /**
     * Create a key-value pair table.
     * Displays key and value columns side by side.
     *
     * @param title Table title
     * @param items Items to display
     * @param keyFunc Extract key from item
     * @param valFunc Extract value from item
     * @return Rendered key-value table with border
     */
    public static <T> String renderKeyValueTable(String title, List<T> items,
                                                 Function<T, String> keyFunc, Function<T, String> valFunc) {
        List<ColumnDef<T>> columns = List.of(
            new ColumnDef<>("Key", keyFunc, ColumnDef.Align.LEFT),
            new ColumnDef<>("Value", valFunc, ColumnDef.Align.LEFT)
        );
        return renderTableWithBorder(title, items, columns);
    }

    /**
     * Create a single-column table.
     *
     * @param header Column header
     * @param items Items to display
     * @param extractor Extract display string from item
     * @return Rendered single-column table
     */
    public static <T> String renderSingleColumn(String header, List<T> items, Function<T, String> extractor) {
        List<ColumnDef<T>> columns = List.of(new ColumnDef<>(header, extractor, ColumnDef.Align.LEFT));
        return renderTable(items, columns);
    }

    // === Text Utilities ===

    /**
     * Center a string within a given width.
     *
     * @param text Text to center
     * @param width Target width
     * @return Centered text with padding
     */
    public static String centerString(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int totalPad = width - text.length();
        int padStart = totalPad / 2;
        int padEnd = totalPad - padStart;
        return " ".repeat(padStart) + text + " ".repeat(padEnd);
    }

    /**
     * Center text to match the width of a reference string.
     *
     * @param text Text to center
     * @param reference Reference string (first line width is used)
     * @return Centered text
     */
    public static String centerStringToMatch(String text, String reference) {
        int width = reference.split("\n")[0].length();
        return centerString(text, width);
    }

    /**
     * Wrap text to fit within a maximum line width.
     *
     * @param text Text to wrap
     * @param maxWidth Maximum line width
     * @return Text with lines wrapped
     */
    public static String wrapText(String text, int maxWidth) {
        List<String> result = new ArrayList<>();
        for (String line : text.split("\n")) {
            wrapLine(line, maxWidth, result);
        }
        return String.join("\n", result);
    }

    /**
     * Pad string to a specific width with spaces.
     *
     * @param text Text to pad
     * @param width Target width
     * @return Padded text (left-aligned)
     */
    public static String padString(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        return text + " ".repeat(width - text.length());
    }

    /**
     * Merge multiple tables horizontally with padding between them.
     *
     * @param padding Horizontal padding between tables
     * @param tables Tables to merge
     * @return Merged table
     */
    public static String mergeTablesHorizontally(int padding, String... tables) {
        if (tables.length == 0) {
            return "";
        }

        List<List<String>> rows = new ArrayList<>();
        int maxRows = 0;

        // Split each table into rows
        for (String table : tables) {
            List<String> tableRows = List.of(table.split("\n"));
            rows.add(new ArrayList<>(tableRows));
            maxRows = Math.max(maxRows, tableRows.size());
        }

        // Pad all rows to same height
        String padStr = " ".repeat(Math.max(1, rows.get(0).get(0).length()));
        for (List<String> tableRows : rows) {
            while (tableRows.size() < maxRows) {
                tableRows.add(padStr);
            }
        }

        // Merge rows horizontally
        StringBuilder result = new StringBuilder();
        String horizontalPad = " ".repeat(padding);
        for (int i = 0; i < maxRows; i++) {
            int rowIndex = i;
            String merged = rows.stream()
                .map(tableRows -> tableRows.get(rowIndex))
                .reduce((a, b) -> a + horizontalPad + b)
                .orElse("");
            result.append(merged);
            if (i < maxRows - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    // === Private Helpers ===

    private static <T> ColumnData<T> createColumn(String header, Function<T, String> extractor,
                                                  ColumnDef.Align align, int minWidth, int maxWidth) {
        Column col = new Column()
            .header(header)
            .headerAlign(HorizontalAlign.CENTER)
            .dataAlign(alignToHorizontal(align));

        if (minWidth > 0) {
            col.minWidth(minWidth);
        }
        if (maxWidth > 0) {
            col.maxWidth(maxWidth, DEFAULT_OVERFLOW);
        }

        return col.with(extractor);
    }

    private static HorizontalAlign alignToHorizontal(ColumnDef.Align align) {
        return switch (align) {
            case LEFT -> HorizontalAlign.LEFT;
            case RIGHT -> HorizontalAlign.RIGHT;
            case CENTER -> HorizontalAlign.CENTER;
        };
    }

    private static String addTableHeader(String header, String table) {
        String[] splitTable = table.split("\n");
        if (splitTable.length == 0) {
            return table;
        }

        int tableLen = splitTable[0].length();
        header = " " + header + " ";

        // Top border with title
        String top = "┌─ " + header + " "
                   + "─".repeat(Math.max(0, tableLen - header.length() - 5)) + "┐";

        // Replace first line border with connected border
        splitTable[0] = "├" + splitTable[0].substring(1, tableLen - 1) + "┤";

        return top + "\n" + String.join("\n", splitTable);
    }

    private static void wrapLine(String line, int maxWidth, List<String> result) {
        if (line.length() <= maxWidth) {
            result.add(line);
            return;
        }

        // Find last space within maxWidth
        int lastSpace = -1;
        for (int i = 0; i < Math.min(maxWidth, line.length()); i++) {
            if (line.charAt(i) == ' ') {
                lastSpace = i;
            }
        }

        if (lastSpace == -1) {
            // No space found, split at maxWidth
            result.add(line.substring(0, maxWidth));
            wrapLine(line.substring(maxWidth), maxWidth, result);
        } else {
            // Split at last space
            result.add(line.substring(0, lastSpace));
            wrapLine(line.substring(lastSpace + 1), maxWidth, result);
        }
    }

    /**
     * Column definition for table rendering.
     *
     * @param header Column header text
     * @param extractor Function to extract column value from row item
     * @param align Column alignment (LEFT, RIGHT, CENTER)
     * @param minWidth Minimum column width (0 = no minimum)
     * @param maxWidth Maximum column width (0 = no maximum)
     */
    public record ColumnDef<T>(String header, Function<T, String> extractor, Align align, int minWidth, int maxWidth) {
        /**
         * Create ColumnDef with default width constraints (no min, 80 max).
         */
        public ColumnDef(String header, Function<T, String> extractor, Align align) {
            this(header, extractor, align, DEFAULT_MIN_WIDTH, DEFAULT_MAX_WIDTH);
        }

        public enum Align { LEFT, RIGHT, CENTER }
    }

    /**
     * Indexed item for numbered tables.
     *
     * @param index Item index (1-based for display)
     * @param value Actual item value
     */
    record IndexedItem<T>(int index, T value) {}
}
