package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record PlannerSubtodo(
    String id,
    String taskId,
    String title,
    AvatarTodoStatus status,
    int position,
    Instant createdAt,
    Instant updatedAt
) {
}
