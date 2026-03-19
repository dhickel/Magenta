package io.mindspice.magenta.ui.tui.windows;

import casciian.TApplication;
import casciian.TWindow;
import casciian.event.TKeypressEvent;
import casciian.event.TMouseEvent;
import casciian.event.TResizeEvent;
import io.mindspice.magenta.ui.tui.WorkspaceHost;
import io.mindspice.magenta.ui.tui.WorkspaceWindowLifecycle;

public abstract class WorkspaceTWindow extends TWindow implements WorkspaceWindowLifecycle {
    private WorkspaceHost workspaceHost;
    private String workspaceId;
    private String windowId;

    protected WorkspaceTWindow(TApplication application, String title, int width, int height) {
        this(application, title, width, height, 0);
    }

    protected WorkspaceTWindow(TApplication application, String title, int width, int height, int flags) {
        super(application, title, width, height, RESIZABLE | HIDEONCLOSE | flags);
    }

    @Override
    public final void bindWorkspaceHost(WorkspaceHost host, String workspaceId, String windowId) {
        this.workspaceHost = host;
        this.workspaceId = workspaceId;
        this.windowId = windowId;
    }

    @Override
    protected void onFocus() {
        super.onFocus();
        if (workspaceHost != null) {
            workspaceHost.syncWindowFocused(this);
        }
    }

    @Override
    protected void onHide() {
        super.onHide();
        if (workspaceHost != null) {
            workspaceHost.syncWindowHidden(this);
        }
    }

    @Override
    protected void onShow() {
        super.onShow();
        if (workspaceHost != null) {
            workspaceHost.syncWindowShown(this);
        }
    }

    @Override
    protected void onClose() {
        super.onClose();
        if (workspaceHost != null) {
            workspaceHost.syncWindowClosed(this);
        }
    }

    @Override
    public void onResize(TResizeEvent event) {
        super.onResize(event);
        if (workspaceHost != null && event.getType() == TResizeEvent.Type.WIDGET) {
            workspaceHost.syncWindowResizedOrMoved(this, !workspaceHost.isWindowMaximized(this));
        }
    }

    @Override
    public void onMouseUp(TMouseEvent event) {
        boolean movementInProgress = inMovements();
        super.onMouseUp(event);
        if (workspaceHost != null && movementInProgress && !inMovements()) {
            workspaceHost.syncWindowResizedOrMoved(this, !workspaceHost.isWindowMaximized(this));
        }
    }

    @Override
    public void onKeypress(TKeypressEvent event) {
        int beforeX = getX();
        int beforeY = getY();
        int beforeWidth = getWidth();
        int beforeHeight = getHeight();
        super.onKeypress(event);
        if (workspaceHost != null
                && (beforeX != getX()
                || beforeY != getY()
                || beforeWidth != getWidth()
                || beforeHeight != getHeight())
                && !inMovements()) {
            workspaceHost.syncWindowResizedOrMoved(this, !workspaceHost.isWindowMaximized(this));
        }
    }

    @Override
    public void maximize() {
        if (workspaceHost != null && !workspaceHost.isLifecycleSyncSuppressed(this) && !workspaceHost.isWindowMaximized(this)) {
            workspaceHost.beforeWindowMaximize(this);
        }
        super.maximize();
        if (workspaceHost != null && !workspaceHost.isLifecycleSyncSuppressed(this)) {
            workspaceHost.syncWindowResizedOrMoved(this, false);
        }
    }

    @Override
    public void restore() {
        super.restore();
        if (workspaceHost != null && !workspaceHost.isLifecycleSyncSuppressed(this)) {
            workspaceHost.syncWindowResizedOrMoved(this, true);
        }
    }

    protected final String workspaceId() {
        return workspaceId;
    }

    protected final String windowId() {
        return windowId;
    }
}
