package com.magenta.io.terminal;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TerminalDisplayTest {

    private Terminal terminal;
    private TerminalDisplay display;

    @BeforeEach
    void setUp() throws IOException {
        terminal = TerminalBuilder.builder()
            .system(false)
            .dumb(true)
            .build();
        display = new TerminalDisplay(terminal);
    }

    @Test
    void testGetTerminalSize() {
        TerminalDisplay.Size size = display.getTerminalSize();
        assertNotNull(size);
        assertTrue(size.width() > 0);
        assertTrue(size.height() > 0);
    }

    @Test
    void testDrawHorizontalLine() {
        AttributedString line = display.drawHorizontalLine(10, '─');
        assertEquals("──────────", line.toString());
    }

    @Test
    void testDrawHorizontalLineZeroWidth() {
        AttributedString line = display.drawHorizontalLine(0, '─');
        assertEquals("", line.toString());
    }

    @Test
    void testPadRight() {
        AttributedString padded = display.padRight("test", 10);
        assertEquals("test      ", padded.toString());
    }

    @Test
    void testPadLeft() {
        AttributedString padded = display.padLeft("test", 10);
        assertEquals("      test", padded.toString());
    }

    @Test
    void testRightAlign() {
        AttributedString aligned = display.rightAlign("test", 20);
        String result = aligned.toString();
        assertTrue(result.endsWith("test"));
        assertEquals(20 - 1, result.length());
    }

    @Test
    void testRightAlignTextWiderThanTerminal() {
        AttributedString aligned = display.rightAlign("very long text", 5);
        // Should not add negative padding
        assertTrue(aligned.toString().contains("very long text"));
    }

    @Test
    void testDrawBox() {
        List<AttributedString> content = List.of(
            new AttributedString("Line 1"),
            new AttributedString("Line 2")
        );
        AttributedString box = display.drawBox("Title", 20, content);

        String boxStr = box.toString();
        assertTrue(boxStr.contains("Title"));
        assertTrue(boxStr.contains("┌"));
        assertTrue(boxStr.contains("┐"));
        assertTrue(boxStr.contains("│"));
        assertTrue(boxStr.contains("└"));
        assertTrue(boxStr.contains("┘"));
    }

    @Test
    void testDrawBoxEmptyContent() {
        AttributedString box = display.drawBox("Empty", 20, List.of());

        String boxStr = box.toString();
        assertTrue(boxStr.contains("Empty"));
        assertTrue(boxStr.contains("┌"));
        assertTrue(boxStr.contains("└"));
        // No content lines between borders
        assertFalse(boxStr.contains("│"));
    }

    @Test
    void testTerminalAccessor() {
        assertSame(terminal, display.terminal());
    }
}
