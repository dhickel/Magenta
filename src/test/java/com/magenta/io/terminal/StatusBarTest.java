package com.magenta.io.terminal;

import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatusBarTest {

    // === alignRight Tests ===

    @Test
    void testAlignRightPadsToWidth() {
        AttributedString status = new AttributedString("test");
        AttributedString aligned = StatusBar.alignRight(status, 20);

        String text = aligned.toString();
        assertTrue(text.endsWith("test"));
        // padding = 20 - 4 - 1 = 15, total = 15 + 4 = 19
        assertEquals(19, text.length());
    }

    @Test
    void testAlignRightPreservesStyle() {
        AttributedStyle greenStyle = AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);
        AttributedString status = new AttributedString("ok", greenStyle);
        AttributedString aligned = StatusBar.alignRight(status, 20);

        // The styled portion ("ok") should retain green foreground
        // Padding portion should be unstyled
        String text = aligned.toString();
        assertTrue(text.endsWith("ok"));

        // Check that the styled characters retain their style
        int okStart = text.length() - 2;
        AttributedStyle styleAtOk = aligned.styleAt(okStart);
        assertEquals(greenStyle.getStyle(), styleAtOk.getStyle());
    }

    @Test
    void testAlignRightNoPaddingWhenTextWiderThanWidth() {
        AttributedString status = new AttributedString("very long text");
        AttributedString aligned = StatusBar.alignRight(status, 5);

        // No negative padding - text should remain intact
        assertTrue(aligned.toString().contains("very long text"));
    }

    @Test
    void testAlignRightExactWidth() {
        // status length 4, width 5 -> padding = 5 - 4 - 1 = 0
        AttributedString status = new AttributedString("test");
        AttributedString aligned = StatusBar.alignRight(status, 5);

        assertEquals("test", aligned.toString());
    }

    @Test
    void testAlignRightEmptyString() {
        AttributedString status = new AttributedString("");
        AttributedString aligned = StatusBar.alignRight(status, 20);

        // padding = 20 - 0 - 1 = 19
        assertEquals(19, aligned.toString().length());
        assertTrue(aligned.toString().isBlank());
    }

    @Test
    void testAlignRightWidthOne() {
        AttributedString status = new AttributedString("x");
        AttributedString aligned = StatusBar.alignRight(status, 1);

        // padding = max(0, 1 - 1 - 1) = 0
        assertEquals("x", aligned.toString());
    }

    // === Method Reference Compatibility ===

    @Test
    void testFullMatchesFunctionalInterface() {
        // Verify StatusBar::full can be used as a StatusBar method reference
        // This is a compile-time check - if this compiles, the contract is satisfied
        StatusBar bar = StatusBar::full;
        assertNotNull(bar);
    }

    @Test
    void testAlignedMatchesFunctionalInterface() {
        StatusBar bar = StatusBar::aligned;
        assertNotNull(bar);
    }

    @Test
    void testCompactMatchesFunctionalInterface() {
        StatusBar bar = StatusBar::compact;
        assertNotNull(bar);
    }

    @Test
    void testLambdaMatchesFunctionalInterface() {
        // Custom lambdas should also work
        StatusBar custom = (session, width) -> new AttributedString("custom");
        assertNotNull(custom);
    }
}
