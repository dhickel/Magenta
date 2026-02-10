package com.magenta.io;

import com.magenta.io.terminal.Command;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the ReadResult ADT.
 */
class ReadResultTest {

    @Test
    void testInputCreation() {
        ReadResult.Input input = ReadResult.input("hello world");
        assertEquals("hello world", input.content());
        assertNotNull(input.timestamp());
        assertTrue(input.isInput());
        assertFalse(input.isCommand());
        assertFalse(input.isBlocked());
    }

    @Test
    void testCmdCreation() {
        Command exitCmd = Command.of("exit", "Exit", raw -> raw.equals("/exit"), (s, r) -> {});
        ReadResult.Cmd cmd = ReadResult.cmd(exitCmd, "/exit");
        assertEquals(exitCmd, cmd.command());
        assertNotNull(cmd.timestamp());
        assertTrue(cmd.isCommand());
        assertFalse(cmd.isInput());
        assertFalse(cmd.isBlocked());
    }

    @Test
    void testBlockedCreation() {
        ReadResult.Blocked blocked = ReadResult.blocked("rm -rf /", "Contains blocked pattern: rm -rf");
        assertEquals("rm -rf /", blocked.original());
        assertEquals("Contains blocked pattern: rm -rf", blocked.reason());
        assertNotNull(blocked.timestamp());
        assertTrue(blocked.isBlocked());
        assertFalse(blocked.isInput());
        assertFalse(blocked.isCommand());
    }

    @Test
    void testContentAccessor() {
        Command helpCmd = Command.of("help", "Help", raw -> raw.equals("/help"), (s, r) -> {});
        ReadResult input = ReadResult.input("test");
        ReadResult cmd = ReadResult.cmd(helpCmd, "/help");
        ReadResult blocked = ReadResult.blocked("original", "reason");

        assertEquals("test", input.content());
        assertEquals("/help", cmd.content());
        assertEquals("original", blocked.content());
    }

    @Test
    void testTimestampAccessor() {
        LocalDateTime before = LocalDateTime.now();
        ReadResult input = ReadResult.input("test");
        LocalDateTime after = LocalDateTime.now();

        assertTrue(input.timestamp().isAfter(before.minusSeconds(1)));
        assertTrue(input.timestamp().isBefore(after.plusSeconds(1)));
    }
}
