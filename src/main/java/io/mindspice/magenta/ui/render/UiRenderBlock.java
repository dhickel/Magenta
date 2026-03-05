package io.mindspice.magenta.ui.render;

import java.util.List;

public record UiRenderBlock(
        String title,
        List<String> lines,
        UiStyle style
) {
    public UiRenderBlock {
        title = title == null ? "" : title;
        lines = lines == null ? List.of() : List.copyOf(lines);
        style = style == null ? UiStyle.DEFAULT : style;
    }
}
