package com.magenta.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class FileSystemToolsTest {

    private FileSystemTools tools;
    private final String TEST_DIR = "test_sandbox";

    @BeforeEach
    void setUp() throws IOException {
        tools = new FileSystemTools();
        Files.createDirectories(Paths.get(TEST_DIR));
    }

    @AfterEach
    void tearDown() throws IOException {
        // Cleanup test directory
        tools.deleteDirectoryRecursive(TEST_DIR);
    }

    @Test
    void testWriteAndReadFile() {
        String filePath = TEST_DIR + "/hello.txt";
        String content = "Hello World";
        
        tools.writeFile(filePath, content);
        String read = tools.readFile(filePath, null, null);
        
        assertEquals(content, read);
    }

    @Test
    void testListDirectory() {
        tools.createDirectory(TEST_DIR + "/subdir");
        tools.writeFile(TEST_DIR + "/file1.txt", "content");
        
        String list = tools.listDirectory(TEST_DIR);
        assertTrue(list.contains("subdir"));
        assertTrue(list.contains("file1.txt"));
    }

    @Test
    void testDeleteFile() {
        String filePath = TEST_DIR + "/todelete.txt";
        tools.writeFile(filePath, "bye");
        
        tools.deleteFile(filePath);
        
        String result = tools.readFile(filePath, null, null);
        assertTrue(result.startsWith("Error"));
    }

    @Test
    void testReadFileChunk() {
        String filePath = TEST_DIR + "/chunk.txt";
        // \n is separator
        String content = "Line 1\nLine 2\nLine 3\nLine 4";
        tools.writeFile(filePath, content);

        // Read lines 2-3
        String chunk = tools.readFile(filePath, 2, 3);
        // Output format:
        // === path (lines 2-3 of 4) ===
        //    2 | Line 2
        //    3 | Line 3

        assertTrue(chunk.contains("Line 2"));
        assertTrue(chunk.contains("Line 3"));
        assertFalse(chunk.contains("Line 1"));
        assertFalse(chunk.contains("Line 4"));
    }

    @Test
    void testSearchReplace() {
        String filePath = TEST_DIR + "/replace.txt";
        tools.writeFile(filePath, "Hello World");

        // Preview
        String preview = tools.searchReplace(filePath, "World", "Java", false, false);
        assertTrue(preview.contains("Preview"));
        assertEquals("Hello World", tools.readFile(filePath, null, null));

        // Apply
        String result = tools.searchReplace(filePath, "World", "Java", false, true);
        assertTrue(result.contains("Applied"));
        assertEquals("Hello Java", tools.readFile(filePath, null, null));
    }
}
