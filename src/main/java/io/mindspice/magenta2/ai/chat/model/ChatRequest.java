package io.mindspice.magenta2.ai.chat.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public sealed interface ChatRequest {
    record MsgRequest(String conversationId, String message, String model) implements ChatRequest { }

    record CmdRequest(String conversationId, String command, String model) implements ChatRequest {
        public CmdRequest(String conversationId, String command) {
            this(conversationId, command, null);
        }
    }

    record Archive(boolean archived) implements ChatRequest { }

    record Favorite(@JsonAlias("isFavorite") boolean favorite) implements ChatRequest { }

    record SetTitle(String title) implements ChatRequest { }

    record TurnInterrupt(String conversationId, String interruptToken, String message) implements ChatRequest { }

    record PlanAnswer(String answer, String notes, Integer questionIndex) implements ChatRequest { }

}
