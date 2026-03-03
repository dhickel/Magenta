package io.mindspice.magenta.runtime.session;

import io.mindspice.magenta.runtime.config.RuntimeConfig;
import io.mindspice.magenta.runtime.context.Context;
import io.mindspice.magenta.runtime.session.config.SessionConfig;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Session(
        UUID sessionId,
        String agentId,
        String alias,
        RuntimeConfig.ModelConfig modelConfig,
        List<String> toolIds,
        Context context,
        SessionConfig sessionConfig,
        Instant createdAt
) {}
