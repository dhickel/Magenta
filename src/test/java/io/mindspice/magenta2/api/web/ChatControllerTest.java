package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatControllerTest {

    private static final String CONVERSATION_ID = "00000000-0000-0000-0000-000000000001";

    private final ChatService chatService = new StubChatService(
        List.of(CONVERSATION_ID),
        Map.of(CONVERSATION_ID, "qwen3")
    );
    private final ChatController chatController = new ChatController(chatService);

    @Test
    void switchCommandAcceptsConversationUuidArgument() {
        ChatResponse.CmdResponse response = chatController.command(
            new ChatRequest.CmdRequest(null, "/switch " + CONVERSATION_ID)
        );

        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.model()).isEqualTo("qwen3");
        assertThat(response.message()).isEqualTo("Switched to " + CONVERSATION_ID);
    }

    @Test
    void switchCommandRejectsExtraArguments() {
        assertThatThrownBy(() -> chatController.command(
            new ChatRequest.CmdRequest(null, "/switch " + CONVERSATION_ID + " extra")
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("switch accepts only a conversation UUID");
            });
    }

    private static class StubChatService extends ChatService {
        private final List<String> conversationIds;
        private final Map<String, String> modelsByConversationId;

        StubChatService(List<String> conversationIds, Map<String, String> modelsByConversationId) {
            super(null, null, null, null, null);
            this.conversationIds = conversationIds;
            this.modelsByConversationId = modelsByConversationId;
        }

        @Override
        public List<String> listConversationIds() {
            return conversationIds;
        }

        @Override
        public boolean conversationExists(String conversationId) {
            return conversationIds.contains(conversationId);
        }

        @Override
        public List<ChatMessage> history(String conversationId) {
            return List.of();
        }

        @Override
        public String storedConversationModel(String conversationId) {
            return modelsByConversationId.get(conversationId);
        }
    }
}
