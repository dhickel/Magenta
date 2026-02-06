package com.magenta.io.terminal;

import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.Display;
import org.jline.utils.InfoCmp;

import java.util.List;

/**
 * Stateless terminal rendering utilities.
 * Wraps JLine's Terminal and Display classes for Magenta-specific operations.
 *
 * <p>This class is purely functional - no session state, no cached values.
 * All methods accept the data they need and return rendered results.
 */
public class TerminalDisplay {
    private final Terminal terminal;
    private final Display display;

    public TerminalDisplay(Terminal terminal) {
        this.terminal = terminal;
        this.display = new Display(terminal, false);
    }

    // === Screen Management ===

    /**
     * Clear the entire screen.
     */
    public void clear() {
        terminal.puts(InfoCmp.Capability.clear_screen);
        terminal.flush();
    }

    /**
     * Update terminal with new lines.
     * Uses JLine's Display for efficient multi-line updates.
     *
     * @param lines Lines to display
     */
    public void updateLines(List<AttributedString> lines) {
        display.update(lines, 0);
    }

    /**
     * Move cursor to specific position (0-indexed).
     *
     * @param row Row position
     * @param col Column position
     */
    public void moveCursor(int row, int col) {
        terminal.puts(InfoCmp.Capability.cursor_address, row, col);
        terminal.flush();
    }

    // === Drawing Utilities ===

    /**
     * Draw a box with title and content.
     * Uses Unicode box drawing characters.
     *
     * @param title Box title (displayed in top border)
     * @param width Box width in characters
     * @param content Content lines (will be padded to fit)
     * @return Rendered box as AttributedString
     */
    public AttributedString drawBox(String title, int width, List<AttributedString> content) {
        var box = new StringBuilder();

        // Top border with title
        box.append("┌─ ").append(title).append(" ");
        box.append("─".repeat(Math.max(0, width - title.length() - 4)));
        box.append("┐\n");

        // Content lines
        for (AttributedString line : content) {
            String lineStr = line.toAnsi(terminal);
            if (lineStr.length() > width - 2) {
                lineStr = lineStr.substring(0, width - 2);
            }
            box.append("│ ").append(lineStr);
            box.append(" ".repeat(Math.max(0, width - lineStr.length() - 2)));
            box.append(" │\n");
        }

        // Bottom border
        box.append("└").append("─".repeat(width)).append("┘");

        return new AttributedString(box.toString());
    }

    /**
     * Draw a horizontal line with specified character.
     *
     * @param width Line width in characters
     * @param ch Character to repeat
     * @return Line as AttributedString
     */
    public AttributedString drawHorizontalLine(int width, char ch) {
        return new AttributedString(String.valueOf(ch).repeat(width));
    }

    /**
     * Pad text to specified width (left-aligned, padded on right).
     *
     * @param text Text to pad
     * @param width Target width
     * @return Padded text
     */
    public AttributedString padRight(String text, int width) {
        return new AttributedString(String.format("%-" + width + "s", text));
    }

    /**
     * Pad text to specified width (right-aligned, padded on left).
     *
     * @param text Text to pad
     * @param width Target width
     * @return Padded text
     */
    public AttributedString padLeft(String text, int width) {
        return new AttributedString(String.format("%" + width + "s", text));
    }

    /**
     * Right-align text within terminal width.
     * Adds padding on left to push text to right edge.
     *
     * @param text Text to align
     * @param terminalWidth Current terminal width
     * @return Right-aligned text with padding
     */
    public AttributedString rightAlign(String text, int terminalWidth) {
        int padding = Math.max(0, terminalWidth - text.length() - 1);
        return new AttributedString(" ".repeat(padding) + text);
    }

    // === Size Queries ===

    /**
     * Get current terminal size.
     *
     * @return Terminal dimensions (width, height)
     */
    public Size getTerminalSize() {
        return new Size(terminal.getWidth(), terminal.getHeight());
    }

    /**
     * Get underlying JLine Terminal.
     * Use sparingly - prefer higher-level methods.
     *
     * @return JLine Terminal instance
     */
    public Terminal terminal() {
        return terminal;
    }

    /**
     * Terminal dimensions.
     *
     * @param width Terminal width in characters
     * @param height Terminal height in lines
     */
    public record Size(int width, int height) {}
}
