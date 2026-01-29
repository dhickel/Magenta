package com.magenta.session;

import com.magenta.io.IOManager;
import com.magenta.io.ResponseHandler;

/**
 * Abstract base for all session implementations.
 * Accesses shared state (config, arguments) from ConfigManager singleton.
 * Holds session-level resources (IOManager, exitFlag).
 * Subclasses provide agent-specific or context-specific behavior.
 * NOTE: IOManager is NOT owned by session - managed by SessionManager.
 */
public abstract class AbstractSession implements Session {
    // Session-level resources (IOManager is injected, not owned)
    protected IOManager ioManager;
    protected boolean exitFlag = false;
    protected ResponseHandler responseHandler; // Lazily initialized

    protected AbstractSession(IOManager ioManager) {
        this.ioManager = ioManager;
    }

    @Override
    public IOManager io() {
        return ioManager;
    }

    @Override
    public boolean shouldExit() {
        return exitFlag;
    }

    @Override
    public void setExit(boolean exit) {
        this.exitFlag = exit;
    }

    @Override
    public void attachIO(IOManager io) {
        this.ioManager = io;
        // Clear cached response handler - will be recreated with new IOManager
        this.responseHandler = null;
    }

    @Override
    public void close() throws Exception {
        // Don't close ioManager - we don't own it (SessionManager does)
        // Subclasses should close their own resources here
    }

    // Abstract method for ResponseHandler (requires agent-specific configuration like color)
    @Override
    public abstract ResponseHandler responseHandler();
}
