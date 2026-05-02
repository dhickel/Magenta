package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.execution.ActiveTurnRegistry.ActiveTurn;
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
    void newCommandStartsUnsavedChatWithoutCreatingConversationId() {
        ChatResponse.CmdResponse response = chatController.command(
            new ChatRequest.CmdRequest(CONVERSATION_ID, "/new")
        );

        assertThat(response.conversationId()).isNull();
        assertThat(response.model()).isNull();
        assertThat(response.message()).isEqualTo("New chat");
        assertThat(response.conversationIds()).containsExactly(CONVERSATION_ID);
        assertThat(response.history()).isEmpty();
        assertThat(response.contextUsage()).isNull();
        assertThat(response.planState().mode()).isEqualTo("NORMAL");
        assertThat(chatService.newConversationIdCalls).isZero();
    }

    @Test
    void sessionsPayloadIncludesNullableTitles() {
        chatService.title = "Reminder Followup";
        chatService.titleJobStatus = "SUCCEEDED";

        var response = chatController.sessions();

        assertThat(response.conversationIds()).containsExactly(CONVERSATION_ID);
        assertThat(response.sessions()).containsExactly(new ChatSession(CONVERSATION_ID, "Reminder Followup", "SUCCEEDED"));
    }

    @Test
    void historyPayloadIncludesConversationTitle() {
        chatService.title = "Reminder Followup";

        var response = chatController.history(CONVERSATION_ID);

        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.title()).isEqualTo("Reminder Followup");
    }

    @Test
    void renameUpdatesConversationTitle() {
        ChatSession response = chatController.rename(
            CONVERSATION_ID,
            new ChatRequest.SetTitle("  Updated   Session  ")
        );

        assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
        assertThat(response.title()).isEqualTo("Updated Session");
        assertThat(chatService.title).isEqualTo("Updated Session");
    }

    @Test
    void renameRejectsBlankTitle() {
        assertThatThrownBy(() -> chatController.rename(CONVERSATION_ID, new ChatRequest.SetTitle(" ")))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("title is required");
            });
    }

    @Test
    void favoriteUpdatesConversationFavoriteState() {
        ChatSession response = chatController.favorite(CONVERSATION_ID, new ChatRequest.Favorite(true));

        assertThat(response.favorite()).isTrue();
        assertThat(chatService.favorite).isTrue();
    }

    @Test
    void favoriteRequestBindsBrowserPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ChatRequest.Favorite request = objectMapper.readValue("{\"favorite\":true}", ChatRequest.Favorite.class);
        ChatRequest.Favorite legacyRequest = objectMapper.readValue("{\"isFavorite\":true}", ChatRequest.Favorite.class);

        assertThat(request.favorite()).isTrue();
        assertThat(legacyRequest.favorite()).isTrue();
    }

    @Test
    void archiveUpdatesConversationArchiveState() {
        ChatSession response = chatController.archive(CONVERSATION_ID, new ChatRequest.Archive(true));

        assertThat(response.archived()).isTrue();
        assertThat(chatService.archived).isTrue();
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
        assertThat(chatService.executed).isTrue();
    }

    @Test
    void clearExecPlanCommandIsRemoved() {
        assertThatThrownBy(() -> chatController.command(new ChatRequest.CmdRequest(CONVERSATION_ID, "/clr-exec-plan")))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).isEqualTo("unknown command: clr-exec-plan");
            });
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
        private boolean executed;
        private int newConversationIdCalls;
        private String title;
        private String titleJobStatus;
        private boolean favorite;
        private boolean archived;

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
        public List<ChatSession> listSessions() {
            return conversationIds.stream()
                .map(conversationId -> new ChatSession(conversationId, title, titleJobStatus, favorite, archived, null))
                .toList();
        }

        @Override
        public boolean conversationExists(String conversationId) {
            return conversationIds.contains(conversationId);
        }

        @Override
        public String newConversationId() {
            newConversationIdCalls++;
            return "00000000-0000-0000-0000-000000000002";
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
        public String conversationTitle(String conversationId) {
            return title;
        }

        @Override
        public String conversationTitleJobStatus(String conversationId) {
            return titleJobStatus;
        }

        @Override
        public ChatSession renameConversation(String conversationId, String title) {
            this.title = title.trim().replaceAll("\\s+", " ");
            return new ChatSession(conversationId, this.title, titleJobStatus, favorite, archived, null);
        }

        @Override
        public ChatSession setConversationFavorite(String conversationId, boolean favorite) {
            this.favorite = favorite;
            return new ChatSession(conversationId, title, titleJobStatus, favorite, archived, null);
        }

        @Override
        public ChatSession setConversationArchived(String conversationId, boolean archived) {
            this.archived = archived;
            return new ChatSession(conversationId, title, titleJobStatus, favorite, archived, null);
        }

        @Override
        public ChatResponse.MsgResponse beginPlan(String conversationId) {
            planState = new ChatPlanState("PLAN", "DRAFT", null, null, null, null, List.of(), List.of(), List.of());
            return new ChatResponse.MsgResponse(conversationId, "qwen3", "What goal should we plan?", null, planState);
        }

        @Override
        public ChatResponse.MsgResponse beginPlan(String conversationId, String selectedModel) {
            return beginPlan(conversationId);
        }

        @Override
        public void exitPlan(String conversationId) {
            planState = ChatPlanState.normal();
        }

        @Override
        public ChatResponse.MsgResponse executeSavedPlan(String conversationId) {
            if (!savedPlan) {
                throw new IllegalStateException("No saved plan exists for this conversation");
            }
            executed = true;
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
            return stream(request, null);
        }

        @Override
        public Flux<ChatMessage> stream(ResolvedChatRequest request, ActiveTurn activeTurn) {
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
