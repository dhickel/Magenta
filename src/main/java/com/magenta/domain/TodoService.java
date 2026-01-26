package com.magenta.domain;

import com.magenta.data.DatabaseService;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TodoService {
    private final List<Task> rootTasks = new ArrayList<>();
    private final PropertyChangeSupport support = new PropertyChangeSupport(this);
    private final DatabaseService dbService;

    public TodoService(DatabaseService dbService) {
        this.dbService = dbService;
        loadTasks();
    }

    private void loadTasks() {
        rootTasks.clear();
        Map<String, Task> taskMap = new HashMap<>();
        List<Task> allTasks = new ArrayList<>();

        try {
            Connection conn = dbService.getTaskConnection();
            if (conn == null) return; // Should not happen if init called

            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM tasks")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    Task t = new Task(
                        rs.getString("id"),
                        rs.getString("description"),
                        rs.getString("parent_id"),
                        rs.getBoolean("completed")
                    );
                    taskMap.put(t.getId(), t);
                    allTasks.add(t);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        // Reconstruct tree
        for (Task t : allTasks) {
            if (t.getParentId() == null || t.getParentId().isEmpty()) {
                rootTasks.add(t);
            } else {
                Task parent = taskMap.get(t.getParentId());
                if (parent != null) {
                    // Manually add to avoid re-setting parentId logic in addSubTask which might affect dirty checks if we had them
                    // But Task.addSubTask sets parentId. It's fine.
                    parent.addSubTask(t);
                } else {
                    rootTasks.add(t);
                }
            }
        }
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

        // Persist
        try (PreparedStatement stmt = dbService.getTaskConnection().prepareStatement("INSERT INTO tasks (id, description, parent_id, completed, created_at) VALUES (?, ?, ?, ?, ?)")) {
            stmt.setString(1, newTask.getId());
            stmt.setString(2, newTask.getDescription());
            stmt.setString(3, newTask.getParentId());
            stmt.setBoolean(4, newTask.isCompleted());
            stmt.setLong(5, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace(); // Log error
        }

        notifyUpdate();
        return newTask;
    }

    public boolean completeTask(String id) {
        Optional<Task> task = findTask(id);
        if (task.isPresent()) {
            task.get().setCompleted(true);
            
            // Persist
            try (PreparedStatement stmt = dbService.getTaskConnection().prepareStatement("UPDATE tasks SET completed = ? WHERE id = ?")) {
                stmt.setBoolean(1, true);
                stmt.setString(2, id);
                stmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }

            notifyUpdate();
            return true;
        }
        return false;
    }

    public boolean removeTask(String id) {
        // Recursive remove from memory and DB
        // Simple approach: Delete from DB by ID. If there are children, they become orphans or we should delete them too.
        // The recursive memory removal is already implemented. We should match it in DB.
        
        // Find the task first to identify children if needed?
        // Actually, let's just use the memory structure to find all IDs to delete.
        Optional<Task> target = findTask(id);
        if (target.isEmpty()) return false;
        
        List<String> idsToDelete = new ArrayList<>();
        collectIds(target.get(), idsToDelete);
        
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
            // Remove from DB
            try (PreparedStatement stmt = dbService.getTaskConnection().prepareStatement("DELETE FROM tasks WHERE id = ?")) {
                for (String deleteId : idsToDelete) {
                    stmt.setString(1, deleteId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
            notifyUpdate();
            return true;
        }
        return false;
    }

    private void collectIds(Task t, List<String> ids) {
        ids.add(t.getId());
        for (Task child : t.getSubTasks()) {
            collectIds(child, ids);
        }
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