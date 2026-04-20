package io.mindspice.magenta2.web;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.mindspice.magenta2.chat.ChatCommandRequest;
import io.mindspice.magenta2.chat.ChatCommandResponse;
import io.mindspice.magenta2.chat.ChatHistoryResponse;
import io.mindspice.magenta2.chat.ChatRequest;
import io.mindspice.magenta2.chat.ChatResponse;
import io.mindspice.magenta2.chat.ChatSessionsResponse;
import io.mindspice.magenta2.chat.ChatService;
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

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        return chatService.chat(request);
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
    public ChatCommandResponse command(@RequestBody ChatCommandRequest request) {
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

    private ChatCommandResponse handleNew() {
        String conversationId = chatService.newConversationId();
        List<String> persistedConversationIds = chatService.listConversationIds();
        List<String> conversationIds = persistedConversationIds;
        if (!persistedConversationIds.contains(conversationId)) {
            List<String> withNewConversation = new ArrayList<>(persistedConversationIds);
            withNewConversation.add(0, conversationId);
            conversationIds = List.copyOf(withNewConversation);
        }
        return new ChatCommandResponse(
            conversationId,
            null,
            "Created new session " + conversationId,
            conversationIds,
            chatService.history(conversationId)
        );
    }

    private ChatCommandResponse handleSwitch(String[] parts) {
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
        return new ChatCommandResponse(
            targetConversationId,
            chatService.storedConversationModel(targetConversationId),
            "Switched to " + targetConversationId,
            chatService.listConversationIds(),
            chatService.history(targetConversationId)
        );
    }

    private ChatCommandResponse handleClear(String requestConversationId, String[] parts) {
        String targetConversationId = parts.length > 1 ? normalize(parts[1]) : normalize(requestConversationId);
        if (targetConversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "clear requires conversationId or /clear <conversationId>");
        }
        requireValidUuid(targetConversationId);
        chatService.clearConversation(targetConversationId);
        return new ChatCommandResponse(
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
}
