package io.mindspice.magenta2.ai.chat.workflow;

import java.time.Instant;
import java.util.List;

public record WorkflowDefinition(
    String id,
    String title,
    String summary,
    List<WorkflowStep> steps,
    Instant createdAt,
    Instant updatedAt
) {
    public WorkflowDefinition {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
