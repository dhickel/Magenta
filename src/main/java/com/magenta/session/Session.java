package com.magenta.session;

import com.magenta.io.IOManager;

public class Session {
    private final SessionState state;

    public Session(SessionState state) {
        this.state = state;
    }

    public void setExit(boolean shouldExit) {
        state.setExit(shouldExit);
    }

    public boolean shouldExit() {
        return state.shouldExit();
    }

    protected SessionState state() {
        return state;
    }

    protected IOManager io() {
        return state.getIOManager();
    }
}
