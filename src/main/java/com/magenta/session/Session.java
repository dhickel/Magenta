package com.magenta.session;

import com.magenta.io.IOManager;

/**
 * Interface defining a session's I/O context.
 * Implementations provide access to I/O, global context, and exit control.
 * All I/O interactions go through the IOManager.
 * Sessions own their resources and must be closed when done.
 */
public interface Session extends AutoCloseable {
    IOManager io();
    GlobalContext context();
    boolean shouldExit();
    void setExit(boolean exit);

    @Override
    void close() throws Exception;
}
