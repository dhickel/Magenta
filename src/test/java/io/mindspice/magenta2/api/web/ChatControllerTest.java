package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatControllerTest {

    private static final String CONVERSATION_ID = "00000000-0000-0000-0000-000000000001";

    private final StubChatService chatService = new StubChatService(
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

    @Test
    void planCommandStartsPlanningTurn() {
        ChatResponse.CmdResponse response = chatController.command(
            new ChatRequest.CmdRequest(CONVERSATION_ID, "/plan")
        );

        assertThat(response.message()).isEqualTo("What goal should we plan?");
        assertThat(response.planState().mode()).isEqualTo("PLAN");
        assertThat(response.planState().goal()).isNull();
    }

    @Test
    void planCommandRejectsArguments() {
        assertThatThrownBy(() -> chatController.command(
            new ChatRequest.CmdRequest(CONVERSATION_ID, "/plan add reminder support")
        ))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("plan does not accept arguments");
            });
    }

    @Test
    void exitPlanCommandDropsPlanState() {
        chatController.command(new ChatRequest.CmdRequest(CONVERSATION_ID, "/plan"));

        ChatResponse.CmdResponse response = chatController.command(
            new ChatRequest.CmdRequest(CONVERSATION_ID, "/exit-plan")
        );

        assertThat(response.planState().mode()).isEqualTo("NORMAL");
        assertThat(response.message()).contains("Exited plan mode");
    }

    @Test
    void execPlanCommandRunsSavedPlan() {
        chatService.savedPlan = true;

        ChatResponse.CmdResponse response = chatController.command(
            new ChatRequest.CmdRequest(CONVERSATION_ID, "/exec-plan")
        );

        assertThat(response.message()).isEqualTo("executed plan");
        assertThat(chatService.executedWithClearContext).isFalse();
    }

    @Test
    void clearExecPlanCommandRunsSavedPlanAfterClearingContext() {
        chatService.savedPlan = true;

        chatController.command(new ChatRequest.CmdRequest(CONVERSATION_ID, "/clr-exec-plan"));

        assertThat(chatService.executedWithClearContext).isTrue();
    }

    @Test
    void streamReturnsBeforeDelayedChatStreamCompletes() throws Exception {
        BlockingStreamChatService blockingChatService = new BlockingStreamChatService(
            List.of(CONVERSATION_ID),
            Map.of(CONVERSATION_ID, "qwen3")
        );
        ChatController controller = new ChatController(blockingChatService);

        CompletableFuture<SseEmitter> response = CompletableFuture.supplyAsync(() ->
            controller.stream(new ChatRequest.MsgRequest(CONVERSATION_ID, "use tools", "qwen3"))
        );

        assertThat(blockingChatService.subscribed.await(1, TimeUnit.SECONDS)).isTrue();
        SseEmitter emitter = response.get(200, TimeUnit.MILLISECONDS);
        assertThat(emitter).isNotNull();

        blockingChatService.release.countDown();
        response.get(1, TimeUnit.SECONDS);
    }

    private static class StubChatService extends ChatService {
        private final List<String> conversationIds;
        private final Map<String, String> modelsByConversationId;
        private ChatPlanState planState = ChatPlanState.normal();
        private boolean savedPlan;
        private boolean executedWithClearContext;

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

        @Override
        public ChatResponse.MsgResponse beginPlan(String conversationId) {
            planState = new ChatPlanState("PLAN", "DRAFT", null, null, null, null, List.of(), List.of(), List.of());
            return new ChatResponse.MsgResponse(conversationId, "qwen3", "What goal should we plan?", null, planState);
        }

        @Override
        public void exitPlan(String conversationId) {
            planState = ChatPlanState.normal();
        }

        @Override
        public ChatResponse.MsgResponse executeSavedPlan(String conversationId, boolean clearContext) {
            if (!savedPlan) {
                throw new IllegalStateException("No saved plan exists for this conversation");
            }
            executedWithClearContext = clearContext;
            planState = new ChatPlanState(
                "NORMAL",
                "NEEDS_REVIEW",
                "Saved Plan",
                null,
                null,
                null,
                List.of("Step"),
                List.of("Do the work"),
                List.of("Evidence: ran")
            );
            return new ChatResponse.MsgResponse(conversationId, "qwen3", "executed plan", null, planState);
        }

        @Override
        public ChatPlanState planState(String conversationId) {
            return planState;
        }
    }

    private static class BlockingStreamChatService extends StubChatService {
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingStreamChatService(List<String> conversationIds, Map<String, String> modelsByConversationId) {
            super(conversationIds, modelsByConversationId);
        }

        @Override
        public ResolvedChatRequest resolve(ChatRequest request) {
            return new ResolvedChatRequest(CONVERSATION_ID, "use tools", "qwen3");
        }

        @Override
        public Flux<ChatMessage> stream(ResolvedChatRequest request) {
            return Flux.create(sink -> {
                subscribed.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                    sink.next(new ChatMessage("assistant", "done", "<p>done</p>", null));
                    sink.complete();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    sink.error(exception);
                }
            });
        }

        @Override
        public List<ChatMessage> history(String conversationId) {
            return List.of(new ChatMessage("assistant", "done", "<p>done</p>", null));
        }
    }
}
