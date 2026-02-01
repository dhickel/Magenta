package com.magenta.io;

/**
 * Abstract base class for IOManager implementations.
 * Provides common fields for pipes and implements accessor methods.
 */
public abstract class AbstractIOManager implements IOManager {

    protected InputPipe inputPipe;
    protected OutputPipe outputPipe;

    protected AbstractIOManager() {
    }

    @Override
    public InputPipe inputPipe() {
        return inputPipe;
    }

    @Override
    public OutputPipe outputPipe() {
        return outputPipe;
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
