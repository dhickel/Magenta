package com.magenta.task;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TodoService {
    private final List<Task> rootTasks = new ArrayList<>();
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);

    public TodoService() {
        // In-memory only, no initial load
    }

    public Task addTask(String description, String parentId) {
        Task newTask = new Task(description);
        
        if (parentId == null || parentId.isEmpty()) {
            rootTasks.add(newTask);
        } else {
            Optional<Task> parent = findTask(parentId);
            if (parent.isPresent()) {
                parent.get().addSubTask(newTask);
            } else {
                throw new IllegalArgumentException("Parent task not found: " + parentId);
            }
        }

        notifyUpdate();
        return newTask;
    }

    public boolean completeTask(String id) {
        Optional<Task> task = findTask(id);
        if (task.isPresent()) {
            task.get().setCompleted(true);
            notifyUpdate();
            return true;
        }
        return false;
    }

    public boolean removeTask(String id) {
        Optional<Task> target = findTask(id);
        if (target.isEmpty()) return false;
        
        // Remove from memory
        boolean removed = rootTasks.removeIf(t -> t.getId().equals(id));
        if (!removed) {
            for (Task root : rootTasks) {
                if (removeRecursive(root, id)) {
                    removed = true;
                    break;
                }
            }
        }

        if (removed) {
            notifyUpdate();
            return true;
        }
        return false;
    }

    private boolean removeRecursive(Task parent, String targetId) {
        boolean removed = parent.removeSubTask(targetId);
        if (removed) return true;

        for (Task child : parent.getSubTasks()) {
            if (removeRecursive(child, targetId)) return true;
        }
        return false;
    }

    public Optional<Task> findTask(String id) {
        for (Task root : rootTasks) {
            if (root.getId().equals(id)) return Optional.of(root);
            Optional<Task> found = findRecursive(root, id);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    private Optional<Task> findRecursive(Task parent, String targetId) {
        for (Task child : parent.getSubTasks()) {
            if (child.getId().equals(targetId)) return Optional.of(child);
            Optional<Task> found = findRecursive(child, targetId);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    public String getFormattedTree() {
        StringBuilder sb = new StringBuilder();
        for (Task root : rootTasks) {
            appendTask(sb, root, 0);
        }
        return sb.toString();
    }

    private void appendTask(StringBuilder sb, Task task, int depth) {
        String indent = "  ".repeat(depth);
        sb.append(indent).append(task.toString()).append("\n");
        for (Task child : task.getSubTasks()) {
            appendTask(sb, child, depth + 1);
        }
    }

    private void notifyUpdate() {
        support.firePropertyChange("todos", null, getFormattedTree());
    }

    public void addListener(PropertyChangeListener listener) {
        support.addPropertyChangeListener(listener);
    }
}
