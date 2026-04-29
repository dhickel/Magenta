package io.mindspice.magenta2.ai.chat.plan;

import java.time.Instant;
import java.util.List;

public record ExecutionPlan(
    String conversationId,
    PlanMode mode,
    PlanStatus status,
    String goal,
    String title,
    String summary,
    String notes,
    List<String> assumptions,
    List<PlanStep> steps,
    int planStartMessageOrder,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean hasSavedPlan() {
        return title != null && !title.isBlank() && steps != null && !steps.isEmpty();
    }
}
