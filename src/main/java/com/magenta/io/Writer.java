package com.magenta.io;

/**
 * Simple immediate-mode writer. Outputs tokens directly as they arrive.
 */
public class Writer implements ResponseHandler {

    protected final IOManager io;
    protected final Integer colorCode;
    private final StringBuilder buffer = new StringBuilder();

    protected void appendBuffer(String token) {
        buffer.append(token);
    }

    public Writer(IOManager io) {
        this(io, null);
    }

    public Writer(IOManager io, Integer colorCode) {
        this.io = io;
        this.colorCode = colorCode;
    }

    @Override
    public void write(String token) {
        appendBuffer(token);
        if (colorCode != null) {
            io.print(token, colorCode);
        } else {
            io.print(token);
        }
    }

    @Override
    public void complete() {
        io.println("");
    }

    @Override
    public void error(Throwable t) {
        io.println("");
        io.error("Error: " + t.getMessage());
    }

    @Override
    public String getBuffer() {
        return buffer.toString();
    }
}
