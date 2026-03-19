package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TCommand;
import casciian.event.TCommandEvent;
import casciian.event.TMenuEvent;
import casciian.menu.TMenu;
import io.mindspice.magenta.ui.tui.WorkspaceHost.WindowMenuEntry;
import io.mindspice.magenta.ui.tui.WorkspaceHost.WorkspaceOption;
import io.mindspice.magenta.ui.tui.workspace.WorkspaceDefinition;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TuiApplication extends TApplication {
    private static final int MID_WORKSPACE_SWITCH = 7101;
    private static final int MID_WORKSPACE_SAVE = 7102;
    private static final int MID_WORKSPACE_LOAD = 7103;

    private static final int MID_WINDOW_SELECTOR_BASE = 7400;
    private static final int MID_WINDOW_HIDE_ACTIVE = 7501;
    private static final int MID_WINDOW_TOGGLE_MAXIMIZE = 7502;
    private static final int MID_WINDOW_CLOSE_ACTIVE = 7503;

    private static final int MID_THEME_BASE = 7600;

    private final TuiThemeRegistry themeRegistry;
    private final WorkspaceHost workspaceHost;
    private final Map<Integer, String> dynamicWindowMenuIds = new LinkedHashMap<>();
    private final Map<Integer, String> themeMenuIds = new LinkedHashMap<>();

    public static void configureFrameworkChromeDefaults() {
        System.setProperty("casciian.TWindow.borderStyleForeground", "single");
        System.setProperty("casciian.TWindow.borderStyleModal", "single");
        System.setProperty("casciian.TWindow.borderStyleInactive", "single");
        System.setProperty("casciian.TWindow.borderStyleMoving", "single");
        System.setProperty("casciian.shadowOpacity", "0");
        System.setProperty("casciian.TMenu.borderStyle", "single");
        System.setProperty("casciian.TPanel.borderStyle", "single");
    }

    public TuiApplication(TuiThemeRegistry themeRegistry, WorkspaceHost workspaceHost) throws UnsupportedEncodingException {
        super(BackendType.XTERM);
        this.themeRegistry = Objects.requireNonNull(themeRegistry, "themeRegistry");
        this.workspaceHost = Objects.requireNonNull(workspaceHost, "workspaceHost");
        setHideStatusBar(true);
        this.themeRegistry.apply(this, TuiThemeRegistry.DEFAULT_THEME);
        this.workspaceHost.addStateListener(() -> invokeLater(this::rebuildMenuShell));
        installMenuShell();
    }

    public String activateInitialWorkspace() {
        String initialId = workspaceHost.activeWorkspaceId();
        return workspaceHost.switchWorkspace(initialId, this);
    }

    public void rebuildMenuShell() {
        closeMenu();
        List<TMenu> existingMenus = List.copyOf(getAllMenus());
        for (TMenu menu : existingMenus) {
            removeMenu(menu);
        }
        dynamicWindowMenuIds.clear();
        themeMenuIds.clear();
        installMenuShell();
    }

    @Override
    protected boolean onCommand(TCommandEvent event) {
        if (event.equals(TCommand.cmTile)) {
            workspaceHost.applyWorkspaceLayoutMode(this, WorkspaceDefinition.LayoutMode.TILED);
            return true;
        }
        if (event.equals(TCommand.cmCascade)) {
            workspaceHost.applyWorkspaceLayoutMode(this, WorkspaceDefinition.LayoutMode.CASCADE);
            return true;
        }
        return super.onCommand(event);
    }

    @Override
    protected boolean onMenu(TMenuEvent event) {
        int id = event.getId();
        if (id == MID_WORKSPACE_SWITCH) {
            WorkspaceOption selected = chooseWorkspace();
            if (selected != null) {
                messageBox("Workspace", workspaceHost.switchWorkspace(selected.id(), this));
            }
            return true;
        }
        if (id == MID_WORKSPACE_SAVE) {
            messageBox("Workspace", workspaceHost.saveActiveWorkspaceSnapshot(this));
            return true;
        }
        if (id == MID_WORKSPACE_LOAD) {
            messageBox("Workspace", workspaceHost.loadActiveWorkspaceSnapshot(this));
            return true;
        }
        String windowId = dynamicWindowMenuIds.get(id);
        if (windowId != null) {
            maybeShowWindowFeedback(workspaceHost.focusOrRestoreWindow(windowId, this));
            return true;
        }
        if (id == MID_WINDOW_HIDE_ACTIVE) {
            maybeShowWindowFeedback(workspaceHost.hideActiveWindow(this));
            return true;
        }
        if (id == MID_WINDOW_TOGGLE_MAXIMIZE) {
            maybeShowWindowFeedback(workspaceHost.toggleActiveWindowZoom(this));
            return true;
        }
        if (id == MID_WINDOW_CLOSE_ACTIVE || id == TMenu.MID_WINDOW_CLOSE) {
            maybeShowWindowFeedback(workspaceHost.closeActiveWindow(this));
            return true;
        }
        String themeId = themeMenuIds.get(id);
        if (themeId != null) {
            themeRegistry.apply(this, themeId);
            workspaceHost.recordWindowAction("theme_apply", null, "Applied theme: " + themeId);
            rebuildMenuShell();
            return true;
        }
        return super.onMenu(event);
    }

    public void applyNativeWindowLayout(WorkspaceDefinition.LayoutMode mode) {
        TCommand command = mode == WorkspaceDefinition.LayoutMode.CASCADE ? TCommand.cmCascade : TCommand.cmTile;
        super.onCommand(new TCommandEvent(getBackend(), command));
    }

    private void installMenuShell() {
        TMenu workspaceMenu = addMenu("&Workspace");
        workspaceMenu.addItem(MID_WORKSPACE_SWITCH, "&Switch...");
        workspaceMenu.addItem(MID_WORKSPACE_SAVE, "&Save Layout");
        workspaceMenu.addItem(MID_WORKSPACE_LOAD, "&Load Layout");

        TMenu windowMenu = addWindowMenu();
        addDynamicWindowEntries(windowMenu);

        TMenu viewMenu = addMenu("&View");
        int menuId = MID_THEME_BASE;
        for (TuiThemeProfile profile : themeRegistry.profiles()) {
            menuId += 1;
            themeMenuIds.put(menuId, profile.id());
            viewMenu.addItem(menuId, "Theme: " + profile.name());
        }
    }

    private void addDynamicWindowEntries(TMenu windowMenu) {
        windowMenu.addSeparator();
        int menuId = MID_WINDOW_SELECTOR_BASE;
        for (WindowMenuEntry entry : workspaceHost.windowMenuEntries()) {
            dynamicWindowMenuIds.put(menuId, entry.windowId());
            windowMenu.addItem(menuId, windowMenuLabel(entry));
            menuId += 1;
        }
        windowMenu.addSeparator();
        windowMenu.addItem(MID_WINDOW_HIDE_ACTIVE, "&Hide Active");
        windowMenu.addItem(MID_WINDOW_TOGGLE_MAXIMIZE, "Toggle Ma&ximize");
        windowMenu.addItem(MID_WINDOW_CLOSE_ACTIVE, "&Close Active");
    }

    private WorkspaceOption chooseWorkspace() {
        List<WorkspaceOption> options = workspaceHost.workspaceOptions();
        if (options.isEmpty()) {
            messageBox("Workspace", "No workspaces available.");
            return null;
        }
        StringBuilder prompt = new StringBuilder("Type an index or id:\n");
        for (int i = 0; i < options.size(); i++) {
            WorkspaceOption option = options.get(i);
            prompt.append(i + 1)
                    .append(") ")
                    .append(option.name())
                    .append(" [")
                    .append(option.id())
                    .append("]\n");
        }
        var input = inputBox("Switch Workspace", prompt.toString(), "1");
        if (!input.isOk() || input.getText() == null) {
            return null;
        }
        String selected = input.getText().trim();
        if (selected.isEmpty()) {
            return null;
        }
        try {
            int index = Integer.parseInt(selected);
            if (index >= 1 && index <= options.size()) {
                return options.get(index - 1);
            }
        } catch (NumberFormatException ignored) {
        }
        return options.stream()
                .filter(option -> option.id().equals(selected))
                .findFirst()
                .orElse(null);
    }

    static String windowMenuLabel(WindowMenuEntry entry) {
        String label = "Focus/Restore: " + entry.title();
        if (!entry.visible()) {
            return label + " [hidden]";
        }
        if (entry.maximized()) {
            return label + " [max]";
        }
        return label;
    }

    private void maybeShowWindowFeedback(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (message.startsWith("No ")
                || message.startsWith("Unknown ")
                || message.startsWith("Cannot ")
                || message.startsWith("Window ")
                || message.startsWith("Active ")
                || message.startsWith("Workspace action failed")
                || message.startsWith("Chat controller")) {
            messageBox("Window", message);
        }
    }
}
