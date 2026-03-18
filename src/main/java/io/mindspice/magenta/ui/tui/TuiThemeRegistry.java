package io.mindspice.magenta.ui.tui;

import casciian.TApplication;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TuiThemeRegistry {
    public static final String DEFAULT_THEME = "dark";
    public static final String FEMME_THEME = "femme";

    private static final String BUILTIN_DARK = "dark";

    private final Map<String, TuiThemeProfile> profilesById;

    public TuiThemeRegistry(Path configRoot) {
        Objects.requireNonNull(configRoot, "configRoot");
        Map<String, TuiThemeProfile> loaded = new TuiThemeConfigLoader().load(configRoot);
        Map<String, TuiThemeProfile> merged = new LinkedHashMap<>();
        merged.putAll(builtins());
        loaded.values().forEach(profile -> merged.put(normalizeId(profile.id()), profile));
        this.profilesById = Map.copyOf(merged);
    }

    public List<TuiThemeProfile> profiles() {
        return profilesById.values().stream().toList();
    }

    public void apply(TApplication app, String themeId) {
        Objects.requireNonNull(app, "app");
        String normalized = normalizeId(themeId == null ? DEFAULT_THEME : themeId);
        TuiThemeProfile profile = profilesById.get(normalized);
        if (profile == null) {
            profile = profilesById.get(DEFAULT_THEME);
        }
        if (profile == null) {
            profile = builtins().get(BUILTIN_DARK);
        }
        applyProfile(app, profile);
    }

    private void applyProfile(TApplication app, TuiThemeProfile profile) {
        if (FEMME_THEME.equals(normalizeId(profile.base()))) {
            app.getTheme().setFemme();
        } else {
            app.getTheme().setDefaultTheme();
        }
        profile.colors().forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null || value.isBlank()) {
                return;
            }
            app.getTheme().setColorFromString(key.trim(), value.trim());
        });
    }

    private Map<String, TuiThemeProfile> builtins() {
        Map<String, String> darkColors = Map.ofEntries(
                Map.entry("tdesktop.background", "rgb: #111827 on #0b1220"),
                Map.entry("twindow.border", "rgb: #e2e8f0 on #1f2937"),
                Map.entry("twindow.background", "rgb: #d1d5db on #111827"),
                Map.entry("twindow.border.inactive", "rgb: #9ca3af on #1f2937"),
                Map.entry("twindow.background.inactive", "rgb: #a8b0bd on #111827"),
                Map.entry("twindow.border.modal", "rgb: #f8fafc on #334155"),
                Map.entry("twindow.background.modal", "rgb: #e5e7eb on #0f172a"),
                Map.entry("twindow.border.modal.inactive", "rgb: #9ca3af on #334155"),
                Map.entry("twindow.background.modal.inactive", "rgb: #cbd5e1 on #0f172a"),
                Map.entry("twindow.border.windowmove", "rgb: #93c5fd on #1f2937"),
                Map.entry("twindow.background.windowmove", "rgb: #d1d5db on #111827"),
                Map.entry("twindow.border.modal.windowmove", "rgb: #93c5fd on #334155"),
                Map.entry("tmenu", "rgb: #cbd5e1 on #111827"),
                Map.entry("tmenu.highlighted", "rgb: #0b1220 on #38bdf8"),
                Map.entry("tmenu.mnemonic", "rgb: #7dd3fc on #111827"),
                Map.entry("tmenu.mnemonic.highlighted", "rgb: #0b1220 on #38bdf8"),
                Map.entry("ttext", "rgb: #d1d5db on #111827"),
                Map.entry("tlabel", "rgb: #cbd5e1 on #111827"),
                Map.entry("tlabel.mnemonic", "rgb: #93c5fd on #111827"),
                Map.entry("tpanel.border", "rgb: #64748b on #111827"),
                Map.entry("tfield.active", "rgb: #e2e8f0 on #1e293b"),
                Map.entry("tfield.inactive", "rgb: #cbd5e1 on #111827"),
                Map.entry("teditor", "rgb: #d1d5db on #111827"),
                Map.entry("teditor.selected", "rgb: #0b1220 on #38bdf8"),
                Map.entry("teditor.margin", "rgb: #94a3b8 on #111827"),
                Map.entry("tscroller.bar", "rgb: #94a3b8 on #1e293b"),
                Map.entry("tscroller.arrows", "rgb: #e2e8f0 on #334155"),
                Map.entry("tstatusbar.text", "rgb: #cbd5e1 on #0b1220"),
                Map.entry("tstatusbar.button", "rgb: #7dd3fc on #0b1220"),
                Map.entry("tstatusbar.selected", "rgb: #0b1220 on #38bdf8"),
                Map.entry("magenta.transcript.user", "rgb: #f8fafc on #111827"),
                Map.entry("magenta.transcript.assistant", "rgb: #86efac on #111827"),
                Map.entry("magenta.transcript.tool", "rgb: #fde68a on #111827"),
                Map.entry("magenta.transcript.error", "rgb: #fca5a5 on #111827"),
                Map.entry("magenta.transcript.info", "rgb: #7dd3fc on #111827")
        );
        Map<String, TuiThemeProfile> builtins = new LinkedHashMap<>();
        builtins.put(BUILTIN_DARK, new TuiThemeProfile(BUILTIN_DARK, "Dark", "default", darkColors));
        builtins.put(FEMME_THEME, new TuiThemeProfile(FEMME_THEME, "Femme", "femme", Map.ofEntries(
                Map.entry("magenta.transcript.user", "bold white on magenta"),
                Map.entry("magenta.transcript.assistant", "bold green on magenta"),
                Map.entry("magenta.transcript.tool", "bold yellow on magenta"),
                Map.entry("magenta.transcript.error", "bold red on magenta"),
                Map.entry("magenta.transcript.info", "bold cyan on magenta")
        )));
        return builtins;
    }

    private String normalizeId(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
