package io.mindspice.magenta2.api.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.EventReactionService;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxService;
import io.mindspice.magenta2.ai.orchestration.runtime.ScheduleService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/agents/{agentId}")
public class AgentOrchestrationController {
    private final InboxService inboxService;
    private final AssignmentService assignmentService;
    private final ScheduleService scheduleService;
    private final EventReactionService reactionService;
    private final AgentProfileService agentProfileService;
    private final ChatService chatService;

    @Value("${magenta.features.schedules-enabled:false}")
    private boolean schedulesEnabled;

    @Value("${magenta.features.reactions-enabled:false}")
    private boolean reactionsEnabled;

    public AgentOrchestrationController(
        InboxService inboxService,
        AssignmentService assignmentService,
        ScheduleService scheduleService,
        EventReactionService reactionService,
        AgentProfileService agentProfileService,
        ChatService chatService
    ) {
        this.inboxService = inboxService;
        this.assignmentService = assignmentService;
        this.scheduleService = scheduleService;
        this.reactionService = reactionService;
        this.agentProfileService = agentProfileService;
        this.chatService = chatService;
    }

    @GetMapping("/inbox")
    public List<InboxMessage> inbox(@PathVariable String agentId) {
        return inboxService.messages(agentId);
    }

    @PostMapping("/inbox")
    public InboxMessage send(@PathVariable String agentId, @Valid @RequestBody InboxMessage message) {
        try {
            return inboxService.send(agentId, message);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/inbox/{messageId}/read")
    public InboxMessage read(@PathVariable String messageId) {
        return inboxService.markRead(messageId);
    }

    @PostMapping("/inbox/{messageId}/handled")
    public InboxMessage handled(@PathVariable String messageId) {
        return inboxService.markHandled(messageId);
    }

    @GetMapping("/assignments")
    public List<WorkAssignment> assignments(@PathVariable String agentId) {
        return assignmentService.assignments(agentId);
    }

    @PostMapping("/assignments")
    public WorkAssignment assign(
        @PathVariable String agentId,
        @Valid @RequestBody AgentAssignmentCreateRequest request
    ) {
        try {
            return assignmentService.create(new AssignmentRequest(
                agentId,
                request.jobId(),
                request.jobItemId(),
                request.assignmentType(),
                request.priority(),
                request.modelOverride(),
                request.workspaceId(),
                request.input()
            ));
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/assignments/{assignmentId}/cancel")
    public WorkAssignment cancel(@PathVariable String assignmentId) {
        return assignmentService.cancel(assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/pause")
    public WorkAssignment pause(@PathVariable String assignmentId) {
        return assignmentService.pause(assignmentId);
    }

    @PostMapping("/assignments/{assignmentId}/resume")
    public WorkAssignment resume(@PathVariable String assignmentId) {
        return assignmentService.resume(assignmentId);
    }

    @GetMapping("/schedules")
    public List<AgentSchedule> schedules(@PathVariable String agentId) {
        if (!schedulesEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedules are not available");
        }
        return scheduleService.schedules(agentId);
    }

    @PostMapping("/schedules")
    public AgentSchedule schedule(@PathVariable String agentId, @Valid @RequestBody AgentSchedule schedule) {
        if (!schedulesEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedules are not available");
        }
        try {
            return scheduleService.save(agentId, schedule);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/event-reactions")
    public List<AgentEventReaction> reactions(@PathVariable String agentId) {
        if (!reactionsEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event reactions are not available");
        }
        return reactionService.reactions(agentId);
    }

    @PostMapping("/event-reactions")
    public AgentEventReaction reaction(@PathVariable String agentId, @Valid @RequestBody AgentEventReaction reaction) {
        if (!reactionsEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event reactions are not available");
        }
        try {
            return reactionService.save(agentId, reaction);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String agentId, @Valid @RequestBody AgentChatRequest request) {
        SseEmitter emitter = SseStreamLifecycle.createEmitter();
        SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
        SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

        Disposable subscription = Flux.defer(() -> {
                AgentProfile agent = agentProfileService.get(agentId);
                String message = request == null ? null : request.message();
                if (message == null || message.isBlank()) {
                    return Flux.just(new AgentChatEvent("error", Map.of(
                        "event", "error",
                        "error", "message is required"
                    )));
                }
                String pageContext = request.pageContext() == null || request.pageContext().isBlank()
                    ? "orchestration page"
                    : request.pageContext();
                String prompt = "Agent page context: " + pageContext + "\n\n" + message;
                String model = request.model() == null || request.model().isBlank() ? agent.defaultModel() : request.model();
                var start = new LinkedHashMap<String, Object>();
                start.put("event", "start");
                start.put("agentId", agent.id());
                start.put("agentName", agent.name());
                return Flux.concat(
                    Flux.just(new AgentChatEvent("start", start)),
                    Mono.fromCallable(() -> donePayload(agent, request, prompt, model))
                        .subscribeOn(Schedulers.boundedElastic())
                        .map(done -> new AgentChatEvent("done", done))
                        .onErrorResume(error -> Mono.just(new AgentChatEvent("error", Map.of(
                            "event", "error",
                            "error", error.getMessage()
                        ))))
                        .flux()
                );
            })
            .subscribe(
                event -> {
                    if (!SseStreamLifecycle.trySendSseEvent(emitter, event.name(), event.data())) {
                        guard.dispose();
                        SseStreamLifecycle.completeQuietly(emitter);
                        return;
                    }
                    if ("done".equals(event.name()) || "error".equals(event.name())) {
                        SseStreamLifecycle.completeQuietly(emitter);
                    }
                },
                error -> {
                    var errorPayload = new LinkedHashMap<String, Object>();
                    errorPayload.put("event", "error");
                    errorPayload.put("error", error.getMessage());
                    if (SseStreamLifecycle.trySendSseEvent(emitter, "error", errorPayload)) {
                        SseStreamLifecycle.completeQuietly(emitter);
                    }
                }
            );

        guard.set(subscription);
        return emitter;
    }

    private Map<String, Object> donePayload(AgentProfile agent, AgentChatRequest request, String prompt, String model) {
        ChatResponse response = chatService.chat(new ChatRequest.MsgRequest(
            request.conversationId(), prompt, model, null
        ));
        if (!(response instanceof ChatResponse.MsgResponse messageResponse)) {
            throw new IllegalStateException("agent chat returned an unsupported response");
        }
        var done = new LinkedHashMap<String, Object>();
        done.put("event", "done");
        done.put("agentId", agent.id());
        done.put("conversationId", messageResponse.conversationId());
        done.put("model", messageResponse.model());
        done.put("message", messageResponse.response());
        return done;
    }

    private record AgentChatEvent(String name, Map<String, Object> data) {}

    public record AgentChatRequest(String conversationId, String message, String model, String pageContext) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentAssignmentCreateRequest(
        String jobId,
        String jobItemId,
        @NotNull AssignmentType assignmentType,
        Integer priority,
        String modelOverride,
        String workspaceId,
        Map<String, Object> input
    ) {
        public AgentAssignmentCreateRequest {
            input = input == null ? Map.of() : Map.copyOf(input);
        }
    }
}
