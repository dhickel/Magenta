package io.mindspice.magenta2.avatar.dashboard;

import java.time.Instant;
import java.util.List;

public record DashboardFileNote(
    String sourceMode,
    String sourceLabel,
    String bindingId,
    String path,
    String title,
    String snippet,
    List<String> tags,
    Instant updatedAt,
    boolean editable,
    boolean markdown
) {
    public DashboardFileNote {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
