package io.mindspice.magenta.ui.tui;

import casciian.TApplication;
import casciian.TWindow;
import casciian.event.TMenuEvent;
import casciian.menu.TMenu;
import casciian.menu.TSubMenu;

import java.io.UnsupportedEncodingException;

public final class TuiApplication extends TApplication {
    private static final int MID_WORKSPACE_SWITCH = 7101;
    private static final int MID_WORKSPACE_SAVE = 7102;
    private static final int MID_WORKSPACE_LOAD = 7103;

    private static final int MID_WINDOW_ADD = 7201;
    private static final int MID_WINDOW_HIDDEN_LIST = 7202;

    private static final int MID_THEME_DEFAULT = 7301;
    private static final int MID_THEME_FEMME = 7302;

    private final TuiThemeRegistry themeRegistry;
    private final WorkspaceHost workspaceHost;

    public TuiApplication(TuiThemeRegistry themeRegistry, WorkspaceHost workspaceHost) throws UnsupportedEncodingException {
        super(BackendType.XTERM);
        this.themeRegistry = themeRegistry;
        this.workspaceHost = workspaceHost;
        this.themeRegistry.apply(this, TuiThemeRegistry.DEFAULT_THEME);
        installMenuShell();
        initializeWorkspaceShell();
    }

    private void installMenuShell() {
        TMenu workspaceMenu = addMenu("&Workspace");
        workspaceMenu.addItem(MID_WORKSPACE_SWITCH, "&Switch (placeholder)");
        workspaceMenu.addItem(MID_WORKSPACE_SAVE, "&Save (placeholder)");
        workspaceMenu.addItem(MID_WORKSPACE_LOAD, "&Load (placeholder)");

        TMenu windowMenu = addWindowMenu();
        windowMenu.addSeparator();
        windowMenu.addItem(MID_WINDOW_ADD, "&Add Window (placeholder)");
        TSubMenu hiddenWindows = windowMenu.addSubMenu("&Hidden Windows");
        hiddenWindows.addItem(MID_WINDOW_HIDDEN_LIST, "&List Hidden (placeholder)");
        windowMenu.addSeparator();
        windowMenu.addItem(TMenu.MID_TILE, "&Tile");
        windowMenu.addItem(TMenu.MID_CASCADE, "&Cascade");

        TMenu viewMenu = addMenu("&View");
        viewMenu.addItem(MID_THEME_DEFAULT, "&Theme: Default");
        viewMenu.addItem(MID_THEME_FEMME, "T&heme: Femme");
    }

    private void initializeWorkspaceShell() {
        workspaceHost.switchWorkspace("default", this);
        int width = Math.max(40, getScreen().getWidth());
        int height = Math.max(12, getScreen().getHeight() - 1);
        TWindow shell = addWindow("magenta shell", width, height);
        workspaceHost.registerWindow("default", shell);
        shell.activate();
    }

    @Override
    protected boolean onMenu(TMenuEvent event) {
        int id = event.getId();
        if (id == MID_WORKSPACE_SWITCH) {
            workspaceHost.switchWorkspace("default", this);
            messageBox("Workspace", "Workspace switch placeholder");
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
        if (id == MID_WINDOW_ADD) {
            TWindow window = addWindow("workspace window", 64, 20);
            workspaceHost.registerWindow(workspaceHost.activeWorkspaceId(), window);
            window.activate();
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
}
