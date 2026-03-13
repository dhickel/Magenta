package io.mindspice.magenta.ui.casciian;

import casciian.bits.CellAttributes;
import casciian.bits.ColorTheme;

public final class CasciianTheme {

    private CasciianTheme() {
    }

    public static void applyDarkMinimal(ColorTheme theme) {
        // Keep the baseline dark and restrained to avoid retro/cascading-window aesthetics.
        theme.setColor(ColorTheme.TDESKTOP_BACKGROUND, rgb(0x14, 0x18, 0x22, 0x14, 0x18, 0x22));
        theme.setColor(ColorTheme.TWINDOW_BACKGROUND, rgb(0xC8, 0xD0, 0xDB, 0x14, 0x18, 0x22));
        theme.setColor(ColorTheme.TWINDOW_BORDER, rgb(0x7A, 0x88, 0x9A, 0x14, 0x18, 0x22));
        theme.setColor(ColorTheme.TWINDOW_BACKGROUND_INACTIVE, rgb(0x9F, 0xAB, 0xBA, 0x11, 0x14, 0x1D));
        theme.setColor(ColorTheme.TWINDOW_BORDER_INACTIVE, rgb(0x5E, 0x69, 0x79, 0x11, 0x14, 0x1D));

        theme.setColor(ColorTheme.TPANEL_BORDER, rgb(0x7A, 0x88, 0x9A, 0x14, 0x18, 0x22));
        theme.setColor(ColorTheme.TTEXT, rgb(0xD5, 0xDE, 0xEB, 0x14, 0x18, 0x22));
        theme.setColor(ColorTheme.TFIELD_ACTIVE, rgb(0xD5, 0xDE, 0xEB, 0x1A, 0x20, 0x2B));
        theme.setColor(ColorTheme.TFIELD_INACTIVE, rgb(0xB9, 0xC3, 0xD2, 0x17, 0x1D, 0x27));
        theme.setColor(ColorTheme.TEDITOR, rgb(0xD5, 0xDE, 0xEB, 0x1A, 0x20, 0x2B));
        theme.setColor(ColorTheme.TEDITOR_MARGIN, rgb(0x87, 0x93, 0xA3, 0x1A, 0x20, 0x2B));

        theme.setColor(ColorTheme.TSCROLLER_BAR, rgb(0xB6, 0xC0, 0xCF, 0x22, 0x2B, 0x38));
        theme.setColor(ColorTheme.TSCROLLER_ARROWS, rgb(0x9F, 0xAB, 0xBA, 0x1A, 0x20, 0x2B));
        theme.setColor(ColorTheme.TSPLITPANE, rgb(0x7A, 0x88, 0x9A, 0x14, 0x18, 0x22));
    }

    public static CellAttributes roleBlock(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "user" -> rgb(0xCF, 0xDA, 0xF0, 0x2A, 0x35, 0x49);
            case "assistant", "magenta" -> rgb(0xC5, 0xDF, 0xD8, 0x2A, 0x4A, 0x44);
            case "tool" -> rgb(0xE4, 0xD4, 0xB2, 0x5B, 0x49, 0x2A);
            case "warn", "warning" -> rgb(0xF0, 0xDE, 0xA8, 0x5A, 0x44, 0x14);
            case "error" -> rgb(0xF1, 0xC7, 0xC7, 0x5D, 0x27, 0x2D);
            default -> rgb(0xCF, 0xD8, 0xE5, 0x2B, 0x31, 0x3D);
        };
    }

    private static CellAttributes rgb(int fr, int fg, int fb, int br, int bg, int bb) {
        CellAttributes attrs = new CellAttributes();
        attrs.setForeColorRGB(casciian.bits.Rgb.combineRgb(fr, fg, fb));
        attrs.setBackColorRGB(casciian.bits.Rgb.combineRgb(br, bg, bb));
        return attrs;
    }
}
