package com.magenta.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class SearchToolsTest {

    private SearchTools tools;
    private final String TEST_DIR = "test_search_sandbox";

    @BeforeEach
    void setUp() throws IOException {
        tools = new SearchTools();
        Path testPath = Paths.get(TEST_DIR);
        if (!Files.exists(testPath)) {
            Files.createDirectories(testPath);
        }
        Files.writeString(testPath.resolve("TestClass.java"), "class TestClass { void testMethod() {} }");
    }

    @Test
    void testFindDefinition() {
        String result = tools.findDefinition("TestClass");
        assertNotNull(result);

        // Note: The SearchTools implementation scans the actual project root, so it might find real classes
        // or our test file if it's in the scan path. Since we created it in current dir, it should be found.

        // Cleanup
        try {
            Files.deleteIfExists(Paths.get(TEST_DIR).resolve("TestClass.java"));
            Files.deleteIfExists(Paths.get(TEST_DIR));
        } catch (IOException e) {
            // ignore
        }
    }
}
