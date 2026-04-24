package io.mindspice.magenta2.ai.chat.model;

public sealed interface ChatRequest {
    record MsgRequest(String conversationId, String message, String model) implements ChatRequest { }

    record CmdRequest(String conversationId, String command) implements ChatRequest { }

}
