package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TWindow;
import io.mindspice.magenta.ui.tui.chat.ChatWindow;
import io.mindspice.magenta.ui.tui.workspace.WindowKindFactoryRegistry;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceOverlayStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
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
    private final Map<TWindow, WindowBinding> bindingsByWindow = new IdentityHashMap<>();
    private final List<Runnable> stateListeners = new ArrayList<>();
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

    public synchronized void addStateListener(Runnable listener) {
        if (listener != null) {
            stateListeners.add(listener);
        }
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
            ensureOverlayState(activeWorkspace(), previous);
            hideWorkspace(previous, app);

            WorkspaceState next = statesByWorkspaceId.computeIfAbsent(targetWorkspaceId, ignored -> new WorkspaceState());
            ensureOverlayState(target, next);
            activeWorkspaceId = targetWorkspaceId;
            showWorkspace(target, next, app);
            emit("workspace_switch", targetWorkspaceId, null, "success", "allowed",
                    "Switched workspace to '" + target.name() + "' (" + target.id() + ")");
            notifyStateChanged();
            return "Switched workspace to '" + target.name() + "' (" + target.id() + ")";
        });
    }

    public synchronized String saveActiveWorkspaceSnapshot(TuiApplication app) {
        return runAction("workspace_save", activeWorkspaceId, null, () -> {
            Objects.requireNonNull(app, "app");
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            ensureOverlayState(workspace, state);
            WorkspaceOverlayStore.Overlay overlay = buildOverlay(state, app);
            overlayStore.save(workspace.id(), overlay);
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
            applyOverlay(workspace, state, overlay, true);
            showWorkspace(workspace, state, app);
            String message = "Loaded workspace layout overlay: " + workspace.id();
            emit("workspace_load", workspace.id(), null, "success", "allowed", message);
            notifyStateChanged();
            return message;
        });
    }

    public synchronized List<WindowMenuEntry> windowMenuEntries() {
        WorkspaceDefinition workspace = activeWorkspace();
        WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
        ensureOverlayState(workspace, state);
        return workspace.windows().stream()
                .map(descriptor -> {
                    WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
                    WorkspaceOverlayStore.OverlayWindowState overlayState = state.overlayWindowsById.get(descriptor.id());
                    boolean visible = instance != null ? !instance.window.isHidden() : Boolean.TRUE.equals(overlayState.visible());
                    boolean instantiated = instance != null;
                    boolean maximized = overlayState != null && Boolean.TRUE.equals(overlayState.maximized());
                    return new WindowMenuEntry(descriptor.id(), descriptor.title(), visible, maximized, instantiated);
                })
                .toList();
    }

    public synchronized String focusOrRestoreWindow(String windowId, TuiApplication app) {
        return runAction("window_focus", activeWorkspaceId, windowId, () -> {
            Objects.requireNonNull(app, "app");
            if (windowId == null || windowId.isBlank()) {
                return "Window id is required";
            }
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            ensureOverlayState(workspace, state);
            WorkspaceDefinition.WindowDescriptor descriptor = descriptorFor(workspace, windowId.trim());
            if (descriptor == null) {
                return "Unknown window for workspace '" + workspace.id() + "': " + windowId;
            }

            WindowInstance instance = state.instancesByWindowId.get(descriptor.id());
            if (instance == null) {
                WindowInstance created = ensureWindow(workspace.id(), descriptor, state, app);
                WorkspaceOverlayStore.OverlayWindowState overlayState = state.overlayWindowsById.get(descriptor.id());
                WorkspaceOverlayStore.OverlayWindowState openState = overlayStateForOpen(overlayState, descriptor.geometry());
                withLifecycleSyncSuppressed(state, () -> {
                    applyWindowState(created.window, descriptor.geometry(), openState, app);
                    created.window.show();
                });
                syncWindowShown(created.window);
                created.window.activate();
                state.activeWindowHint = descriptor.id();
                emit("window_open", workspace.id(), descriptor.id(), "success", "allowed",
                        "Opened window '" + descriptor.title() + "'");
                return "Opened window '" + descriptor.title() + "'";
            }

            if (instance.window.isHidden()) {
                instance.window.show();
                instance.window.activate();
                state.activeWindowHint = descriptor.id();
                emit("window_restore", workspace.id(), descriptor.id(), "success", "allowed",
                        "Restored window '" + descriptor.title() + "'");
                return "Restored window '" + descriptor.title() + "'";
            }

            instance.window.activate();
            state.activeWindowHint = descriptor.id();
            emit("window_focus", workspace.id(), descriptor.id(), "success", "allowed",
                    "Focused window '" + descriptor.title() + "'");
            return "Focused window '" + descriptor.title() + "'";
        });
    }

    public synchronized String hideActiveWindow(TuiApplication app) {
        Objects.requireNonNull(app, "app");
        TWindow activeWindow = app.getActiveWindow();
        if (activeWindow == null) {
            return "No active window to hide";
        }
        WindowBinding binding = bindingsByWindow.get(activeWindow);
        if (binding == null) {
            return "Active window is not managed by the workspace host";
        }
        if (activeWindow.isHidden()) {
            return "Active window is already hidden";
        }
        activeWindow.hide();
        return "Hid active window";
    }

    public synchronized String openWindow(String windowId, TuiApplication app) {
        return focusOrRestoreWindow(windowId, app);
    }

    public synchronized String closeActiveWindow(TuiApplication app) {
        Objects.requireNonNull(app, "app");
        TWindow activeWindow = app.getActiveWindow();
        if (activeWindow == null) {
            return "No active window to close";
        }
        WindowBinding binding = bindingsByWindow.get(activeWindow);
        if (binding == null) {
            return "Active window is not managed by the workspace host";
        }
        return closeWindow(binding.windowId(), app);
    }

    public synchronized String closeWindow(String windowId, TuiApplication app) {
        return runAction("window_close", activeWorkspaceId, windowId, () -> {
            Objects.requireNonNull(app, "app");
            if (windowId == null || windowId.isBlank()) {
                return "Window id is required";
            }
            WorkspaceDefinition workspace = activeWorkspace();
            WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
            ensureOverlayState(workspace, state);
            WindowInstance instance = state.instancesByWindowId.get(windowId.trim());
            if (instance == null) {
                return "Window is not currently open: " + windowId;
            }
            if (instance.window instanceof ChatWindow chatWindow) {
                return chatWindow.requestCloseWindow();
            }
            instance.window.close();
            return "Closed window '" + instance.descriptor().title() + "'";
        });
    }

    public synchronized String toggleActiveWindowZoom(TuiApplication app) {
        WorkspaceDefinition workspace = activeWorkspace();
        WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
        ensureOverlayState(workspace, state);
        Objects.requireNonNull(app, "app");
        TWindow activeWindow = app.getActiveWindow();
        if (activeWindow == null) {
            return "No active window to maximize or restore";
        }
        WindowBinding binding = bindingsByWindow.get(activeWindow);
        if (binding == null) {
            return "Active window is not managed by the workspace host";
        }
        boolean maximized = isWindowMaximized(activeWindow);
        if (maximized) {
            activeWindow.restore();
            emit("window_restore", binding.workspaceId(), binding.windowId(), "success", "allowed", "Restored active window");
            notifyStateChanged();
            return "Restored active window";
        }
        activeWindow.maximize();
        emit("window_maximize", binding.workspaceId(), binding.windowId(), "success", "allowed", "Maximized active window");
        notifyStateChanged();
        return "Maximized active window";
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

    public synchronized void recordWindowAction(String actionType, TWindow window, String message) {
        if (window == null) {
            emit(actionType, activeWorkspaceId, null, "success", "allowed", message);
            return;
        }
        WindowBinding binding = bindingsByWindow.get(window);
        emit(actionType, binding == null ? activeWorkspaceId : binding.workspaceId(), binding == null ? null : binding.windowId(),
                "success", "allowed", message);
    }

    public synchronized boolean isLifecycleSyncSuppressed(TWindow window) {
        WindowContext context = resolveWindowContext(window);
        return context == null || context.state().lifecycleSyncDepth > 0;
    }

    public synchronized boolean isWindowMaximized(TWindow window) {
        if (window == null) {
            return false;
        }
        return inferMaximized(window, window.getApplication());
    }

    public synchronized void beforeWindowMaximize(TWindow window) {
        syncWindowResizedOrMoved(window, true);
    }

    public synchronized void syncWindowFocused(TWindow window) {
        WindowContext context = resolveWindowContext(window);
        if (context == null || context.state().lifecycleSyncDepth > 0) {
            return;
        }
        context.state().activeWindowHint = context.windowId();
    }

    public synchronized void syncWindowShown(TWindow window) {
        WindowContext context = resolveWindowContext(window);
        if (context == null || context.state().lifecycleSyncDepth > 0) {
            return;
        }
        WorkspaceOverlayStore.OverlayWindowState prior = stateForWindow(context);
        WorkspaceDefinition.Geometry geometry = geometryFromWindow(window, window.getApplication());
        boolean maximized = inferMaximized(window, window.getApplication());
        WorkspaceDefinition.Geometry normalGeometry = maximized
                ? effectiveNormalGeometry(prior, context.instance().descriptor().geometry())
                : geometry;
        updateWindowState(context, new WorkspaceOverlayStore.OverlayWindowState(
                Boolean.TRUE,
                maximized,
                geometry,
                normalGeometry
        ));
        context.state().activeWindowHint = context.windowId();
        notifyStateChanged();
    }

    public synchronized void syncWindowHidden(TWindow window) {
        WindowContext context = resolveWindowContext(window);
        if (context == null || context.state().lifecycleSyncDepth > 0) {
            return;
        }
        WorkspaceOverlayStore.OverlayWindowState prior = stateForWindow(context);
        WorkspaceDefinition.Geometry geometry = geometryFromWindow(window, window.getApplication());
        boolean maximized = inferMaximized(window, window.getApplication());
        WorkspaceDefinition.Geometry normalGeometry = maximized
                ? effectiveNormalGeometry(prior, context.instance().descriptor().geometry())
                : geometry;
        updateWindowState(context, new WorkspaceOverlayStore.OverlayWindowState(
                Boolean.FALSE,
                maximized,
                geometry,
                normalGeometry
        ));
        activatePreferredVisibleWindow(context.workspaceId(), context.windowId());
        notifyStateChanged();
    }

    public synchronized void syncWindowClosed(TWindow window) {
        WindowContext context = resolveWindowContext(window);
        if (context == null) {
            return;
        }
        WorkspaceOverlayStore.OverlayWindowState prior = stateForWindow(context);
        WorkspaceDefinition.Geometry geometry = geometryFromWindow(window, window.getApplication());
        WorkspaceDefinition.Geometry normalGeometry = effectiveNormalGeometry(prior, context.instance().descriptor().geometry());
        updateWindowState(context, new WorkspaceOverlayStore.OverlayWindowState(
                Boolean.FALSE,
                Boolean.FALSE,
                geometry,
                normalGeometry
        ));
        context.state().instancesByWindowId.remove(context.windowId());
        bindingsByWindow.remove(window);
        emit("window_close", context.workspaceId(), context.windowId(), "success", "allowed",
                "Closed window '" + context.instance().descriptor().title() + "'");
        activatePreferredVisibleWindow(context.workspaceId(), context.windowId());
        notifyStateChanged();
    }

    public synchronized void syncWindowResizedOrMoved(TWindow window, boolean allowNormalGeometryUpdate) {
        WindowContext context = resolveWindowContext(window);
        if (context == null || context.state().lifecycleSyncDepth > 0) {
            return;
        }
        WorkspaceOverlayStore.OverlayWindowState prior = stateForWindow(context);
        WorkspaceDefinition.Geometry geometry = geometryFromWindow(window, window.getApplication());
        boolean maximized = inferMaximized(window, window.getApplication());
        WorkspaceDefinition.Geometry normalGeometry = effectiveNormalGeometry(prior, context.instance().descriptor().geometry());
        if (allowNormalGeometryUpdate && !maximized) {
            normalGeometry = geometry;
        }
        updateWindowState(context, new WorkspaceOverlayStore.OverlayWindowState(
                !window.isHidden(),
                maximized,
                geometry,
                maximized ? normalGeometry : geometry
        ));
        if (!window.isHidden()) {
            context.state().activeWindowHint = context.windowId();
        }
    }

    public synchronized void applyWorkspaceLayoutMode(TuiApplication app, WorkspaceDefinition.LayoutMode mode) {
        Objects.requireNonNull(app, "app");
        Objects.requireNonNull(mode, "mode");
        WorkspaceDefinition workspace = activeWorkspace();
        WorkspaceState state = statesByWorkspaceId.computeIfAbsent(workspace.id(), ignored -> new WorkspaceState());
        ensureOverlayState(workspace, state);
        withLifecycleSyncSuppressed(state, () -> app.applyNativeWindowLayout(mode));
        for (WindowInstance instance : state.instancesByWindowId.values()) {
            if (instance.window.isHidden()) {
                continue;
            }
            syncWindowResizedOrMoved(instance.window, true);
        }
        state.layoutInitialized = true;
        emit("workspace_layout", workspace.id(), null, "success", "allowed", "Applied " + mode.name().toLowerCase(Locale.ROOT) + " layout");
        notifyStateChanged();
    }

    private void showWorkspace(WorkspaceDefinition workspace, WorkspaceState state, TuiApplication app) {
        boolean applyDefaultLayout = !state.layoutInitialized && !state.loadedFromOverlay;
        LinkedHashSet<String> visibleWindowIds = new LinkedHashSet<>();
        withLifecycleSyncSuppressed(state, () -> {
            for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
                WorkspaceOverlayStore.OverlayWindowState overlayState = state.overlayWindowsById.get(descriptor.id());
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

                WindowInstance instance = ensureWindow(workspace.id(), descriptor, state, app);
                applyWindowState(instance.window, descriptor.geometry(), overlayState, app);
                instance.window.show();
                visibleWindowIds.add(descriptor.id());
            }
        });

        if (applyDefaultLayout) {
            applyWorkspaceLayoutMode(app, workspace.layoutMode());
            state.layoutInitialized = true;
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
        withLifecycleSyncSuppressed(state, () -> {
            for (WindowInstance instance : state.instancesByWindowId.values()) {
                if (app.hasWindow(instance.window) && !instance.window.isHidden()) {
                    instance.window.hide();
                }
            }
        });
    }

    private WindowInstance ensureWindow(
            String workspaceId,
            WorkspaceDefinition.WindowDescriptor descriptor,
            WorkspaceState state,
            TuiApplication app
    ) {
        WindowInstance existing = state.instancesByWindowId.get(descriptor.id());
        if (existing != null) {
            return existing;
        }

        TWindow window = windowKindRegistry.require(descriptor.kind()).create(descriptor, app);
        if (window instanceof WorkspaceWindowLifecycle lifecycle) {
            lifecycle.bindWorkspaceHost(this, workspaceId, descriptor.id());
        }
        WindowInstance created = new WindowInstance(descriptor, window);
        state.instancesByWindowId.put(descriptor.id(), created);
        bindingsByWindow.put(window, new WindowBinding(workspaceId, descriptor.id()));
        emit("window_open", workspaceId, descriptor.id(), "success", "allowed",
                "Created window '" + descriptor.title() + "' with kind '" + descriptor.kind() + "'");
        return created;
    }

    private WorkspaceOverlayStore.Overlay buildOverlay(WorkspaceState state, TuiApplication app) {
        String activeWindowId = state.activeWindowHint;
        TWindow activeWindow = app.getActiveWindow();
        if (activeWindow != null) {
            WindowBinding binding = bindingsByWindow.get(activeWindow);
            if (binding != null && statesByWorkspaceId.get(binding.workspaceId()) == state) {
                activeWindowId = binding.windowId();
            }
        }
        return new WorkspaceOverlayStore.Overlay(activeWindowId, new LinkedHashMap<>(state.overlayWindowsById));
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
        WorkspaceDefinition.Geometry normalGeometry = overlayState != null && overlayState.normalGeometry() != null
                ? overlayState.normalGeometry()
                : geometry;
        boolean shouldMaximize = overlayState != null && Boolean.TRUE.equals(overlayState.maximized());
        if (inferMaximized(window, app)) {
            window.restore();
        }
        if (shouldMaximize) {
            if (normalGeometry != null) {
                applyGeometry(window, normalGeometry, app);
            }
            window.maximize();
            return;
        }
        if (geometry != null) {
            applyGeometry(window, geometry, app);
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

        if (state.activeWindowHint != null && visibleWindowIds.contains(state.activeWindowHint)) {
            return state.activeWindowHint;
        }
        return visibleWindowIds.iterator().next();
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

    private void ensureOverlayState(WorkspaceDefinition workspace, WorkspaceState state) {
        if (state.overlayInitialized) {
            return;
        }
        WorkspaceOverlayStore.Overlay overlay = overlayStore.load(workspace.id());
        applyOverlay(workspace, state, overlay, overlay != null);
    }

    private void applyOverlay(
            WorkspaceDefinition workspace,
            WorkspaceState state,
            WorkspaceOverlayStore.Overlay overlay,
            boolean loadedFromOverlay
    ) {
        state.overlayWindowsById.clear();
        for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
            WorkspaceOverlayStore.OverlayWindowState overlayState = overlay == null ? null : overlay.windows().get(descriptor.id());
            state.overlayWindowsById.put(descriptor.id(), mergedWindowState(descriptor, overlayState));
        }
        state.activeWindowHint = overlay == null ? null : overlay.activeWindowId();
        state.overlayInitialized = true;
        state.loadedFromOverlay = loadedFromOverlay;
        state.layoutInitialized = loadedFromOverlay;
    }

    private WorkspaceOverlayStore.OverlayWindowState mergedWindowState(
            WorkspaceDefinition.WindowDescriptor descriptor,
            WorkspaceOverlayStore.OverlayWindowState overlayState
    ) {
        WorkspaceDefinition.Geometry geometry = overlayState != null && overlayState.geometry() != null
                ? overlayState.geometry()
                : descriptor.geometry();
        WorkspaceDefinition.Geometry normalGeometry = overlayState != null && overlayState.normalGeometry() != null
                ? overlayState.normalGeometry()
                : geometry;
        Boolean visible = overlayState != null && overlayState.visible() != null
                ? overlayState.visible()
                : descriptor.visible();
        Boolean maximized = overlayState != null && overlayState.maximized() != null
                ? overlayState.maximized()
                : Boolean.FALSE;
        return new WorkspaceOverlayStore.OverlayWindowState(visible, maximized, geometry, normalGeometry);
    }

    private WorkspaceOverlayStore.OverlayWindowState stateForWindow(WindowContext context) {
        return context.state().overlayWindowsById.get(context.windowId());
    }

    private WorkspaceDefinition.Geometry reopenGeometry(
            WorkspaceDefinition.Geometry defaultGeometry,
            WorkspaceOverlayStore.OverlayWindowState overlayState
    ) {
        if (overlayState == null || Boolean.TRUE.equals(overlayState.maximized())) {
            return defaultGeometry;
        }
        return overlayState.normalGeometry() != null ? overlayState.normalGeometry() : defaultGeometry;
    }

    private WorkspaceOverlayStore.OverlayWindowState overlayStateForOpen(
            WorkspaceOverlayStore.OverlayWindowState overlayState,
            WorkspaceDefinition.Geometry defaultGeometry
    ) {
        if (overlayState == null) {
            return null;
        }
        if (Boolean.TRUE.equals(overlayState.maximized())) {
            return new WorkspaceOverlayStore.OverlayWindowState(
                    Boolean.TRUE,
                    Boolean.FALSE,
                    effectiveNormalGeometry(overlayState, defaultGeometry),
                    effectiveNormalGeometry(overlayState, defaultGeometry)
            );
        }
        return new WorkspaceOverlayStore.OverlayWindowState(
                Boolean.TRUE,
                Boolean.FALSE,
                reopenGeometry(defaultGeometry, overlayState),
                effectiveNormalGeometry(overlayState, defaultGeometry)
        );
    }

    private WorkspaceDefinition.Geometry effectiveNormalGeometry(
            WorkspaceOverlayStore.OverlayWindowState state,
            WorkspaceDefinition.Geometry defaultGeometry
    ) {
        if (state != null && state.normalGeometry() != null) {
            return state.normalGeometry();
        }
        if (state != null && state.geometry() != null) {
            return state.geometry();
        }
        return defaultGeometry;
    }

    private void updateWindowState(WindowContext context, WorkspaceOverlayStore.OverlayWindowState updatedState) {
        context.state().overlayWindowsById.put(context.windowId(), updatedState);
    }

    private WorkspaceDefinition.WindowDescriptor descriptorFor(WorkspaceDefinition workspace, String windowId) {
        return workspace.windows().stream()
                .filter(candidate -> candidate.id().equals(windowId))
                .findFirst()
                .orElse(null);
    }

    private void activatePreferredVisibleWindow(String workspaceId, String excludedWindowId) {
        WorkspaceState state = statesByWorkspaceId.get(workspaceId);
        WorkspaceDefinition workspace = workspacesById.get(workspaceId);
        if (state == null || workspace == null) {
            return;
        }
        for (WorkspaceDefinition.WindowDescriptor descriptor : workspace.windows()) {
            if (descriptor.id().equals(excludedWindowId)) {
                continue;
            }
            WindowInstance candidate = state.instancesByWindowId.get(descriptor.id());
            if (candidate == null || candidate.window.isHidden()) {
                continue;
            }
            candidate.window.activate();
            state.activeWindowHint = descriptor.id();
            return;
        }
        state.activeWindowHint = null;
    }

    private WindowContext resolveWindowContext(TWindow window) {
        if (window == null) {
            return null;
        }
        WindowBinding binding = bindingsByWindow.get(window);
        if (binding == null) {
            return null;
        }
        WorkspaceState state = statesByWorkspaceId.get(binding.workspaceId());
        if (state == null) {
            return null;
        }
        WindowInstance instance = state.instancesByWindowId.get(binding.windowId());
        if (instance == null) {
            return null;
        }
        return new WindowContext(binding.workspaceId(), binding.windowId(), state, instance);
    }

    private void withLifecycleSyncSuppressed(WorkspaceState state, Runnable action) {
        state.lifecycleSyncDepth += 1;
        try {
            action.run();
        } finally {
            state.lifecycleSyncDepth -= 1;
        }
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

    private void notifyStateChanged() {
        for (Runnable listener : stateListeners) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
            }
        }
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

    public record WindowMenuEntry(
            String windowId,
            String title,
            boolean visible,
            boolean maximized,
            boolean instantiated
    ) {
    }

    private static final class WorkspaceState {
        private final Map<String, WindowInstance> instancesByWindowId = new LinkedHashMap<>();
        private final Map<String, WorkspaceOverlayStore.OverlayWindowState> overlayWindowsById = new LinkedHashMap<>();
        private String activeWindowHint;
        private boolean overlayInitialized;
        private boolean loadedFromOverlay;
        private boolean layoutInitialized;
        private int lifecycleSyncDepth;
    }

    private record WindowInstance(WorkspaceDefinition.WindowDescriptor descriptor, TWindow window) {
    }

    private record WindowBinding(String workspaceId, String windowId) {
    }

    private record WindowContext(
            String workspaceId,
            String windowId,
            WorkspaceState state,
            WindowInstance instance
    ) {
    }
}
