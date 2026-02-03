package com.magenta.io.terminal;

import com.magenta.io.IOManager;
import com.magenta.io.ResponseHandler;

/**
 * Basic response handler that writes streaming tokens to an IOManager.
 * Uses IOManager's default methods which automatically handle security filtering and color.
 */
public class Writer implements ResponseHandler {

    protected final IOManager ioManager;
    protected final Integer colorCode;
    private final StringBuilder buffer = new StringBuilder();

    protected void appendBuffer(String token) {
        buffer.append(token);
    }

    public Writer(IOManager ioManager) {
        this(ioManager, null);
    }

    public Writer(IOManager ioManager, Integer colorCode) {
        this.ioManager = ioManager;
        this.colorCode = colorCode;
    }

    @Override
    public void write(String token) {
        appendBuffer(token);
        if (colorCode != null) {
            ioManager.print(token, colorCode);
        } else {
            ioManager.print(token);
        }
    }

    @Override
    public void complete() {
        ioManager.print("\n");
        reset();
    }

    @Override
    public void error(Throwable t) {
        ioManager.print("\nError: " + t.getMessage() + "\n");
        reset();
    }

    @Override
    public String getBuffer() {
        return buffer.toString();
    }

    protected void reset() {
        buffer.setLength(0);
    }
}
