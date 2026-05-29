package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;

public record AgentOutputsView(
    String sourceMode,
    String sourceLabel,
    String missingBindingMessage,
    List<RunOutputArtifact> outputs
) {
    public AgentOutputsView {
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
    }

    public boolean missingBinding() {
        return missingBindingMessage != null && !missingBindingMessage.isBlank();
    }
}
