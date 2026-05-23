package io.mindspice.magenta2.avatar;

public record PlannerTaskLink(
    String projectId,
    String assignmentId,
    String jobId,
    String outputId
) {
}
