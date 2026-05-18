package io.mindspice.magenta2.api.web;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.mindspice.magenta2.ai.chat.model.ChatHistory;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.model.ChatSession;
import io.mindspice.magenta2.ai.chat.model.ChatSessions;
import io.mindspice.magenta2.ai.chat.model.ChatPlanState;
import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.chat.service.ResolvedChatRequest;
import io.mindspice.magenta2.ai.chat.service.StoredContextUsage;
import io.mindspice.magenta2.ai.chat.service.ContextManagementAdvisor;
import io.mindspice.magenta2.ai.chat.model.ChatStreamEvent;
import io.mindspice.magenta2.ai.chat.service.AuditService;
import io.mindspice.magenta2.ai.execution.ActiveTurnRegistry;
import io.mindspice.magenta2.ai.execution.ActiveTurnRegistry.ActiveTurn;
import io.mindspice.magenta2.ai.execution.InterruptResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final ChatService chatService;
    private final ActiveTurnRegistry activeTurnRegistry;
    private final AuditService auditService;
    private final long planExecutionStreamTimeoutMillis;

    public record PlanStartRequest(String conversationId, String model, String planningModel, String userInstruction) { }
    public record SaveTaskRequest(String title) { }
    public record SaveTaskResponse(String taskId, String taskTitle, ChatPlanState planState) { }

    public ChatController(ChatService chatService) {
        this(chatService, new ActiveTurnRegistry());
    }

    public ChatController(ChatService chatService, ActiveTurnRegistry activeTurnRegistry) {
        this(chatService, activeTurnRegistry, null, 0);
    }

    public ChatController(ChatService chatService, ActiveTurnRegistry activeTurnRegistry, long planExecutionStreamTimeoutSeconds) {
        this(chatService, activeTurnRegistry, null, planExecutionStreamTimeoutSeconds);
    }

    @Autowired
    public ChatController(
        ChatService chatService,
        ActiveTurnRegistry activeTurnRegistry,
        @Autowired(required = false) AuditService auditService,
        @Value("${magenta.plan.execution-stream-timeout-seconds:0}") long planExecutionStreamTimeoutSeconds
    ) {
        this.chatService = chatService;
        this.activeTurnRegistry = activeTurnRegistry;
        this.auditService = auditService;
        this.planExecutionStreamTimeoutMillis = planExecutionStreamTimeoutSeconds <= 0
            ? 0L
            : planExecutionStreamTimeoutSeconds * 1000;
    }

    @PostMapping
    public ChatResponse chat(@Valid @RequestBody ChatRequest.MsgRequest request) {
        return chatService.chat(request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody ChatRequest.MsgRequest request) {
        ResolvedChatRequest resolvedRequest = chatService.resolve(request);
        return streamResolved(resolvedRequest, false);
    }

    @PostMapping(value = "/{conversationId}/plan/execute/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPlanExecution(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Direct plan execution is disabled. Save the plan and send it to an agent.");
    }

    private SseEmitter streamResolved(ResolvedChatRequest resolvedRequest, boolean planExecution) {
        ActiveTurn activeTurn = activeTurnRegistry.register(resolvedRequest.conversationId());
        SseEmitter emitter = SseStreamLifecycle.createEmitter(
            planExecution ? planExecutionStreamTimeoutMillis : 0L
        );
        SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
        AtomicBoolean planExecutionFinalized = new AtomicBoolean(false);

        Runnable domainCleanup = () -> {
            guard.dispose();
            activeTurnRegistry.complete(activeTurn.turnId());
        };
        java.util.function.Consumer<RuntimeException> failPlanExecution = exception -> {
            domainCleanup.run();
            if (planExecution && planExecutionFinalized.compareAndSet(false, true)) {
                chatService.recordExecutionFailure(resolvedRequest.conversationId(), exception);
            }
            recordStreamError(resolvedRequest, planExecution ? "plan_stream_error" : "stream_error", exception);
        };

        emitter.onCompletion(domainCleanup);
        emitter.onTimeout(() -> failPlanExecution.accept(new IllegalStateException(
            "Plan execution stream timed out after " + (planExecutionStreamTimeoutMillis / 1000) + " seconds"
        )));
        emitter.onError(error -> failPlanExecution.accept(new IllegalStateException(
            "Plan execution stream ended before completion: " + ChatStreamSupport.safeMessage(error), error
        )));

        try {
            ChatStreamSupport.sendSseEvent(
                emitter,
                "start",
                new ChatStreamEvent.Start(
                    resolvedRequest.conversationId(),
                    resolvedRequest.model(),
                    activeTurn.turnId(),
                    activeTurn.token(),
                    chatService.planState(resolvedRequest.conversationId())
                )
            );
        } catch (Exception e) {
            emitter.completeWithError(e);
            return emitter;
        }

        Disposable subscription = chatService.stream(resolvedRequest, activeTurn)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(
            message -> {
                try {
                    if ("tool".equalsIgnoreCase(message.role())) {
                        ChatStreamSupport.sendSseEvent(
                            emitter,
                            "tool",
                            new ChatStreamEvent.Tool(
                                message.toolActivity(),
                                chatService.contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
                                chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                        return;
                    }
                    if ("user".equalsIgnoreCase(message.role())) {
                        ChatStreamSupport.sendSseEvent(
                            emitter,
                            "interrupt",
                            new ChatStreamEvent.Interrupt(
                                message.text(),
                                chatService.contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
                                chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                        return;
                    }
                    if ("system".equalsIgnoreCase(message.role())) {
                        ChatStreamSupport.sendSseEvent(
                            emitter,
                            "system",
                            new ChatStreamEvent.SystemNotice(
                                message.text(),
                                message.renderedHtml(),
                                chatService.contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
                                chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                        return;
                    }
                    ChatStreamSupport.sendSseEvent(
                        emitter,
                        "context",
                        new ChatStreamEvent.Context(
                            chatService.contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
                            chatService.planState(resolvedRequest.conversationId())
                        )
                    );
                    ChatStreamSupport.sendSseEvent(
                        emitter,
                        "chunk",
                        new ChatStreamEvent.Chunk(
                            message.text(),
                            message.renderedHtml(),
                            message.thinkingHtml(),
                            chatService.contextUsage(resolvedRequest.conversationId(), resolvedRequest.model()),
                            chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                } catch (Exception e) {
                    failPlanExecution.accept(new IllegalStateException(
                        "Plan execution stream ended before the client received completion.", e
                    ));
                    emitter.completeWithError(e);
                }
            },
            error -> {
                try {
                    activeTurnRegistry.complete(activeTurn.turnId());
                    if (planExecution) {
                        RuntimeException runtimeException = error instanceof RuntimeException existing
                            ? existing
                            : new IllegalStateException(ChatStreamSupport.safeMessage(error), error);
                        if (planExecutionFinalized.compareAndSet(false, true)) {
                            chatService.recordExecutionFailure(resolvedRequest.conversationId(), runtimeException);
                        }
                    } else {
                        chatService.discardLastUserMessage(resolvedRequest.conversationId(), resolvedRequest.message());
                    }
                    recordStreamError(resolvedRequest, planExecution ? "plan_stream_error" : "stream_error", error);
                    ChatStreamSupport.sendSseEvent(emitter, "error", new ChatStreamEvent.Error(error.getMessage()));
                    emitter.complete();
                } catch (Exception sendError) {
                    emitter.completeWithError(sendError);
                }
            },
            () -> {
                try {
                    activeTurnRegistry.complete(activeTurn.turnId());
                    if (planExecution) {
                        chatService.handlePlanExecutionStreamFinished(resolvedRequest.conversationId());
                        planExecutionFinalized.set(true);
                    }
                    StoredContextUsage contextUsage = chatService.maintainContextUsage(
                        resolvedRequest.conversationId(),
                        resolvedRequest.model()
                    );
                    if (contextUsage.compacted()) {
                        ChatMessage compactionNotice = chatService.systemNotice(
                            ContextManagementAdvisor.COMPACTION_NOTICE
                        );
                        ChatStreamSupport.sendSseEvent(
                            emitter,
                            "system",
                            new ChatStreamEvent.SystemNotice(
                                compactionNotice.text(),
                                compactionNotice.renderedHtml(),
                                contextUsage.usage(),
                                chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                    }
                    ChatMessage lastMessage = ChatStreamSupport.lastAssistantMessage(
                        chatService, resolvedRequest.conversationId()
                    );
                    ChatStreamSupport.sendSseEvent(
                        emitter,
                        "done",
                        new ChatStreamEvent.Done(
                            resolvedRequest.conversationId(),
                            resolvedRequest.model(),
                            lastMessage.text(),
                            lastMessage.renderedHtml(),
                            contextUsage.usage(),
                            chatService.planState(resolvedRequest.conversationId())
                            )
                        );
                    emitter.complete();
                } catch (Exception e) {
                    failPlanExecution.accept(new IllegalStateException(
                        "Plan execution stream failed while finalizing completion.", e
                    ));
                    emitter.completeWithError(e);
                }
            }
        );
        guard.set(subscription);
        return emitter;
    }

    @PostMapping("/turns/{turnId}/interrupt")
    public InterruptResult interrupt(@PathVariable String turnId, @Valid @RequestBody ChatRequest.TurnInterrupt request) {
        String conversationId = normalize(request == null ? null : request.conversationId());
        String token = request == null ? null : request.interruptToken();
        String message = normalize(request == null ? null : request.message());
        if (conversationId == null || token == null || message == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId, interruptToken, and message are required");
        }
        return activeTurnRegistry.interrupt(turnId, conversationId, token, message);
    }

    @GetMapping("/sessions")
    public ChatSessions sessions() {
        return new ChatSessions(chatService.listConversationIds(), chatService.listSessions());
    }

    @GetMapping("/{conversationId}/history")
    public ChatHistory history(@PathVariable String conversationId) {
        String model = chatService.storedConversationModel(conversationId);
        StoredContextUsage contextUsage = chatService.maintainContextUsage(conversationId, model);
        return new ChatHistory(
            conversationId,
            chatService.conversationTitle(conversationId),
            chatService.conversationTitleJobStatus(conversationId),
            model,
            chatService.history(conversationId),
            contextUsage.usage(),
            chatService.planState(conversationId)
        );
    }

    @PatchMapping("/{conversationId}/title")
    public ChatSession rename(@PathVariable String conversationId, @Valid @RequestBody ChatRequest.SetTitle request) {
        requireValidUuid(conversationId);

        if (!chatService.conversationExists(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found: " + conversationId);
        }

        String title = normalize(request == null ? null : request.title());
        if (title == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "title is required");
        }
        return chatService.renameConversation(conversationId, title);
    }

    @PatchMapping("/{conversationId}/favorite")
    public ChatSession favorite(@PathVariable String conversationId, @Valid @RequestBody ChatRequest.Favorite request) {
        requireExistingConversation(conversationId);
        return chatService.setConversationFavorite(conversationId, request != null && request.favorite());
    }

    @PatchMapping("/{conversationId}/archive")
    public ChatSession archive(@PathVariable String conversationId, @Valid @RequestBody ChatRequest.Archive request) {
        requireExistingConversation(conversationId);
        return chatService.setConversationArchived(conversationId, request != null && request.archived());
    }

    @PostMapping("/commands")
    public ChatResponse.CmdResponse command(@Valid @RequestBody ChatRequest.CmdRequest request) {
        String command = normalize(request.command());
        if (command == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "command is required");
        }

        String[] parts = command.split("\\s+");
        String rootCommand = commandName(parts[0]);
        return switch (rootCommand.toLowerCase()) {
            case "new" -> {
                requireNoArguments(rootCommand, parts);
                yield handleNew();
            }
            case "plan" -> {
                requireNoArguments(rootCommand, parts);
                yield handlePlan(request.conversationId(), request.model(), request.planningModel());
            }
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown command: " + rootCommand);
        };
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String conversationId) {
        chatService.clearConversation(conversationId);
    }

    @PostMapping("/{conversationId}/plan/answers")
    public ChatResponse.MsgResponse answerPlanPrompt(
        @PathVariable String conversationId,
        @Valid @RequestBody ChatRequest.PlanAnswer request
    ) {
        requireValidUuid(conversationId);
        try {
            return chatService.submitPlanAnswer(
                conversationId,
                request == null ? null : request.answer(),
                request == null ? null : request.notes(),
                request == null ? null : request.questionIndex()
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/{conversationId}/plan/approve")
    public ChatPlanState approvePlan(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        try {
            return chatService.approvePlan(conversationId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/{conversationId}/plan/continue")
    public ChatPlanState continuePlanning(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        try {
            return chatService.continuePlanning(conversationId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PatchMapping("/{conversationId}/plan/cancel")
    public ChatPlanState cancelPlanAction(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        chatService.exitPlan(conversationId);
        return chatService.planState(conversationId);
    }

    @PatchMapping("/{conversationId}/plan/save-task")
    public SaveTaskResponse savePlanAsTask(
        @PathVariable String conversationId,
        @RequestBody(required = false) SaveTaskRequest request
    ) {
        requireValidUuid(conversationId);
        try {
            PlanDefinition savedTask = chatService.savePlanAsTask(
                conversationId,
                request == null ? null : request.title()
            );
            return new SaveTaskResponse(savedTask.id(), savedTask.title(), chatService.planState(conversationId));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/{conversationId}/plan/execute")
    public ChatResponse.CmdResponse executePlan(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "Direct plan execution is disabled. Save the plan and send it to an agent.");
    }

    @PostMapping("/plans/{planId}/continue")
    public ChatResponse.CmdResponse continuePlanDefinition(
        @PathVariable String planId,
        @RequestBody(required = false) PlanStartRequest request
    ) {
        String conversationId = normalize(request == null ? null : request.conversationId());
        if (conversationId == null) {
            conversationId = chatService.newConversationId();
        }
        ChatResponse.MsgResponse response = chatService.beginPlanFromDefinition(
            conversationId,
            planId,
            request == null ? null : request.model(),
            request == null ? null : request.planningModel(),
            request == null ? null : request.userInstruction()
        );
        List<String> conversationIds = new ArrayList<>(chatService.listConversationIds());
        if (!conversationIds.contains(conversationId)) {
            conversationIds.add(0, conversationId);
        }
        return new ChatResponse.CmdResponse(
            conversationId,
            response.model(),
            response.response(),
            List.copyOf(conversationIds),
            chatService.history(conversationId),
            response.contextUsage(),
            chatService.planState(conversationId),
            response.toolActivities()
        );
    }

    @DeleteMapping("/{conversationId}/plan")
    public ChatResponse.CmdResponse cancelPlan(@PathVariable String conversationId) {
        requireValidUuid(conversationId);
        return handleExitPlan(conversationId);
    }

    private ChatResponse.CmdResponse handleNew() {
        return new ChatResponse.CmdResponse(
            null,
            null,
            "New chat",
            chatService.listConversationIds(),
            List.of(),
            null,
            ChatPlanState.normal()
        );
    }

    private ChatResponse.CmdResponse handlePlan(String requestConversationId, String selectedModel, String planningModel) {
        String conversationId = normalize(requestConversationId);
        if (conversationId == null) {
            conversationId = chatService.newConversationId();
        }
        ChatResponse.MsgResponse response = chatService.beginPlan(conversationId, selectedModel, planningModel);
        List<String> conversationIds = new ArrayList<>(chatService.listConversationIds());
        if (!conversationIds.contains(conversationId)) {
            conversationIds.add(0, conversationId);
        }
        return new ChatResponse.CmdResponse(
            conversationId,
            response.model(),
            response.response(),
            List.copyOf(conversationIds),
            chatService.history(conversationId),
            response.contextUsage(),
            chatService.planState(conversationId)
        );
    }

    private ChatResponse.CmdResponse handleExitPlan(String requestConversationId) {
        String conversationId = requiredConversationId(requestConversationId, "plan cancellation requires an active conversation");
        chatService.exitPlan(conversationId);
        String model = chatService.storedConversationModel(conversationId);
        StoredContextUsage contextUsage = chatService.maintainContextUsage(conversationId, model);
        return new ChatResponse.CmdResponse(
            conversationId,
            model,
            "Exited plan mode and discarded the draft plan.",
            chatService.listConversationIds(),
            chatService.history(conversationId),
            contextUsage.usage(),
            chatService.planState(conversationId)
        );
    }

    private String commandName(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    private void requireNoArguments(String command, String[] parts) {
        if (parts.length > 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, command + " does not accept arguments");
        }
    }

    private String requiredConversationId(String conversationId, String message) {
        String normalized = normalize(conversationId);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        requireValidUuid(normalized);
        return normalized;
    }

    private void requireExistingConversation(String conversationId) {
        requireValidUuid(conversationId);
        if (!chatService.conversationExists(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "conversation not found: " + conversationId);
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void recordStreamError(ResolvedChatRequest request, String errorType, Throwable error) {
        if (auditService != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            auditService.recordError(
                request.conversationId(), errorType, error.getMessage(), sw.toString(), request.model());
        }
    }

    private void requireValidUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid UUID: " + value);
        }
    }
}
