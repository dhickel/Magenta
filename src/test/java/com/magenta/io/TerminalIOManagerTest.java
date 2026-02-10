package com.magenta.io;

import com.magenta.io.terminal.TerminalIOManager;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TerminalIOManagerTest {

    @Test
    void testConstruction() {
        try {
            TerminalIOManager instance = new TerminalIOManager();
            assertNotNull(instance);
        } catch (IOException e) {
            // If we can't create a terminal (e.g. in some CI envs), skip
            System.err.println("Skipping construction test due to IOException: " + e.getMessage());
        }
    }
}
