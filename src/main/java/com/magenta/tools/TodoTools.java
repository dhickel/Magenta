package com.magenta.tools;

import com.magenta.domain.TodoService;
import dev.langchain4j.agent.tool.Tool;

public class TodoTools {
    private final TodoService service;

    public TodoTools(TodoService service) {
        this.service = service;
    }

    @Tool("Get the full hierarchical list of tasks.")
    public String getTodoList() {
        String list = service.getFormattedTree();
        return list.isEmpty() ? "The list is empty." : list;
    }

    @Tool("Add a new task. Optional: Provide a parentId to make it a subtask.")
    public String addTask(String description, String parentId) {
        try {
            var task = service.addTask(description, parentId);
            return "Added task: " + task.toString();
        } catch (IllegalArgumentException e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool("Mark a task as completed using its ID.")
    public String completeTask(String taskId) {
        boolean success = service.completeTask(taskId);
        return success ? "Task marked as completed." : "Task not found with ID: " + taskId;
    }

    @Tool("Remove a task using its ID.")
    public String removeTask(String taskId) {
        boolean success = service.removeTask(taskId);
        return success ? "Task removed." : "Task not found with ID: " + taskId;
    }
}
