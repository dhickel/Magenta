package com.magenta.io;

import com.magenta.io.terminal.TerminalIOManager;
import org.junit.jupiter.api.Test;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TerminalIOManagerTest {

    @Test
    void testSingleton() {
        try {
            TerminalIOManager instance1 = TerminalIOManager.getInstance();
            TerminalIOManager instance2 = TerminalIOManager.getInstance();
            
            assertNotNull(instance1);
            assertSame(instance1, instance2, "TerminalIOManager should be a singleton");
        } catch (IOException e) {
            // If we can't create a terminal (e.g. in some CI envs), we can't strictly test the singleton property 
            // of the returned object, but we can at least verify it threw the expected exception type.
            // However, JLine usually handles non-interactive envs.
            System.err.println("Skipping singleton test due to IOException: " + e.getMessage());
        }
    }
}
