package io.mindspice.magenta.ui.tui.chat;

public interface ChatController {
    boolean submitComposerText(String text);

    void requestAbort();
}
