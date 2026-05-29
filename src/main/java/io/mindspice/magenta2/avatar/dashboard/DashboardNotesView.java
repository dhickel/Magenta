package io.mindspice.magenta2.avatar.dashboard;

import java.util.List;

import io.mindspice.magenta2.avatar.AvatarNote;

public record DashboardNotesView(
    String sourceMode,
    String sourceLabel,
    String missingBindingMessage,
    String query,
    String lastOpenedNoteId,
    String lastOpenedFilePath,
    List<AvatarNote> personalNotes,
    List<DashboardFileNote> fileNotes
) {
    public DashboardNotesView {
        personalNotes = personalNotes == null ? List.of() : List.copyOf(personalNotes);
        fileNotes = fileNotes == null ? List.of() : List.copyOf(fileNotes);
    }

    public boolean missingBinding() {
        return missingBindingMessage != null && !missingBindingMessage.isBlank();
    }
}
