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
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void blankMessageReturnsErrorEmitter() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat(
            "agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, " ", null, "agent detail")
        );

        assertThat(emitter).isNotNull();
        assertThat(chatService.request).isNull();
    }

    @Test
    void assignRejectsNullAssignmentType() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, new StubAssignmentService(), null, null, profileService, chatService
        );

        assertThatThrownBy(() -> controller.assign("agent-1", new AssignmentRequest(
            "agent-1", null, null, null, 0, null, null, java.util.Map.of()
        ))).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        });
    }

    @Test
    void assignSucceedsWithValidAssignment() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, new StubAssignmentService(), null, null, profileService, chatService
        );

        WorkAssignment result = controller.assign("agent-1", new AssignmentRequest(
            "agent-1", null, null, AssignmentType.TASK_RUN, 0, null, null, java.util.Map.of()
        ));

        assertThat(result).isNotNull();
        assertThat(result.agentId()).isEqualTo("agent-1");
    }

    @Test
    void agentChatStreamEmitterHasNoTimeout() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat(
            "agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "hello", "qwen3", "agent detail")
        );

        assertThat(emitter.getTimeout()).isZero();
    }

    @Test
    void agentChatStreamHandlesModelWithNullResponse() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        NullResponseChatService nullChatService = new NullResponseChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, nullChatService
        );

        SseEmitter emitter = controller.chat(
            "agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "hello", "qwen3", "agent detail")
        );

        assertThat(emitter).isNotNull();
    }

    private static class StubAssignmentService extends AssignmentService {
        StubAssignmentService() {
            super(null, null, null, null);
        }

        @Override
        public WorkAssignment create(AssignmentRequest request) {
            if (request.agentId() == null || request.agentId().isBlank()) {
                throw new IllegalArgumentException("agentId is required");
            }
            if (request.assignmentType() == null) {
                throw new IllegalArgumentException("assignmentType is required");
            }
            return new WorkAssignment("assign-1", request.agentId(), null, null, request.assignmentType(),
                0, OrchestrationStatus.QUEUED, null, null, 0,
                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                null, null, null, null, null, null, null);
        }
    }

    private static class NullResponseChatService extends ChatService {
        NullResponseChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            // Return a response that isn't MsgResponse, triggering error path
            return null;
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
