package io.mindspice.magenta2.ai.orchestration.settings;

public record RuntimeSettings(
    String defaultAgentId,
    String defaultAgentName,
    String defaultModel,
    String planningModel,
    String summaryModel,
    String compactionModel,
    Integer contextBufferPercent
) {
}
