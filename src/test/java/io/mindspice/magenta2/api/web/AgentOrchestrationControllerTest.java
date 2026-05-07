package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.List;

import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestrationControllerTest {

    @Test
    void agentChatStreamStartsAndCallsChatServiceWithPageContext() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat(
            "agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "Inspect this task", null, "task editor")
        );

        assertThat(emitter.getTimeout()).isZero();
        assertThat(profileService.requestedId).isEqualTo("agent-1");
        assertThat(chatService.request.message()).contains("Agent page context: task editor");
        assertThat(chatService.request.message()).contains("Inspect this task");
        assertThat(chatService.request.model()).isEqualTo("qwen3");
    }

    @Test
    void agentChatStreamReturnsErrorEmitterWhenMessageIsBlank() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat(
            "agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, " ", null, "agent detail")
        );

        assertThat(emitter.getTimeout()).isZero();
        assertThat(chatService.request).isNull();
    }

    private static class StubAgentProfileService extends AgentProfileService {
        private String requestedId;

        StubAgentProfileService() {
            super(null, null, null);
        }

        @Override
        public AgentProfile get(String id) {
            requestedId = id;
            return new AgentProfile(
                id,
                "Magenta",
                AgentProfileStatus.ACTIVE,
                "qwen3",
                "system",
                List.of(),
                List.of(),
                true,
                Instant.EPOCH,
                Instant.EPOCH
            );
        }
    }

    private static class StubChatService extends ChatService {
        private ChatRequest.MsgRequest request;

        StubChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = (ChatRequest.MsgRequest) request;
            return new ChatResponse.MsgResponse("conversation-1", this.request.model(), "ok", null, ChatPlanState.normal());
        }
    }
}
