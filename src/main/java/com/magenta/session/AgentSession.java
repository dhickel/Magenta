package com.magenta.session;

import com.magenta.io.IOManager;

public class AgentSession extends Session {
    private final AgentState agentState;
    private final InteractionMode mode;

    public AgentSession(SessionState state) {
        this(state, new InteractionMode.StreamingChat());
    }

    public AgentSession(SessionState state, InteractionMode mode) {
        super(state);
        this.agentState = new AgentState(this, state.getBaseAgent());
        this.mode = mode;
    }

    public void run() {
        while (!shouldExit()) {
            mode.processIteration(this);
        }
    }

    public AgentState agentState() {
        return agentState;
    }

    public InteractionMode mode() {
        return mode;
    }

    @Override
    public SessionState state() {
        return super.state();
    }

    @Override
    public IOManager io() {
        return super.io();
    }
}
