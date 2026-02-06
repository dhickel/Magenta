package com.magenta.io.terminal;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Table rendering utility using ASCII-table library.
 * Provides Magenta-specific table formatting with borders and alignment.
 */
public class TableRenderer {

    /**
     * Render a simple table with columns.
     *
     * @param items Table rows
     * @param columns Column definitions (header, extractor, alignment)
     * @return Rendered ASCII table
     */
    public static <T> String renderTable(List<T> items, List<ColumnDef<T>> columns) {
        List<ColumnData<T>> columnData = columns.stream()
            .map(col -> createColumn(col.header(), col.extractor(), col.align()))
            .toList();

        return AsciiTable.getTable(items, columnData);
    }

    /**
     * Render table with border and title.
     * Adds Unicode box drawing around table.
     *
     * @param title Table title (shown in top border)
     * @param items Table rows
     * @param columns Column definitions
     * @return Rendered table with border
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
     * @return Rendered indexed table
     */
    public static <T> String renderIndexedTable(String title, String dataHeader,
                                                List<T> items, Function<T, String> dataFunc) {
        List<ColumnDef<IndexedItem<T>>> columns = List.of(
            new ColumnDef<>("Index", item -> String.valueOf(item.index()), ColumnDef.Align.RIGHT),
            new ColumnDef<>(dataHeader, item -> dataFunc.apply(item.value()), ColumnDef.Align.LEFT)
        );

        List<IndexedItem<T>> indexed = IntStream.range(0, items.size())
            .mapToObj(i -> new IndexedItem<>(i + 1, items.get(i)))
            .toList();

        return renderTableWithBorder(title, indexed, columns);
    }

    // === Private Helpers ===

    private static <T> ColumnData<T> createColumn(String header, Function<T, String> extractor,
                                                  ColumnDef.Align align) {
        Column col = new Column()
            .header(header)
            .headerAlign(HorizontalAlign.CENTER)
            .dataAlign(alignToHorizontal(align))
            .maxWidth(80, OverflowBehaviour.CLIP_LEFT);

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

    /**
     * Column definition for table rendering.
     *
     * @param header Column header text
     * @param extractor Function to extract column value from row item
     * @param align Column alignment (LEFT, RIGHT, CENTER)
     */
    public record ColumnDef<T>(String header, Function<T, String> extractor, Align align) {
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
