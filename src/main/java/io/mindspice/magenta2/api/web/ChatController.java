package io.mindspice.magenta2.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import io.mindspice.magenta2.ai.chat.model.ChatHistoryResponse;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ChatSessionsResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.chat.service.ChatService.ResolvedChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatStreamEvent;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest.MsgRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest.MsgRequest request) {
        ResolvedChatRequest resolvedRequest = chatService.resolve(request);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Runnable cancelSubscription = () -> {
            Disposable subscription = subscriptionRef.get();
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        };

        emitter.onCompletion(cancelSubscription);
        emitter.onTimeout(cancelSubscription);
        emitter.onError(error -> cancelSubscription.run());

        try {
            sendEvent(emitter, "start", ChatStreamEvent.start(resolvedRequest.conversationId(), resolvedRequest.model()));
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        StringBuilder responseText = new StringBuilder();
        Disposable subscription = chatService.stream(resolvedRequest).subscribe(
            chunk -> {
                responseText.append(chunk);
                try {
                    sendEvent(
                        emitter,
                        "chunk",
                        ChatStreamEvent.message(
                            resolvedRequest.conversationId(),
                            resolvedRequest.model(),
                            chatService.renderAssistantMessage(responseText.toString())
                        )
                    );
                } catch (Exception e) {
                    cancelSubscription.run();
                    emitter.completeWithError(e);
                }
            },
            error -> {
                try {
                    sendEvent(emitter, "error", ChatStreamEvent.error(error.getMessage()));
                    emitter.complete();
                } catch (Exception sendError) {
                    emitter.completeWithError(sendError);
                }
            },
            () -> {
                try {
                    sendEvent(
                        emitter,
                        "done",
                        ChatStreamEvent.message(
                            resolvedRequest.conversationId(),
                            resolvedRequest.model(),
                            chatService.renderAssistantMessage(responseText.toString())
                        )
                    );
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            }
        );
        subscriptionRef.set(subscription);
        return emitter;
    }

    @GetMapping("/sessions")
    public ChatSessionsResponse sessions() {
        return new ChatSessionsResponse(chatService.listConversationIds());
    }

    @GetMapping("/{conversationId}/history")
    public ChatHistoryResponse history(@PathVariable String conversationId) {
        return new ChatHistoryResponse(
            conversationId,
            chatService.storedConversationModel(conversationId),
            chatService.history(conversationId)
        );
    }

    @PostMapping("/commands")
    public ChatResponse.CmdResponse command(@RequestBody ChatRequest.CmdRequest request) {
        String command = normalize(request.command());
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "command is required");
        }

        String[] parts = command.split("\\s+", 2);
        String rootCommand = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        return switch (rootCommand.toLowerCase()) {
            case "new" -> handleNew();
            case "switch" -> handleSwitch(parts);
            case "clear" -> handleClear(request.conversationId(), parts);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown command: " + rootCommand);
        };
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String conversationId) {
        chatService.clearConversation(conversationId);
    }

    private ChatResponse.CmdResponse handleNew() {
        String conversationId = chatService.newConversationId();
        List<String> persistedConversationIds = chatService.listConversationIds();
        List<String> conversationIds = persistedConversationIds;
        if (!persistedConversationIds.contains(conversationId)) {
            List<String> withNewConversation = new ArrayList<>(persistedConversationIds);
            withNewConversation.add(0, conversationId);
            conversationIds = List.copyOf(withNewConversation);
        }
        return new ChatResponse.CmdResponse(
            conversationId,
            null,
            "Created new session " + conversationId,
            conversationIds,
            chatService.history(conversationId)
        );
    }

    private ChatResponse.CmdResponse handleSwitch(String[] parts) {
        if (parts.length < 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "switch requires a conversation UUID");
        }
        String targetConversationId = normalize(parts[1]);
        if (targetConversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "switch requires a conversation UUID");
        }
        requireValidUuid(targetConversationId);
        if (!chatService.conversationExists(targetConversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found: " + targetConversationId);
        }
        return new ChatResponse.CmdResponse(
            targetConversationId,
            chatService.storedConversationModel(targetConversationId),
            "Switched to " + targetConversationId,
            chatService.listConversationIds(),
            chatService.history(targetConversationId)
        );
    }

    private ChatResponse.CmdResponse handleClear(String requestConversationId, String[] parts) {
        String targetConversationId = parts.length > 1 ? normalize(parts[1]) : normalize(requestConversationId);
        if (targetConversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clear requires conversationId or /clear <conversationId>");
        }
        requireValidUuid(targetConversationId);
        chatService.clearConversation(targetConversationId);
        return new ChatResponse.CmdResponse(
            targetConversationId,
            null,
            "Cleared " + targetConversationId,
            chatService.listConversationIds(),
            chatService.history(targetConversationId)
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void requireValidUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid UUID: " + value);
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, ChatStreamEvent event) throws Exception {
        emitter.send(SseEmitter.event().name(eventName).data(event, MediaType.APPLICATION_JSON));
    }
}
