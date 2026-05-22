package io.mindspice.magenta2.avatar;

import java.time.Instant;

public record AvatarCalendarItem(
    String id,
    String title,
    String notes,
    Instant startsAt,
    Instant endsAt,
    String timezone,
    String location,
    AvatarCalendarStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
