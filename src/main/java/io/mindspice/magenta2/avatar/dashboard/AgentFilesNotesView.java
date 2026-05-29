package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.workspaces.WorkArea;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;

public record AgentFilesNotesView(
    String sourceLabel,
    String missingBindingMessage,
    WorkArea workArea,
    WorkAreaExplorerService.DirectoryListing listing,
    List<WorkAreaExplorerService.Entry> notes
) {
    public AgentFilesNotesView {
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public boolean missingBinding() {
        return missingBindingMessage != null && !missingBindingMessage.isBlank();
    }
}
