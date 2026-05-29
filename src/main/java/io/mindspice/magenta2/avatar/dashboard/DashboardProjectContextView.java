package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;

public record DashboardProjectContextView(
    Project project,
    boolean codeProject,
    String storageRootLabel,
    String missingBindingMessage,
    List<DashboardProjectArtifact> artifacts,
    List<DashboardFileNote> notes,
    List<RunOutputArtifact> outputs
) {
    public DashboardProjectContextView {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        notes = notes == null ? List.of() : List.copyOf(notes);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    public boolean missingBinding() {
        return missingBindingMessage != null && !missingBindingMessage.isBlank();
    }
}
