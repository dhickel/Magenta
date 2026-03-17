package io.mindspice.magenta.ui.tui;

import casciian.TApplication;

import java.util.Objects;

public final class TuiThemeRegistry {
    public static final String DEFAULT_THEME = "default";
    public static final String FEMME_THEME = "femme";

    public void apply(TApplication app, String themeId) {
        Objects.requireNonNull(app, "app");
        String normalized = themeId == null ? DEFAULT_THEME : themeId.trim().toLowerCase(java.util.Locale.ROOT);
        if (FEMME_THEME.equals(normalized)) {
            app.getTheme().setFemme();
            return;
        }
        app.getTheme().setDefaultTheme();
    }
}
