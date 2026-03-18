package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TCommand;
import casciian.TWindow;
import casciian.event.TCommandEvent;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactoryRegistry;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceOverlayStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class WorkspaceHost {
    private final Map<String, WorkspaceDefinition> workspacesById;
    private final WindowKindFactoryRegistry windowKindRegistry;
    private final WorkspaceOverlayStore overlayStore;
    private final Observer observer;
    private final Consumer<RuntimeException> errorHandler;

    private final Map<String, WorkspaceState> statesByWorkspaceId = new LinkedHashMap<>();
    private String activeWorkspaceId;

    public WorkspaceHost(
            Map<String, WorkspaceDefinition> workspacesById,
            WindowKindFactoryRegistry windowKindRegistry,
            WorkspaceOverlayStore overlayStore,
            Observer observer,
            Consumer<RuntimeException> errorHandler
    ) {
        this.workspacesById = validateWorkspaceMap(workspacesById);
        this.windowKindRegistry = Objects.requireNonNull(windowKindRegistry, "windowKindRegistry");
        this.overlayStore = Objects.requireNonNull(overlayStore, "overlayStore");
        this.observer = observer == null ? event -> { } : observer;
        this.errorHandler = errorHandler == null ? error -> { } : errorHandler;
        this.activeWorkspaceId = this.workspacesById.keySet().iterator().next();
    }

    public synchronized List<WorkspaceOption> workspaceOptions() {
        return workspacesById.values().stream()
                .map(workspace -> new WorkspaceOption(workspace.id(), workspace.name()))
                .toList();
    }

    public synchronized String activeWorkspaceId() {
        return activeWorkspaceId;
    }

    public synchronized String switchWorkspace(String workspaceId, TuiApplication app) {
        return runAction("workspace_switch", activeWorkspaceId, null, () -> {
            Objects.requireNonNull(app, "app");
            String targetWorkspaceId = normalizeWorkspaceId(workspaceId);
            WorkspaceDefinition target = workspacesById.get(targetWorkspaceId);
            if (target == null) {
                return "Unknown workspace: " + targetWorkspaceId;
            }

            WorkspaceState previous = statesByWorkspaceId.computeIfAbsent(activeWorkspaceId, ignored -> new WorkspaceState());
            hideWorkspace(previous, app);

            WorkspaceState next = statesByWorkspaceId.computeIfAbsent(targetWorkspaceId, ignored -> new WorkspaceState());
            if (next.overlay == null) {
                next.overlay = overlayStore.load(targetWorkspaceId);
            }

            showWorkspace(target, next, app);
            activeWorkspaceId = targetWorkspaceId;
            emit("workspace_switch", targetWorkspaceId, null, "success", "allowed",
                    "Switched workspace to '" + target.name() + "' (" + target.id() + ")");
            return "Switched workspace to '" + target.name() + "' (" + target.id() + ")";
        });
    }

    public synchronized String saveActiveWorkspaceSnapshot(TuiApplication app) {
        return runAction("workspace_save", activeWorkspaceId, null, () -> {
            Objects.requireNonNull(app, "app");
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            WorkspaceOverlayStore.Overlay overlay = buildOverlay(workspace, state, app);
            overlayStore.save(workspace.id(), overlay);
            state.overlay = overlay;
            String message = "Saved workspace layout overlay: " + workspace.id();
            emit("workspace_save", workspace.id(), null, "success", "allowed", message);
            return message;
        });
    }

    public synchronized String loadActiveWorkspaceSnapshot(TuiApplication app) {
        return runAction("workspace_load", activeWorkspaceId, null, () -> {
            Objects.requireNonNull(app, "app");
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceOverlayStore.Overlay overlay = overlayStore.load(workspace.id());
            if (overlay == null) {
                return "No saved layout overlay for workspace '" + workspace.id() + "'";
            }
            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            state.overlay = overlay;
            showWorkspace(workspace, state, app);
            String message = "Loaded workspace layout overlay: " + workspace.id();
            emit("workspace_load", workspace.id(), null, "success", "allowed", message);
            return message;
        });
    }

    public synchronized List<WindowOption> hiddenWindows() {
        WorkspaceDefinition workspace = activeWorkspace();
        WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
        return workspace.windows().stream()
                .filter(descriptor -> {
                    WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
                    return instance != null && instance.window.isHidden();
                })
                .map(descriptor -> new WindowOption(descriptor.id(), descriptor.title()))
                .toList();
    }

    public synchronized List<WindowOption> addableWindows() {
        WorkspaceDefinition workspace = activeWorkspace();
        WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
        return workspace.windows().stream()
                .filter(descriptor -> {
                    WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
                    return instance == null || instance.window.isHidden();
                })
                .map(descriptor -> new WindowOption(descriptor.id(), descriptor.title()))
                .toList();
    }

    public synchronized String openWindow(String windowId, TuiApplication app) {
        return runAction("window_open", activeWorkspaceId, windowId, () -> {
            Objects.requireNonNull(app, "app");
            if (windowId == null || windowId.isBlank()) {
                return "Window id is required";
            }
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceDefinition.WindowDescriptor descriptor = workspace.windows().stream()
                    .filter(candidate -> candidate.id().equals(windowId.trim()))
                    .findFirst()
                    .orElse(null);
            if (descriptor == null) {
                return "Unknown window for workspace '" + workspace.id() + "': " + windowId;
            }

            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            WindowInstance instance = ensureWindow(descriptor, state, app);
            WorkspaceOverlayStore.OverlayWindowState overlayState = overlayWindowState(state.overlay, descriptor.id());
            applyWindowState(instance.window, descriptor.geometry(), overlayState, app);
            instance.window.show();
            instance.window.activate();
            state.activeWindowHint = descriptor.id();
            String message = "Opened window '" + descriptor.title() + "'";
            emit("window_open", workspace.id(), descriptor.id(), "success", "allowed", message);
            return message;
        });
    }

    public synchronized TWindow firstWindowByKind(String kind) {
        if (kind == null || kind.isBlank()) {
            return null;
        }
        String normalizedKind = normalizeKind(kind);
        WorkspaceState state = statesByWorkspaceId.get(activeWorkspaceId);
        if (state == null) {
            return null;
        }
        WorkspaceDefinition workspace = activeWorkspace();
        for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
            if (!normalizeKind(descriptor.kind()).equals(normalizedKind)) {
                continue;
            }
            WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
            if (instance != null) {
                return instance.window;
            }
        }
        return null;
    }

    public synchronized String describeHiddenWindows() {
        List<WindowOption> hidden = hiddenWindows();
        if (hidden.isEmpty()) {
            return "No hidden windows in workspace '" + activeWorkspaceId + "'";
        }
        return "Hidden windows in workspace '" + activeWorkspaceId + "': "
                + hidden.stream().map(WindowOption::title).reduce((left, right) -> left + ", " + right).orElse("");
    }

    public synchronized void recordWindowAction(String actionType, TWindow window, String message) {
        if (window == null) {
            emit(actionType, activeWorkspaceId, null, "success", "allowed", message);
            return;
        }
        WorkspaceState state = statesByWorkspaceId.get(activeWorkspaceId);
        String windowId = null;
        if (state != null) {
            windowId = state.instancesByWindowId.values().stream()
                    .filter(candidate -> candidate.window == window)
                    .map(candidate -> candidate.descriptor.id())
                    .findFirst()
                    .orElse(null);
        }
        emit(actionType, activeWorkspaceId, windowId, "success", "allowed", message);
    }

    private void showWorkspace(WorkspaceDefinition workspace, WorkspaceState state, TuiApplication app) {
        LinkedHashSet<String> visibleWindowIds = new LinkedHashSet<>();
        for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
            WorkspaceOverlayStore.OverlayWindowState overlayState = overlayWindowState(state.overlay, descriptor.id());
            boolean visible = overlayState != null && overlayState.visible() != null
                    ? overlayState.visible()
                    : descriptor.visible();

            if (!visible) {
                WindowInstance existing = state.instancesByWindowId.get(descriptor.id());
                if (existing != null && app.hasWindow(existing.window)) {
                    existing.window.hide();
                }
                continue;
            }

            WindowInstance instance = ensureWindow(descriptor, state, app);
            applyWindowState(instance.window, descriptor.geometry(), overlayState, app);
            instance.window.show();
            visibleWindowIds.add(descriptor.id());
        }

        if (state.overlay == null) {
            applyWorkspaceLayoutDefault(workspace, app);
        }

        String activeWindowId = resolveActiveWindowHint(state, visibleWindowIds, workspace);
        if (activeWindowId != null) {
            WindowInstance instance = state.instancesByWindowId.get(activeWindowId);
            if (instance != null) {
                instance.window.activate();
                state.activeWindowHint = activeWindowId;
            }
        }
    }

    private void hideWorkspace(WorkspaceState state, TuiApplication app) {
        for (WindowInstance instance : state.instancesByWindowId.values()) {
            if (app.hasWindow(instance.window) && !instance.window.isHidden()) {
                instance.window.hide();
            }
        }
    }

    private WindowInstance ensureWindow(
            WorkspaceDefinition.WindowDescriptor descriptor,
            WorkspaceState state,
            TuiApplication app
    ) {
        WindowInstance existing = state.instancesByWindowId.get(descriptor.id());
        if (existing != null) {
            return existing;
        }

        TWindow window = windowKindRegistry.require(descriptor.kind()).create(descriptor, app);
        WindowInstance created = new WindowInstance(descriptor, window);
        state.instancesByWindowId.put(descriptor.id(), created);
        emit("window_open", activeWorkspaceId, descriptor.id(), "success", "allowed",
                "Created window '" + descriptor.title() + "' with kind '" + descriptor.kind() + "'");
        return created;
    }

    private WorkspaceOverlayStore.Overlay buildOverlay(
            WorkspaceDefinition workspace,
            WorkspaceState state,
            TuiApplication app
    ) {
        Map<String, WorkspaceOverlayStore.OverlayWindowState> windows = new LinkedHashMap<>();
        for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
            WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
            WorkspaceOverlayStore.OverlayWindowState prior = overlayWindowState(state.overlay, descriptor.id());
            if (instance == null) {
                windows.put(descriptor.id(), prior == null
                        ? new WorkspaceOverlayStore.OverlayWindowState(descriptor.visible(), Boolean.FALSE, descriptor.geometry())
                        : prior);
                continue;
            }
            TWindow window = instance.window;
            WorkspaceDefinition.Geometry geometry = geometryFromWindow(window, app);
            windows.put(descriptor.id(), new WorkspaceOverlayStore.OverlayWindowState(
                    !window.isHidden(),
                    inferMaximized(window, app),
                    geometry
            ));
        }

        String activeWindowId = null;
        TWindow activeWindow = app.getActiveWindow();
        if (activeWindow != null) {
            activeWindowId = state.instancesByWindowId.values().stream()
                    .filter(candidate -> candidate.window == activeWindow)
                    .map(candidate -> candidate.descriptor.id())
                    .findFirst()
                    .orElse(state.activeWindowHint);
        }
        return new WorkspaceOverlayStore.Overlay(activeWindowId, windows);
    }

    private WorkspaceOverlayStore.OverlayWindowState overlayWindowState(
            WorkspaceOverlayStore.Overlay overlay,
            String windowId
    ) {
        if (overlay == null) {
            return null;
        }
        return overlay.windows().get(windowId);
    }

    private void applyWindowState(
            TWindow window,
            WorkspaceDefinition.Geometry defaultGeometry,
            WorkspaceOverlayStore.OverlayWindowState overlayState,
            TuiApplication app
    ) {
        WorkspaceDefinition.Geometry geometry = overlayState != null && overlayState.geometry() != null
                ? overlayState.geometry()
                : defaultGeometry;
        if (geometry != null) {
            applyGeometry(window, geometry, app);
        }

        Boolean maximized = overlayState == null ? null : overlayState.maximized();
        if (Boolean.TRUE.equals(maximized)) {
            window.maximize();
            return;
        }

        if (inferMaximized(window, app)) {
            window.restore();
            if (geometry != null) {
                applyGeometry(window, geometry, app);
            }
        }
    }

    private void applyGeometry(TWindow window, WorkspaceDefinition.Geometry geometry, TApplication app) {
        int desktopTop = app.getDesktopTop();
        window.setDimensions(geometry.x(), geometry.y() + desktopTop, geometry.width(), geometry.height());
    }

    private boolean inferMaximized(TWindow window, TApplication app) {
        return window.getX() == 0
                && window.getY() == app.getDesktopTop()
                && window.getWidth() == app.getScreen().getWidth()
                && window.getHeight() == Math.max(1, app.getDesktopBottom() - app.getDesktopTop());
    }

    private WorkspaceDefinition.Geometry geometryFromWindow(TWindow window, TApplication app) {
        int yRelative = Math.max(0, window.getY() - app.getDesktopTop());
        return new WorkspaceDefinition.Geometry(window.getX(), yRelative, window.getWidth(), window.getHeight());
    }

    private String resolveActiveWindowHint(
            WorkspaceState state,
            LinkedHashSet<String> visibleWindowIds,
            WorkspaceDefinition workspace
    ) {
        if (visibleWindowIds.isEmpty()) {
            WorkspaceDefinition.WindowDescriptor first = workspace.windows().getFirst();
            return first.id();
        }

        String overlayHint = state.overlay == null ? null : state.overlay.activeWindowId();
        if (overlayHint != null && visibleWindowIds.contains(overlayHint)) {
            return overlayHint;
        }
        if (state.activeWindowHint != null && visibleWindowIds.contains(state.activeWindowHint)) {
            return state.activeWindowHint;
        }
        return visibleWindowIds.iterator().next();
    }

    private void applyWorkspaceLayoutDefault(WorkspaceDefinition workspace, TuiApplication app) {
        if (workspace.layoutMode() == WorkspaceDefinition.LayoutMode.CASCADE) {
            app.postMenuEvent(new TCommandEvent(app.getBackend(), TCommand.cmCascade));
            return;
        }
        app.postMenuEvent(new TCommandEvent(app.getBackend(), TCommand.cmTile));
    }

    private WorkspaceDefinition activeWorkspace() {
        WorkspaceDefinition workspace = workspacesById.get(activeWorkspaceId);
        if (workspace == null) {
            throw new IllegalStateException("No active workspace: " + activeWorkspaceId);
        }
        return workspace;
    }

    private String normalizeWorkspaceId(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return activeWorkspaceId;
        }
        return workspaceId.trim();
    }

    private String normalizeKind(String kind) {
        return kind == null ? "" : kind.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, WorkspaceDefinition> validateWorkspaceMap(Map<String, WorkspaceDefinition> workspacesById) {
        if (workspacesById == null || workspacesById.isEmpty()) {
            throw new IllegalStateException("Workspace registry must not be empty");
        }
        Map<String, WorkspaceDefinition> ordered = new LinkedHashMap<>();
        workspacesById.values().stream()
                .sorted(Comparator.comparing(WorkspaceDefinition::id))
                .forEach(workspace -> {
                    if (ordered.putIfAbsent(workspace.id(), workspace) != null) {
                        throw new IllegalStateException("Duplicate workspace id: " + workspace.id());
                    }
                });
        return Map.copyOf(ordered);
    }

    private String runAction(String type, String workspaceId, String windowId, Action action) {
        try {
            return action.run();
        } catch (RuntimeException e) {
            emit(type, workspaceId, windowId, "failed", "validation_error", e.getMessage());
            errorHandler.accept(e);
            return "Workspace action failed: " + (e.getMessage() == null ? type : e.getMessage());
        }
    }

    private void emit(
            String type,
            String workspaceId,
            String windowId,
            String status,
            String code,
            String message
    ) {
        observer.onEvent(new WorkspaceEvent(type, workspaceId, windowId, status, code, message));
    }

    @FunctionalInterface
    private interface Action {
        String run();
    }

    @FunctionalInterface
    public interface Observer {
        void onEvent(WorkspaceEvent event);
    }

    public record WorkspaceEvent(
            String type,
            String workspaceId,
            String windowId,
            String status,
            String code,
            String message
    ) {
    }

    public record WorkspaceOption(String id, String name) {
    }

    public record WindowOption(String id, String title) {
    }

    private static final class WorkspaceState {
        private final Map<String, WindowInstance> instancesByWindowId = new LinkedHashMap<>();
        private WorkspaceOverlayStore.Overlay overlay;
        private String activeWindowHint;
    }

    private record WindowInstance(WorkspaceDefinition.WindowDescriptor descriptor, TWindow window) {
    }
}
