package com.magenta.io;

import com.magenta.security.SecurityFilter;

/**
 * Abstract base class for IOManager implementations.
 * Provides common fields for pipes and security filter,
 * and implements accessor methods.
 */
public abstract class AbstractIOManager implements IOManager {

    protected SecurityFilter securityFilter;
    protected InputPipe inputPipe;
    protected OutputPipe outputPipe;
    protected ColorPipe colorPipe;

    protected AbstractIOManager() {
        this.securityFilter = SecurityFilter.identity();
        this.colorPipe = ColorPipe.identity(); // Default: no color
    }

    @Override
    public InputPipe inputPipe() {
        return inputPipe;
    }

    @Override
    public OutputPipe outputPipe() {
        return outputPipe;
    }

    @Override
    public ColorPipe colorPipe() {
        return colorPipe;
    }

    @Override
    public SecurityFilter securityFilter() {
        return securityFilter;
    }

    @Override
    public void setSecurityFilter(SecurityFilter filter) {
        this.securityFilter = filter;
    }

    // === Abstract methods (implementation-specific) ===

    @Override
    public abstract void setCursor(String cursor, Integer cursorColor);

    @Override
    public abstract ResponseHandler createResponseHandler(Integer agentColor, int delayMs);

    @Override
    public void close() throws Exception {
        // Default no-op, subclasses can override
    }
}
