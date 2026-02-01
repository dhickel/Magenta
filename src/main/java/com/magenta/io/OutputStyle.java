package com.magenta.io;

import org.jline.utils.AttributedStyle;

public enum OutputStyle {
    ERROR(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)),
    INFO(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)),
    PROMPT(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold()),
    SECURITY(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold());

    private final AttributedStyle style;

    OutputStyle(AttributedStyle style) {
        this.style = style;
    }

    public AttributedStyle style() {
        return style;
    }
}
