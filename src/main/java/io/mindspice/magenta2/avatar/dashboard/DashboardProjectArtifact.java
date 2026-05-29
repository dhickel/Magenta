package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

public record DashboardProjectArtifact(
    String type,
    String title,
    String path,
    List<String> items,
    String status,
    String error
) {
    public DashboardProjectArtifact {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
