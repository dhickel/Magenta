package io.mindspice.magenta2.ai.chat.model;

public sealed interface ChatRequest {
    record MsgRequest(String conversationId, String message, String model) implements ChatRequest { }

    record CmdRequest(String conversationId, String command) implements ChatRequest { }

    record Archive(boolean archived) implements ChatRequest { }

    record Favorite(boolean isFavorite) implements ChatRequest { }

    record SetTitle(String title) implements ChatRequest { }

}
