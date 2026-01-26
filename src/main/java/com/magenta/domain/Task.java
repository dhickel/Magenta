package com.magenta.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Task {
    private final String id;
    private String description;
    private boolean isCompleted;
    private final List<Task> subTasks;
    private String parentId;

    public Task(String description) {
        this.id = UUID.randomUUID().toString().substring(0, 8); // Short ID for easier handling
        this.description = description;
        this.isCompleted = false;
        this.subTasks = new ArrayList<>();
        this.parentId = null;
    }

    // Constructor for DB hydration
    public Task(String id, String description, String parentId, boolean isCompleted) {
        this.id = id;
        this.description = description;
        this.parentId = parentId;
        this.isCompleted = isCompleted;
        this.subTasks = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isCompleted() {
        return isCompleted;
    }

    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    public List<Task> getSubTasks() {
        return subTasks;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public void addSubTask(Task task) {
        task.setParentId(this.id);
        this.subTasks.add(task);
    }

    public boolean removeSubTask(String taskId) {
        return subTasks.removeIf(t -> t.getId().equals(taskId));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (ID: %s) - Subtasks: %d", 
            isCompleted ? "X" : " ", description, id, subTasks.size());
    }
}
