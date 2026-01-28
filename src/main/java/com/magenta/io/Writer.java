package com.magenta.io;

/**
 * Simple immediate-mode writer. Outputs tokens directly as they arrive.
 */
public class Writer implements ResponseHandler {

    protected final OutputPipe pipe;
    protected final Integer colorCode;
    private final StringBuilder buffer = new StringBuilder();

    protected void appendBuffer(String token) {
        buffer.append(token);
    }

    public Writer(OutputPipe pipe) {
        this(pipe, null);
    }

    public Writer(OutputPipe pipe, Integer colorCode) {
        this.pipe = pipe;
        this.colorCode = colorCode;
    }

    @Override
    public void write(String token) {
        appendBuffer(token);
        if (colorCode != null) {
            pipe.print(token, colorCode);
        } else {
            pipe.print(token);
        }
    }

    @Override
    public void complete() {
        pipe.println("");
        reset();
    }

    @Override
    public void error(Throwable t) {
        pipe.println("");
        pipe.println("Error: " + t.getMessage());
        reset();
    }

    @Override
    public String getBuffer() {
        return buffer.toString();
    }

    /**
     * Reset the buffer for reuse.
     * Called automatically by complete() and error().
     */
    protected void reset() {
        buffer.setLength(0);
    }
}
