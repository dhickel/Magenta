package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService workflowService;
    private final OrchestrationRunService orchestrationRunService;

    public WorkflowController(WorkflowService workflowService, OrchestrationRunService orchestrationRunService) {
        this.workflowService = workflowService;
        this.orchestrationRunService = orchestrationRunService;
    }

    @GetMapping
    public List<WorkflowDefinition> list() {
        return workflowService.listWorkflows();
    }

    @GetMapping("/{workflowId}")
    public WorkflowDefinition get(@PathVariable String workflowId) {
        try {
            return workflowService.getWorkflow(workflowId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping
    public WorkflowDefinition create(@Valid @RequestBody WorkflowDefinition workflow) {
        try {
            return workflowService.saveWorkflow(workflow);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/{workflowId}")
    public WorkflowDefinition update(@PathVariable String workflowId, @Valid @RequestBody WorkflowDefinition workflow) {
        try {
            return workflowService.saveWorkflow(new WorkflowDefinition(
                workflowId, workflow.title(), workflow.summary(), workflow.steps(), workflow.createdAt(), workflow.updatedAt()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{workflowId}")
    public void delete(@PathVariable String workflowId) {
        workflowService.deleteWorkflow(workflowId);
    }

    @GetMapping("/{workflowId}/warnings")
    public List<String> warnings(@PathVariable String workflowId) {
        return workflowService.compatibilityWarnings(workflowService.getWorkflow(workflowId));
    }

    @GetMapping("/{workflowId}/runs")
    public List<WorkflowRun> listRuns(@PathVariable String workflowId) {
        return workflowService.listRuns(workflowId);
    }

    @GetMapping("/runs/{runId}")
    public WorkflowRun getRun(@PathVariable String runId) {
        try {
            return workflowService.getRun(runId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping(value = "/{workflowId}/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String workflowId, @RequestBody(required = false) WorkflowRunRequest request) {
        SseEmitter emitter = SseStreamLifecycle.createEmitter();
        SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
        SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

        try {
            OrchestrationRunContext context = WorkflowStreamSupport.toContext(request);
            Flux<SsePayload> stream;
            if (context.hasContext()) {
                stream = Flux.defer(() -> {
                    OrchestrationRunResult result = orchestrationRunService.runWorkflow(workflowId, context);
                    return WorkflowStreamSupport.orchestrationRunEvents(workflowId, result);
                });
            } else {
                stream = WorkflowStreamSupport.synchronousRunEvents(workflowId, workflowService);
            }
            Disposable subscription = stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    event -> {
                        if (!SseStreamLifecycle.trySendSseEvent(emitter, event.name(), event.data())) {
                            guard.dispose();
                            SseStreamLifecycle.completeQuietly(emitter);
                        }
                    },
                    error -> {
                        if (SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                                Map.of("event", "failed", "error", error.getMessage()))) {
                            SseStreamLifecycle.completeQuietly(emitter);
                        }
                    },
                    () -> SseStreamLifecycle.completeQuietly(emitter)
                );
            guard.set(subscription);
        } catch (IllegalArgumentException exception) {
            if (SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                    Map.of("event", "failed", "error", exception.getMessage()))) {
                SseStreamLifecycle.completeQuietly(emitter);
            }
        } catch (Exception exception) {
            if (SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                    Map.of("event", "failed", "error", exception.getMessage()))) {
                SseStreamLifecycle.completeQuietly(emitter);
            }
        }
        return emitter;
    }

    public record WorkflowRunRequest(
        String agentId,
        String jobId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {
    }
}
