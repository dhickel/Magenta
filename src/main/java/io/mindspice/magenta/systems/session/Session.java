package io.mindspice.magenta.systems.session;

import io.mindspice.magenta.systems.config.RuntimeConfig.ModelConfig;
import io.mindspice.magenta.systems.config.RuntimeConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Session {
    private final UUID sessionId;
    private final String agentId;
    private final String alias;
    private final RuntimeConfig.ModelConfig modelConfig;
    private final List<String> toolIds;
    private final Context context;
    private final SessionConfig sessionConfig;
    private final Instant createdAt;

    public Session(
            UUID sessionId,
            String agentId,
            String alias,
            RuntimeConfig.ModelConfig modelConfig,
            List<String> toolIds,
            Context context,
            SessionConfig sessionConfig,
            Instant createdAt
    ) {
        this.sessionId = sessionId;
        this.agentId = agentId;
        this.alias = alias;
        this.modelConfig = modelConfig;
        this.toolIds = toolIds;
        this.context = context;
        this.sessionConfig = sessionConfig;
        this.createdAt = createdAt;
    }

    public UUID sessionId() {
        return sessionId;
    }

    public String agentId() {
        return agentId;
    }

    public String alias() {
        return alias;
    }

    public RuntimeConfig.ModelConfig modelConfig() {
        return modelConfig;
    }

    public List<String> toolIds() {
        return toolIds;
    }

    public Context context() {
        return context;
    }

    public SessionConfig sessionConfig() {
        return sessionConfig;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
