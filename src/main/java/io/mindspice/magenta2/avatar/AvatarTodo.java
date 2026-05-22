package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record AvatarTodo(
    String id,
    String title,
    String notes,
    AvatarTodoStatus status,
    AvatarPriority priority,
    Instant dueAt,
    String linkedProjectId,
    String linkedTaskId,
    String linkedOutputId,
    Instant createdAt,
    Instant updatedAt,
    Instant completedAt
) {
}
