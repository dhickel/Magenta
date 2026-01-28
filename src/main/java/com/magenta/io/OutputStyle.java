package com.magenta.io;

import org.jline.utils.AttributedStyle;

public enum OutputStyle {
    PLAIN(AttributedStyle.DEFAULT),
    ERROR(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED)),
    WARNING(AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW)),
    SUCCESS(AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN)),
    INFO(AttributedStyle.DEFAULT.foreground(AttributedStyle.CYAN)),
    AGENT(AttributedStyle.DEFAULT),
    PROMPT(AttributedStyle.DEFAULT.foreground(AttributedStyle.BLUE).bold()),
    SECURITY(AttributedStyle.DEFAULT.foreground(AttributedStyle.RED).bold()),
    COMMAND(AttributedStyle.DEFAULT.foreground(AttributedStyle.MAGENTA));

    private final AttributedStyle style;

    OutputStyle(AttributedStyle style) {
        this.style = style;
    }

    public AttributedStyle style() {
        return style;
    }
}
