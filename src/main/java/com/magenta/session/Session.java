package com.magenta.session;

import com.magenta.io.IOManager;
import com.magenta.io.ResponseHandler;

public interface Session extends AutoCloseable {
    IOManager io();
    ResponseHandler responseHandler();
    boolean shouldExit();
    void setExit(boolean exit);

    /**
     * Attach an IOManager to this session.
     * Allows IO injection for session switching without recreating terminal state.
     */
    void attachIO(IOManager io);

    /**
     * Execute one iteration of the session loop.
     * Reads input, parses it, and dispatches to appropriate handler.
     */
    void runOnce();

    @Override
    void close() throws Exception;
}
