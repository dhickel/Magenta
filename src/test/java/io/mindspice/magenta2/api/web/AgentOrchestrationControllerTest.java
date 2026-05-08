package io.mindspice.magenta2.api.web;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Set;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.EventType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.ReactionActionType;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentOrchestrationControllerTest {

    @Test
    void agentChatStreamReturnsBeforeChatServiceCompletes() throws Exception {
        BlockingChatService chatService = new BlockingChatService();
        StubAgentProfileService profileService = new StubAgentProfileService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        CompletableFuture<SseEmitter> response = CompletableFuture.supplyAsync(() ->
            controller.chat("agent-1",
                new AgentOrchestrationController.AgentChatRequest(null, "Inspect this task", null, "task editor"))
        );

        assertThat(chatService.started.await(1, TimeUnit.SECONDS)).isTrue();
        SseEmitter emitter = response.get(200, TimeUnit.MILLISECONDS);
        assertThat(emitter.getTimeout()).isZero();
        chatService.release.countDown();
    }

    @Test
    void agentChatStreamFlushesStartBeforeChatServiceCompletes() throws Exception {
        BlockingChatService chatService = new BlockingChatService();
        StubAgentProfileService profileService = new StubAgentProfileService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat("agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "Inspect this task", null, "task editor"));
        CapturedSse captured = initializeEmitter(emitter);

        assertThat(captured.awaitEventContaining("event=start", 1, TimeUnit.SECONDS)).isTrue();
        assertThat(chatService.started.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(chatService.release.getCount()).isEqualTo(1);

        chatService.release.countDown();
        assertThat(captured.awaitEventContaining("event=done", 2, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void agentChatStreamEmitsStartAndDone() throws Exception {
        StubChatService chatService = new StubChatService();
        StubAgentProfileService profileService = new StubAgentProfileService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat("agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "Inspect this task", null, "task editor"));

        assertThat(emitter.getTimeout()).isZero();
        assertThat(chatService.completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(profileService.requestedId).isEqualTo("agent-1");
        assertThat(chatService.request.message()).contains("Agent page context: task editor");
        assertThat(chatService.request.model()).isEqualTo("qwen3");
    }

    @Test
    void agentChatStreamEmitsErrorForBlankMessage() throws Exception {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, chatService
        );

        SseEmitter emitter = controller.chat("agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, " ", null, "agent detail"));
        CapturedSse captured = initializeEmitter(emitter);

        assertThat(emitter.getTimeout()).isZero();
        assertThat(captured.awaitEventContaining("event=error", 1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.events).anySatisfy(event -> assertThat(event).contains("message is required"));
        assertThat(chatService.request).isNull();
    }

    @Test
    void agentChatStreamEmitsErrorForUnsupportedChatResponse() throws Exception {
        StubAgentProfileService profileService = new StubAgentProfileService();
        NullResponseChatService nullChatService = new NullResponseChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, profileService, nullChatService
        );

        SseEmitter emitter = controller.chat("agent-1",
            new AgentOrchestrationController.AgentChatRequest(null, "hello", "qwen3", "agent detail"));
        CapturedSse captured = initializeEmitter(emitter);

        assertThat(captured.awaitEventContaining("event=error", 1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.events).anySatisfy(event ->
            assertThat(event).contains("agent chat returned an unsupported response")
        );
    }

    @Test
    void assignRejectsNullAssignmentType() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, new StubAssignmentService(), null, null, profileService, chatService
        );

        assertThatThrownBy(() -> controller.assign("agent-1", new AgentOrchestrationController.AgentAssignmentCreateRequest(
            null, null, null, 0, null, null, java.util.Map.of()
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

        WorkAssignment result = controller.assign("agent-1", new AgentOrchestrationController.AgentAssignmentCreateRequest(
            null, null, AssignmentType.TASK_RUN, 0, null, null, java.util.Map.of()
        ));

        assertThat(result).isNotNull();
        assertThat(result.agentId()).isEqualTo("agent-1");
    }

    @Test
    void assignUsesPathAgentIdWithoutBodyAgentId() {
        StubAgentProfileService profileService = new StubAgentProfileService();
        StubChatService chatService = new StubChatService();
        var assignmentService = new AgentIdCapturingAssignmentService();
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, assignmentService, null, null, profileService, chatService
        );

        controller.assign("agent-from-path", new AgentOrchestrationController.AgentAssignmentCreateRequest(
            null, null, AssignmentType.REPORT, 0, null, null, java.util.Map.of()
        ));

        assertThat(assignmentService.receivedAgentId).isEqualTo("agent-from-path");
    }

    @Test
    void assignIgnoresUnknownJsonFieldAgentId() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = """
            {
              "assignmentType": "REPORT",
              "priority": 0,
              "modelOverride": null,
              "input": {"message": "hello"},
              "agentId": "should-be-ignored"
            }
            """;
        AgentOrchestrationController.AgentAssignmentCreateRequest request =
            mapper.readValue(json, AgentOrchestrationController.AgentAssignmentCreateRequest.class);

        assertThat(request.assignmentType()).isEqualTo(AssignmentType.REPORT);
        assertThat(request.input()).containsEntry("message", "hello");
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

    private CapturedSse initializeEmitter(SseEmitter emitter) throws Exception {
        CapturedSse captured = new CapturedSse();
        Class<?> handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler"
        );
        Object handler = Proxy.newProxyInstance(
            handlerType.getClassLoader(),
            new Class<?>[] { handlerType },
            (proxy, method, args) -> {
                if ("send".equals(method.getName()) && args[0] instanceof Set<?> set) {
                    for (Object item : set) {
                        captured.add(String.valueOf(item.getClass().getMethod("getData").invoke(item)));
                    }
                } else if ("send".equals(method.getName())) {
                    captured.add(String.valueOf(args[0]));
                } else if ("complete".equals(method.getName())) {
                    captured.completed.countDown();
                } else if ("completeWithError".equals(method.getName())) {
                    captured.completed.countDown();
                }
                return null;
            }
        );
        var initialize = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredMethod("initialize", handlerType);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
        return captured;
    }

    private static final class CapturedSse {
        private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch completed = new CountDownLatch(1);

        private void add(String event) {
            events.add(event);
            synchronized (events) {
                events.notifyAll();
            }
        }

        private boolean awaitEventContaining(String value, long timeout, TimeUnit unit) throws InterruptedException {
            long deadline = System.nanoTime() + unit.toNanos(timeout);
            synchronized (events) {
                while (events.stream().noneMatch(event -> event.contains(value))) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return false;
                    }
                    TimeUnit.NANOSECONDS.timedWait(events, remaining);
                }
                return true;
            }
        }
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

    private static class AgentIdCapturingAssignmentService extends AssignmentService {
        private String receivedAgentId;

        AgentIdCapturingAssignmentService() {
            super(null, null, null, null);
        }

        @Override
        public WorkAssignment create(AssignmentRequest request) {
            receivedAgentId = request.agentId();
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
        private final CountDownLatch completed = new CountDownLatch(1);

        StubChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            this.request = (ChatRequest.MsgRequest) request;
            var response = new ChatResponse.MsgResponse("conversation-1", this.request.model(), "ok", null, ChatPlanState.normal());
            completed.countDown();
            return response;
        }
    }

    private static class BlockingChatService extends ChatService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            started.countDown();
            try {
                release.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            var msg = (ChatRequest.MsgRequest) request;
            return new ChatResponse.MsgResponse("conv-1", msg.model(), "done", null, ChatPlanState.normal());
        }
    }

    @Test
    void schedulesEndpointReturns404WhenDisabled() {
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, null, null
        );

        assertThatThrownBy(() -> controller.schedules("agent-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getReason()).contains("Schedules are not available");
            });
    }

    @Test
    void scheduleCreateEndpointReturns404WhenDisabled() {
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, null, null
        );

        assertThatThrownBy(() -> controller.schedule("agent-1",
            new AgentSchedule("sched-1", "agent-1", "job-1", Map.of(),
                "0 * * * *", "UTC", true, null, null, null)))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getReason()).contains("Schedules are not available");
            });
    }

    @Test
    void reactionsEndpointReturns404WhenDisabled() {
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, null, null
        );

        assertThatThrownBy(() -> controller.reactions("agent-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getReason()).contains("Event reactions are not available");
            });
    }

    @Test
    void reactionCreateEndpointReturns404WhenDisabled() {
        AgentOrchestrationController controller = new AgentOrchestrationController(
            null, null, null, null, null, null
        );

        assertThatThrownBy(() -> controller.reaction("agent-1",
            new AgentEventReaction("reac-1", "agent-1", EventType.WORKFLOW_STATUS_CHANGED,
                Map.of(), ReactionActionType.ENQUEUE_ASSIGNMENT, Map.of(),
                true, null, null)))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(exception.getReason()).contains("Event reactions are not available");
            });
    }
}
