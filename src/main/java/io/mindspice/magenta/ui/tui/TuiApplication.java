package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TWindow;
import casciian.event.TMenuEvent;
import casciian.menu.TMenu;
import casciian.menu.TSubMenu;

import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class TuiApplication extends TApplication {
    private static final int MID_WORKSPACE_SWITCH = 7101;
    private static final int MID_WORKSPACE_SAVE = 7102;
    private static final int MID_WORKSPACE_LOAD = 7103;

    private static final int MID_WINDOW_ACTIVATE_CHAT = 7201;
    private static final int MID_WINDOW_HIDE_ACTIVE = 7202;
    private static final int MID_WINDOW_SHOW_HIDDEN = 7203;
    private static final int MID_WINDOW_MAXIMIZE_ACTIVE = 7204;
    private static final int MID_WINDOW_RESTORE_ACTIVE = 7205;
    private static final int MID_WINDOW_HIDDEN_LIST = 7206;

    private static final int MID_THEME_DEFAULT = 7301;
    private static final int MID_THEME_FEMME = 7302;

    private final TuiThemeRegistry themeRegistry;
    private final WorkspaceHost workspaceHost;
    private final Map<String, TWindow> windowsByAlias = new LinkedHashMap<>();

    public TuiApplication(TuiThemeRegistry themeRegistry, WorkspaceHost workspaceHost) throws UnsupportedEncodingException {
        super(BackendType.XTERM);
        this.themeRegistry = Objects.requireNonNull(themeRegistry, "themeRegistry");
        this.workspaceHost = Objects.requireNonNull(workspaceHost, "workspaceHost");
        this.themeRegistry.apply(this, TuiThemeRegistry.DEFAULT_THEME);
        installMenuShell();
        workspaceHost.switchWorkspace("default", this);
    }

    public void registerWindow(String workspaceId, String alias, TWindow window) {
        String key = normalizeAlias(alias);
        windowsByAlias.put(key, Objects.requireNonNull(window, "window"));
        workspaceHost.registerWindow(workspaceId, window);
    }

    public TWindow windowByAlias(String alias) {
        return windowsByAlias.get(normalizeAlias(alias));
    }

    public void activateWindow(String alias) {
        TWindow window = windowByAlias(alias);
        if (window == null) {
            return;
        }
        if (window.isHidden()) {
            window.show();
        }
        window.activate();
    }

    private void installMenuShell() {
        TMenu workspaceMenu = addMenu("&Workspace");
        workspaceMenu.addItem(MID_WORKSPACE_SWITCH, "&Switch (default)");
        workspaceMenu.addItem(MID_WORKSPACE_SAVE, "&Save (placeholder)");
        workspaceMenu.addItem(MID_WORKSPACE_LOAD, "&Load (placeholder)");

        TMenu windowMenu = addWindowMenu();
        windowMenu.addSeparator();
        windowMenu.addItem(MID_WINDOW_ACTIVATE_CHAT, "&Activate Chat");
        windowMenu.addItem(MID_WINDOW_HIDE_ACTIVE, "&Hide Active");
        windowMenu.addItem(MID_WINDOW_SHOW_HIDDEN, "&Show First Hidden");
        windowMenu.addItem(MID_WINDOW_MAXIMIZE_ACTIVE, "Ma&ximize Active");
        windowMenu.addItem(MID_WINDOW_RESTORE_ACTIVE, "&Restore Active");
        TSubMenu hiddenWindows = windowMenu.addSubMenu("&Hidden Windows");
        hiddenWindows.addItem(MID_WINDOW_HIDDEN_LIST, "&List Hidden");
        windowMenu.addSeparator();
        windowMenu.addItem(TMenu.MID_TILE, "&Tile");
        windowMenu.addItem(TMenu.MID_CASCADE, "&Cascade");

        TMenu viewMenu = addMenu("&View");
        viewMenu.addItem(MID_THEME_DEFAULT, "&Theme: Default");
        viewMenu.addItem(MID_THEME_FEMME, "T&heme: Femme");
    }

    @Override
    protected boolean onMenu(TMenuEvent event) {
        int id = event.getId();
        if (id == MID_WORKSPACE_SWITCH) {
            workspaceHost.switchWorkspace("default", this);
            return true;
        }
        if (id == MID_WORKSPACE_SAVE) {
            messageBox("Workspace", workspaceHost.saveActiveWorkspaceSnapshot());
            return true;
        }
        if (id == MID_WORKSPACE_LOAD) {
            messageBox("Workspace", workspaceHost.loadActiveWorkspaceSnapshot());
            return true;
        }
        if (id == MID_WINDOW_ACTIVATE_CHAT) {
            activateWindow("chat");
            return true;
        }
        if (id == MID_WINDOW_HIDE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.hide();
            }
            return true;
        }
        if (id == MID_WINDOW_SHOW_HIDDEN) {
            TWindow hidden = workspaceHost.firstHiddenWindow();
            if (hidden != null) {
                hidden.show();
                hidden.activate();
            }
            return true;
        }
        if (id == MID_WINDOW_MAXIMIZE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.maximize();
            }
            return true;
        }
        if (id == MID_WINDOW_RESTORE_ACTIVE) {
            TWindow active = getActiveWindow();
            if (active != null) {
                active.restore();
            }
            return true;
        }
        if (id == MID_WINDOW_HIDDEN_LIST) {
            messageBox("Window", workspaceHost.describeHiddenWindows());
            return true;
        }
        if (id == MID_THEME_DEFAULT) {
            themeRegistry.apply(this, TuiThemeRegistry.DEFAULT_THEME);
            return true;
        }
        if (id == MID_THEME_FEMME) {
            themeRegistry.apply(this, TuiThemeRegistry.FEMME_THEME);
            return true;
        }
        return super.onMenu(event);
    }

    private String normalizeAlias(String alias) {
        if (alias == null || alias.isBlank()) {
            return "chat";
        }
        return alias.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
