package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TWindow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class WorkspaceHost {
    private final Map<String, List<TWindow>> windowsByWorkspace = new LinkedHashMap<>();
    private String activeWorkspaceId = "default";

    public synchronized void registerWindow(String workspaceId, TWindow window) {
        Objects.requireNonNull(window, "window");
        String id = normalizeWorkspaceId(workspaceId);
        windowsByWorkspace.computeIfAbsent(id, ignored -> new ArrayList<>()).add(window);
    }

    public synchronized void switchWorkspace(String workspaceId, TApplication app) {
        Objects.requireNonNull(app, "app");
        String nextWorkspaceId = normalizeWorkspaceId(workspaceId);
        windowsByWorkspace.computeIfAbsent(activeWorkspaceId, ignored -> new ArrayList<>());
        windowsByWorkspace.computeIfAbsent(nextWorkspaceId, ignored -> new ArrayList<>());

        if (!activeWorkspaceId.equals(nextWorkspaceId)) {
            for (TWindow window : windowsByWorkspace.getOrDefault(activeWorkspaceId, List.of())) {
                if (app.hasWindow(window)) {
                    window.hide();
                }
            }
        }

        for (TWindow window : windowsByWorkspace.getOrDefault(nextWorkspaceId, List.of())) {
            if (app.hasWindow(window)) {
                window.show();
            }
        }
        activeWorkspaceId = nextWorkspaceId;
    }

    public synchronized String activeWorkspaceId() {
        return activeWorkspaceId;
    }

    public synchronized TWindow firstHiddenWindow() {
        return windowsByWorkspace
                .getOrDefault(activeWorkspaceId, List.of())
                .stream()
                .filter(TWindow::isHidden)
                .findFirst()
                .orElse(null);
    }

    public synchronized String saveActiveWorkspaceSnapshot() {
        return "Workspace save placeholder for '" + activeWorkspaceId + "'";
    }

    public synchronized String loadActiveWorkspaceSnapshot() {
        return "Workspace load placeholder for '" + activeWorkspaceId + "'";
    }

    public synchronized String describeHiddenWindows() {
        List<String> hiddenTitles = windowsByWorkspace
                .getOrDefault(activeWorkspaceId, List.of())
                .stream()
                .filter(TWindow::isHidden)
                .map(window -> {
                    String title = window.getTitle();
                    return title == null || title.isBlank() ? "(untitled window)" : title;
                })
                .collect(Collectors.toList());

        if (hiddenTitles.isEmpty()) {
            return "No hidden windows in workspace '" + activeWorkspaceId + "'";
        }
        return "Hidden windows in workspace '" + activeWorkspaceId + "': " + String.join(", ", hiddenTitles);
    }

    private String normalizeWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return "default";
        }
        return workspaceId.trim();
    }
}
