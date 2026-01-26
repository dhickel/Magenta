package com.magenta.domain;

import com.magenta.data.DatabaseService;
import com.magenta.config.ConfigManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TodoServiceTest {

    private TodoService todoService;
    private DatabaseService dbService;
    private String tempStoragePath;

    @BeforeEach
    void setUp() throws Exception {
        tempStoragePath = Files.createTempDirectory("magenta_test").toString();
        
        File configFile = new File(tempStoragePath, "config.json");
        String json = "{\"global\":{\"storage_path\":\"" + tempStoragePath + "\"}, \"security\":{}}";
        Files.writeString(configFile.toPath(), json);
        
        ConfigManager.load(configFile.getAbsolutePath());

        dbService = new DatabaseService();
        dbService.init();
        
        todoService = new TodoService(dbService);
    }

    @AfterEach
    void tearDown() {
        if (dbService != null) dbService.close();
    }

    @Test
    void testAddAndRetrieveTask() {
        Task task = todoService.addTask("Buy milk", null);
        assertNotNull(task.getId());
        assertEquals("Buy milk", task.getDescription());
        
        Optional<Task> found = todoService.findTask(task.getId());
        assertTrue(found.isPresent());
    }

    @Test
    void testSubTask() {
        Task root = todoService.addTask("Project A", null);
        Task sub = todoService.addTask("Task 1", root.getId());

        assertEquals(root.getId(), sub.getParentId());
        assertEquals(1, root.getSubTasks().size());
        
        // Test recursive retrieval
        Optional<Task> foundSub = todoService.findTask(sub.getId());
        assertTrue(foundSub.isPresent());
    }

    @Test
    void testCompleteTask() {
        Task task = todoService.addTask("Sleep", null);
        assertFalse(task.isCompleted());
        
        todoService.completeTask(task.getId());
        assertTrue(task.isCompleted());
    }

    @Test
    void testRemoveTask() {
        Task task = todoService.addTask("Delete me", null);
        assertTrue(todoService.findTask(task.getId()).isPresent());
        
        todoService.removeTask(task.getId());
        assertFalse(todoService.findTask(task.getId()).isPresent());
    }
}