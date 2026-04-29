package io.mindspice.magenta2.ai.chat.plan;

public record PlanToolContext(
    String conversationId,
    PlanMode mode
) {
}
