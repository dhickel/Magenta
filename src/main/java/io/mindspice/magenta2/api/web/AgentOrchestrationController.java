package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.model.ChatRequest;
import io.mindspice.magenta2.ai.chat.model.ChatResponse;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentEventReaction;
import io.mindspice.magenta2.ai.orchestration.runtime.AgentSchedule;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.EventReactionService;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.runtime.InboxService;
import io.mindspice.magenta2.ai.orchestration.runtime.ScheduleService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
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

@RestController
@RequestMapping("/api/agents/{agentId}")
public class AgentOrchestrationController {
    private final InboxService inboxService;
    private final AssignmentService assignmentService;
    private final ScheduleService scheduleService;
    private final EventReactionService reactionService;
    private final AgentProfileService agentProfileService;
    private final ChatService chatService;

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
    public InboxMessage send(@PathVariable String agentId, @RequestBody InboxMessage message) {
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
    public WorkAssignment assign(@PathVariable String agentId, @RequestBody AssignmentRequest request) {
        try {
            return assignmentService.create(new AssignmentRequest(
                agentId, request.jobId(), request.jobItemId(), request.assignmentType(), request.priority(),
                request.modelOverride(), request.workspaceId(), request.input()
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
        return scheduleService.schedules(agentId);
    }

    @PostMapping("/schedules")
    public AgentSchedule schedule(@PathVariable String agentId, @RequestBody AgentSchedule schedule) {
        try {
            return scheduleService.save(agentId, schedule);
        } catch (IllegalArgumentException | java.time.DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/event-reactions")
    public List<AgentEventReaction> reactions(@PathVariable String agentId) {
        return reactionService.reactions(agentId);
    }

    @PostMapping("/event-reactions")
    public AgentEventReaction reaction(@PathVariable String agentId, @RequestBody AgentEventReaction reaction) {
        try {
            return reactionService.save(agentId, reaction);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String agentId, @RequestBody AgentChatRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            AgentProfile agent = agentProfileService.get(agentId);
            String message = request == null ? null : request.message();
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("message is required");
            }
            send(emitter, "start", Map.of("event", "start", "agentId", agent.id(), "agentName", agent.name()));
            String pageContext = request.pageContext() == null || request.pageContext().isBlank()
                ? "orchestration page"
                : request.pageContext();
            String prompt = "Agent page context: " + pageContext + "\n\n" + message;
            String model = request.model() == null || request.model().isBlank() ? agent.defaultModel() : request.model();
            ChatResponse response = chatService.chat(new ChatRequest.MsgRequest(
                request.conversationId(), prompt, model, null
            ));
            if (!(response instanceof ChatResponse.MsgResponse messageResponse)) {
                throw new IllegalStateException("agent chat returned an unsupported response");
            }
            java.util.LinkedHashMap<String, Object> done = new java.util.LinkedHashMap<>();
            done.put("event", "done");
            done.put("agentId", agent.id());
            done.put("conversationId", messageResponse.conversationId());
            done.put("model", messageResponse.model());
            done.put("message", messageResponse.response());
            send(emitter, "done", done);
            emitter.complete();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            try {
                send(emitter, "error", Map.of("event", "error", "error", exception.getMessage()));
                emitter.complete();
            } catch (Exception sendError) {
                emitter.completeWithError(sendError);
            }
        } catch (Exception exception) {
            try {
                send(emitter, "error", Map.of("event", "error", "error", exception.getMessage()));
            } catch (Exception ignored) {
            }
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) throws java.io.IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    public record AgentChatRequest(String conversationId, String message, String model, String pageContext) {
    }
}
