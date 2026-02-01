package com.magenta.io;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Message ADT.
 */
class MessageTest {

    @Test
    void testInputMessageCreation() {
        Message.Input input = Message.input("hello world");
        assertEquals("hello world", input.content());
        assertFalse(input.isFiltered());
        assertNull(input.filterReason());
    }

    @Test
    void testOutputMessageCreation() {
        Message.Output output = Message.output("response text");
        assertEquals("response text", output.content());
        assertNull(output.colorCode());
        assertFalse(output.isFiltered());
    }

    @Test
    void testOutputWithColor() {
        Message.Output colored = Message.output("colored text", 5);
        assertEquals("colored text", colored.content());
        assertEquals(5, colored.colorCode());
    }

    @Test
    void testSystemMessage() {
        Message.System system = Message.system("system message");
        assertEquals("system message", system.content());
        assertEquals(OutputStyle.INFO, system.style());
    }

    @Test
    void testFilteredMessage() {
        Message.Filtered filtered = Message.blocked(
            "rm -rf /",
            "Contains blocked pattern: rm -rf",
            Message.FilterType.INPUT
        );

        assertTrue(filtered.isFiltered());
        assertEquals("rm -rf /", filtered.content());
        assertEquals("Contains blocked pattern: rm -rf", filtered.filterReason());
        assertEquals(Message.FilterType.INPUT, filtered.filterType());
    }

    @Test
    void testConvenienceOf() {
        Message msg = Message.of("simple text");
        assertTrue(msg instanceof Message.Output);
        assertEquals("simple text", msg.content());
    }
}
