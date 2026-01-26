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
        String read = tools.readFile(filePath);
        
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
        
        String result = tools.readFile(filePath);
        assertTrue(result.startsWith("Error"));
    }
}
