package com.magenta.io;

/**
 * Functional interface for applying color formatting to text.
 * Implementations can apply ANSI color codes, or ignore color entirely.
 */
@FunctionalInterface
public interface ColorPipe {
    /**
     * Apply color formatting to text.
     * @param text The text to format
     * @param colorCode The color code to apply
     * @return Formatted text (may ignore color and return original text)
     */
    String apply(String text, int colorCode);

    /**
     * Identity color pipe that ignores color codes and returns text unchanged.
     */
    static ColorPipe identity() {
        return (text, code) -> text;
    }
}
