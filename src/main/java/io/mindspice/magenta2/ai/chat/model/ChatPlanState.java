package io.mindspice.magenta2.ai.chat.model;

import java.util.List;

public record ChatPlanState(
    String mode,
    String status,
    String title,
    String summary,
    String goal,
    String notes,
    List<String> steps,
    List<String> acceptanceCriteria,
    List<String> executionEvidence
) {
    public static ChatPlanState normal() {
        return new ChatPlanState("NORMAL", null, null, null, null, null, List.of(), List.of(), List.of());
    }
}
