package io.mindspice.magenta2.api.web;

import io.mindspice.magenta2.ai.orchestration.workflow.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Workflow definition CRUD, run lifecycle, SSE streaming, and inbox endpoints.
 */
@RestController
public class WorkflowController {
    private final WorkflowService workflowService;
    private final InboxService inboxService;

    public WorkflowController(WorkflowService workflowService, InboxService inboxService) {
        this.workflowService = workflowService;
        this.inboxService = inboxService;
    }

    // ════════════════════════════════════════════════════════════════
    //  Workflow Definition CRUD
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/api/workflows")
    public List<WorkflowDefinition> list() {
        return workflowService.listDefinitions();
    }

    @GetMapping("/api/workflows/{workflowId}")
    public WorkflowDefinition get(@PathVariable String workflowId) {
        try {
            return workflowService.getDefinition(workflowId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/api/workflows")
    public WorkflowDefinition create(@RequestBody WorkflowDefinition definition) {
        try {
            return workflowService.saveDefinitionValidated(
                new WorkflowDefinition(UUID.randomUUID().toString(),
                    definition.title(), definition.summary(),
                    definition.nodes(), definition.routes(), null, null));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/api/workflows/{workflowId}")
    public WorkflowDefinition update(@PathVariable String workflowId,
                                     @RequestBody WorkflowDefinition definition) {
        try {
            return workflowService.saveDefinitionValidated(
                new WorkflowDefinition(workflowId,
                    definition.title(), definition.summary(),
                    definition.nodes(), definition.routes(),
                    definition.createdAt(), definition.updatedAt()));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/api/workflows/{workflowId}")
    public void delete(@PathVariable String workflowId) {
        workflowService.deleteDefinition(workflowId);
    }

    // ════════════════════════════════════════════════════════════════
    //  Validation (structured errors + warnings)
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/api/workflows/validate")
    public WorkflowValidator.ValidationResult validateNew(@RequestBody WorkflowDefinition definition) {
        return workflowService.validateGraph(
            new WorkflowDefinition(UUID.randomUUID().toString(),
                definition.title(), definition.summary(),
                definition.nodes(), definition.routes(), null, null));
    }

    @PostMapping("/api/workflows/{workflowId}/validate")
    public WorkflowValidator.ValidationResult validate(@PathVariable String workflowId,
                                  @RequestBody WorkflowDefinition definition) {
        return workflowService.validateGraph(
            new WorkflowDefinition(workflowId, definition.title(), definition.summary(),
                definition.nodes(), definition.routes(), null, null));
    }

    // ════════════════════════════════════════════════════════════════
    //  Runs
    // ════════════════════════════════════════════════════════════════

    @PostMapping("/api/workflows/{workflowId}/runs")
    public WorkflowRun startRun(@PathVariable String workflowId) {
        try {
            return workflowService.startRun(workflowId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping(value = "/api/workflows/{workflowId}/runs/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String workflowId) {
        SseEmitter emitter = SseStreamLifecycle.createEmitter();
        SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
        SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

        try {
            WorkflowRun run = workflowService.startRun(workflowId);

            SseStreamLifecycle.sendSseEvent(emitter, "started",
                Map.of("workflowRunId", run.id(), "workflowId", workflowId,
                       "event", "started"));

            reactor.core.Disposable subscription = reactor.core.publisher.Flux
                .interval(java.time.Duration.ofSeconds(1))
                .subscribe(
                    i -> {
                        try {
                            WorkflowRun current = workflowService.getRun(run.id());
                            String eventType = switch (current.status()) {
                                case WAITING -> "waiting";
                                case COMPLETED -> "completed";
                                case FAILED, CANCELLED, NEEDS_REVIEW -> "failed";
                                default -> "progress";
                            };
                            if (SseStreamLifecycle.trySendSseEvent(emitter, eventType,
                                    Map.of("event", eventType,
                                           "workflowRunId", current.id(),
                                           "status", current.status().wireName(),
                                           "nodeIndex", current.currentNodeIndex()))) {
                                if (current.isTerminal()) {
                                    guard.dispose();
                                    SseStreamLifecycle.completeQuietly(emitter);
                                }
                            }
                        } catch (Exception e) {
                            guard.dispose();
                            SseStreamLifecycle.completeQuietly(emitter);
                        }
                    },
                    error -> {
                        guard.dispose();
                        SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                            Map.of("event", "failed", "error", error.getMessage()));
                        SseStreamLifecycle.completeQuietly(emitter);
                    }
                );
            guard.set(subscription);
        } catch (IllegalArgumentException exception) {
            SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                Map.of("event", "failed", "error", exception.getMessage()));
            SseStreamLifecycle.completeQuietly(emitter);
        } catch (Exception exception) {
            SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                Map.of("event", "failed", "error", exception.getMessage()));
            SseStreamLifecycle.completeQuietly(emitter);
        }

        return emitter;
    }

    @GetMapping("/api/workflow-runs/{runId}")
    public WorkflowRun getRun(@PathVariable String runId) {
        try {
            return workflowService.getRun(runId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @GetMapping("/api/workflows/{workflowId}/runs")
    public List<WorkflowRun> listRuns(@PathVariable String workflowId) {
        return workflowService.listRuns(workflowId);
    }

    @PostMapping("/api/workflow-runs/{runId}/resume")
    public WorkflowRun resumeRun(@PathVariable String runId) {
        try {
            return workflowService.resumeRun(runId);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  User Inbox
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/api/users/inbox")
    public List<InboxMessage> userInbox() {
        return inboxService.userInbox();
    }

    @PostMapping("/api/users/inbox/{messageId}/respond")
    public Map<String, Object> respondUser(@PathVariable String messageId,
                                           @RequestBody InboxRespondRequest request) {
        try {
            InboxMessage message = inboxService.respondUserApproval(
                messageId, request.approved(), request.comment());
            return Map.of(
                "messageId", message.id(),
                "responded", true,
                "approved", request.approved(),
                "workflowRunId", workflowRunId(message)
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    // ── Request DTOs ──

    public record WorkflowRunRequest(
        String agentId,
        String jobId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {}

    public record InboxRespondRequest(boolean approved, String comment) {
    }

    private String workflowRunId(InboxMessage message) {
        if (message.metadataJson() == null || message.metadataJson().isBlank()) {
            return "";
        }
        try {
            Object value = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(message.metadataJson(), Map.class)
                .get("workflowRunId");
            return value == null ? "" : value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
