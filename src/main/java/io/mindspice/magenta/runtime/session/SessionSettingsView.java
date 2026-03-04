package io.mindspice.magenta.runtime.session;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SessionSettingsView(
        UUID sessionId,
        String alias,
        String agentId,
        Instant createdAt,
        boolean blockingOnly,
        boolean toolsEnabled,
        boolean streamingEnabled,
        String agentModelId,
        List<String> agentPromptIds,
        List<String> agentTaskIds,
        List<String> agentWorkflowIds,
        List<String> agentToolIds,
        boolean agentEnabled,
        String resolvedSystemPrompt,
        String modelId,
        String modelProvider,
        String modelName,
        String modelEndpoint,
        int modelMaxTokens,
        int modelMaxContext,
        int modelCompactThreshold,
        double modelTemperature,
        String modelCompactionStrategy,
        String modelTokenizerEncoding,
        boolean modelSupportsToolCalling,
        boolean modelSupportsStreaming,
        boolean modelEnabled
) {
    public SessionSettingsView {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(agentModelId, "agentModelId");
        agentPromptIds = agentPromptIds == null ? List.of() : List.copyOf(agentPromptIds);
        agentTaskIds = agentTaskIds == null ? List.of() : List.copyOf(agentTaskIds);
        agentWorkflowIds = agentWorkflowIds == null ? List.of() : List.copyOf(agentWorkflowIds);
        agentToolIds = agentToolIds == null ? List.of() : List.copyOf(agentToolIds);
        resolvedSystemPrompt = resolvedSystemPrompt == null ? "" : resolvedSystemPrompt;
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(modelProvider, "modelProvider");
        Objects.requireNonNull(modelName, "modelName");
        Objects.requireNonNull(modelEndpoint, "modelEndpoint");
        Objects.requireNonNull(modelCompactionStrategy, "modelCompactionStrategy");
        Objects.requireNonNull(modelTokenizerEncoding, "modelTokenizerEncoding");
    }
}
