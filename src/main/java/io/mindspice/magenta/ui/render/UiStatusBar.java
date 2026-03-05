package io.mindspice.magenta.ui.render;

public record UiStatusBar(
        String topLeft,
        String topRight,
        String bottomLeft,
        String bottomRight
) {
    public UiStatusBar {
        topLeft = topLeft == null ? "" : topLeft;
        topRight = topRight == null ? "" : topRight;
        bottomLeft = bottomLeft == null ? "" : bottomLeft;
        bottomRight = bottomRight == null ? "" : bottomRight;
    }
}
