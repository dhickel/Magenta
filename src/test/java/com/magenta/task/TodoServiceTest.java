package com.magenta.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TodoServiceTest {

    private TodoService todoService;

    @BeforeEach
    void setUp() {
        todoService = new TodoService();
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
