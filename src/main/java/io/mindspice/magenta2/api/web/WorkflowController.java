package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.util.ArrayList;
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
            OrchestrationRunContext context = context(request);
            Flux<SsePayload> stream;
            if (context.hasContext()) {
                stream = Flux.defer(() -> {
                    OrchestrationRunResult result = orchestrationRunService.runWorkflow(workflowId, context);
                    String terminalEvent = result.assignment().status().name().equals("COMPLETED") ? "completed" : "failed";
                    return Flux.just(
                        new SsePayload("started", Map.of(
                            "event", "started",
                            "assignmentId", result.assignment().id(),
                            "runId", result.runId() == null ? "" : result.runId(),
                            "workflowId", workflowId
                        )),
                        new SsePayload(terminalEvent, Map.of(
                            "event", terminalEvent,
                            "assignmentId", result.assignment().id(),
                            "runId", result.runId() == null ? "" : result.runId(),
                            "status", result.assignment().status().name(),
                            "finalOutputs", result.outputValues(),
                            "error", result.assignment().errorText() == null ? "" : result.assignment().errorText()
                        ))
                    );
                });
            } else {
                stream = Flux.defer(() -> {
                    WorkflowDefinition workflow = workflowService.getWorkflow(workflowId);
                    List<SsePayload> events = new ArrayList<>();
                    events.add(new SsePayload("started", Map.of("event", "started", "workflowId", workflowId)));
                    for (var step : workflow.steps()) {
                        events.add(new SsePayload("step_started", Map.of(
                            "event", "step_started",
                            "stepKey", step.stepKey(),
                            "taskId", step.taskId()
                        )));
                    }
                    WorkflowRun run = workflowService.runSynchronously(workflowId);
                    for (var stepRun : run.stepRuns()) {
                        events.add(new SsePayload("step_completed", Map.of(
                            "event", "step_completed",
                            "stepKey", stepRun.stepKey(),
                            "taskRunId", stepRun.taskRunId(),
                            "status", stepRun.status().name()
                        )));
                    }
                    String terminalEvent = run.status().name().equals("COMPLETED") ? "completed" : "failed";
                    events.add(new SsePayload(terminalEvent, Map.of(
                        "event", terminalEvent,
                        "runId", run.id(),
                        "status", run.status().name(),
                        "finalOutputs", run.finalOutputs(),
                        "error", run.errorText() == null ? "" : run.errorText()
                    )));
                    return Flux.fromIterable(events);
                });
            }
            Disposable subscription = stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    event -> {
                        try {
                            send(emitter, event.name(), event.data());
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    },
                    error -> {
                        try {
                            send(emitter, "failed", Map.of("event", "failed", "error", error.getMessage()));
                            emitter.complete();
                        } catch (IOException ioException) {
                            emitter.completeWithError(ioException);
                        }
                    },
                    emitter::complete
                );
            guard.set(subscription);
        } catch (IllegalArgumentException exception) {
            try {
                send(emitter, "failed", Map.of("event", "failed", "error", exception.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.complete();
        } catch (Exception exception) {
            try {
                send(emitter, "failed", Map.of("event", "failed", "error", exception.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private OrchestrationRunContext context(WorkflowRunRequest request) {
        if (request == null) {
            return new OrchestrationRunContext(null, null, null, null, null);
        }
        return new OrchestrationRunContext(
            request.agentId(), request.jobId(), request.workspaceId(), request.modelOverride(), request.priority()
        );
    }

    private record SsePayload(String name, Object data) {
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
