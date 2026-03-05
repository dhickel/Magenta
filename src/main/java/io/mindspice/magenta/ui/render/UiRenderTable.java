package io.mindspice.magenta.ui.render;

import java.util.List;

public record UiRenderTable(
        List<String> headers,
        List<List<String>> rows
) {
    public UiRenderTable {
        headers = headers == null ? List.of() : List.copyOf(headers);
        rows = rows == null ? List.of() : rows.stream().map(row -> row == null ? List.<String>of() : List.copyOf(row)).toList();
    }
}
