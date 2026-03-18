package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TInputBox;
import casciian.TWindow;
import casciian.event.TMenuEvent;
import casciian.menu.TMenu;
import io.mindspice.magenta.ui.tui.WorkspaceHost.WindowOption;
import io.mindspice.magenta.ui.tui.WorkspaceHost.WorkspaceOption;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class TuiApplication extends TApplication {
    private static final int MID_WORKSPACE_SWITCH = 7101;
    private static final int MID_WORKSPACE_SAVE = 7102;
    private static final int MID_WORKSPACE_LOAD = 7103;

    private static final int MID_WINDOW_ADD = 7201;
    private static final int MID_WINDOW_HIDE_ACTIVE = 7202;
    private static final int MID_WINDOW_OPEN_HIDDEN = 7203;
    private static final int MID_WINDOW_MAXIMIZE_ACTIVE = 7204;
    private static final int MID_WINDOW_RESTORE_ACTIVE = 7205;
    private static final int MID_WINDOW_HIDDEN_LIST = 7206;

    private static final int MID_THEME_BASE = 7300;

    private final TuiThemeRegistry themeRegistry;
    private final WorkspaceHost workspaceHost;
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
        installMenuShell();
    }

    public String activateInitialWorkspace() {
        String initialId = workspaceHost.activeWorkspaceId();
        return workspaceHost.switchWorkspace(initialId, this);
    }

    @Override
    protected boolean onMenu(TMenuEvent event) {
        int id = event.getId();
        if (id == MID_WORKSPACE_SWITCH) {
            WorkspaceOption selected = chooseWorkspace();
            if (selected == null) {
                return true;
            }
            messageBox("Workspace", workspaceHost.switchWorkspace(selected.id(), this));
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
        if (id == MID_WINDOW_ADD) {
            WindowOption selected = chooseWindow("Add Window", workspaceHost.addableWindows());
            if (selected == null) {
                return true;
            }
            messageBox("Window", workspaceHost.openWindow(selected.id(), this));
            return true;
        }
        if (id == MID_WINDOW_HIDE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.hide();
                workspaceHost.recordWindowAction("window_hide", active, "Hid active window");
            }
            return true;
        }
        if (id == MID_WINDOW_OPEN_HIDDEN) {
            WindowOption selected = chooseWindow("Open Hidden Window", workspaceHost.hiddenWindows());
            if (selected == null) {
                return true;
            }
            messageBox("Window", workspaceHost.openWindow(selected.id(), this));
            return true;
        }
        if (id == MID_WINDOW_MAXIMIZE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.maximize();
                workspaceHost.recordWindowAction("window_maximize", active, "Maximized active window");
            }
            return true;
        }
        if (id == MID_WINDOW_RESTORE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.restore();
                workspaceHost.recordWindowAction("window_restore", active, "Restored active window");
            }
            return true;
        }
        if (id == MID_WINDOW_HIDDEN_LIST) {
            messageBox("Window", workspaceHost.describeHiddenWindows());
            return true;
        }
        String themeId = themeMenuIds.get(id);
        if (themeId != null) {
            themeRegistry.apply(this, themeId);
            workspaceHost.recordWindowAction("theme_apply", null, "Applied theme: " + themeId);
            return true;
        }
        return super.onMenu(event);
    }

    private void installMenuShell() {
        TMenu workspaceMenu = addMenu("&Workspace");
        workspaceMenu.addItem(MID_WORKSPACE_SWITCH, "&Switch...");
        workspaceMenu.addItem(MID_WORKSPACE_SAVE, "&Save Layout");
        workspaceMenu.addItem(MID_WORKSPACE_LOAD, "&Load Layout");

        TMenu windowMenu = addWindowMenu();
        windowMenu.addSeparator();
        windowMenu.addItem(MID_WINDOW_ADD, "&Add Window...");
        windowMenu.addItem(MID_WINDOW_OPEN_HIDDEN, "Open &Hidden Window...");
        windowMenu.addItem(MID_WINDOW_HIDE_ACTIVE, "&Hide Active");
        windowMenu.addItem(MID_WINDOW_MAXIMIZE_ACTIVE, "Ma&ximize Active");
        windowMenu.addItem(MID_WINDOW_RESTORE_ACTIVE, "&Restore Active");
        windowMenu.addItem(MID_WINDOW_HIDDEN_LIST, "&List Hidden");

        TMenu viewMenu = addMenu("&View");
        int menuId = MID_THEME_BASE;
        for (TuiThemeProfile profile : themeRegistry.profiles()) {
            menuId += 1;
            themeMenuIds.put(menuId, profile.id());
            viewMenu.addItem(menuId, "Theme: " + profile.name());
        }
    }

    private WorkspaceOption chooseWorkspace() {
        List<WorkspaceOption> options = workspaceHost.workspaceOptions();
        if (options.isEmpty()) {
            messageBox("Workspace", "No workspaces available.");
            return null;
        }
        return chooseOption(
                "Switch Workspace",
                options,
                WorkspaceOption::id,
                option -> option.name() + " [" + option.id() + "]"
        );
    }

    private WindowOption chooseWindow(String title, List<WindowOption> options) {
        if (options.isEmpty()) {
            messageBox("Window", "No eligible windows found.");
            return null;
        }
        return chooseOption(title, options, WindowOption::id, WindowOption::title);
    }

    private <T> T chooseOption(
            String title,
            List<T> options,
            java.util.function.Function<T, String> idFn,
            java.util.function.Function<T, String> labelFn
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Type an index or id:\n");
        for (int i = 0; i < options.size(); i++) {
            T option = options.get(i);
            prompt.append(i + 1)
                    .append(") ")
                    .append(labelFn.apply(option))
                    .append('\n');
        }

        TInputBox input = inputBox(title, prompt.toString(), "1");
        if (!input.isOk()) {
            return null;
        }

        String selected = input.getText() == null ? "" : input.getText().trim();
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

        String normalized = selected.toLowerCase(Locale.ROOT);
        for (T option : options) {
            if (idFn.apply(option).toLowerCase(Locale.ROOT).equals(normalized)) {
                return option;
            }
        }

        messageBox(title, "Invalid selection: " + selected);
        return null;
    }
}
