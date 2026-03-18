package io.mindspice.magenta.ui.tui;

import java.util.Map;

public record TuiThemeProfile(
        String id,
        String name,
        String base,
        Map<String, String> colors
) {
    public TuiThemeProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("theme id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("theme name must not be blank");
        }
        base = base == null || base.isBlank() ? "default" : base.trim().toLowerCase(java.util.Locale.ROOT);
        colors = colors == null ? Map.of() : Map.copyOf(colors);
    }
}
