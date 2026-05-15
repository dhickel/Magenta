package io.mindspice.magenta2.ai.orchestration.settings;

public record RuntimeSettings(
    String defaultAgentId,
    String defaultAgentName,
    String defaultModel,
    String planningModel,
    String summaryModel,
    String compactionModel,
    Integer contextBufferPercent,
    String systemChatModel,
    String systemChatPrompt,
    String systemChatApprovedTools,
    Integer systemChatContextLimit,
    Boolean systemChatEnabled
) {
    public RuntimeSettings(
        String defaultAgentId,
        String defaultAgentName,
        String defaultModel,
        String planningModel,
        String summaryModel,
        String compactionModel,
        Integer contextBufferPercent
    ) {
        this(defaultAgentId, defaultAgentName, defaultModel, planningModel, summaryModel, compactionModel,
            contextBufferPercent, null, null, null, null, true);
    }
}
