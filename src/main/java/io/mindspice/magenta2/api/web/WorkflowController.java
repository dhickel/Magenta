package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
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

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {
    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
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
    public WorkflowDefinition create(@RequestBody WorkflowDefinition workflow) {
        try {
            return workflowService.saveWorkflow(workflow);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/{workflowId}")
    public WorkflowDefinition update(@PathVariable String workflowId, @RequestBody WorkflowDefinition workflow) {
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
    public SseEmitter streamRun(@PathVariable String workflowId, @RequestBody(required = false) Map<String, Object> ignored) {
        SseEmitter emitter = new SseEmitter(0L);
        try {
            WorkflowDefinition workflow = workflowService.getWorkflow(workflowId);
            send(emitter, "started", Map.of("event", "started", "workflowId", workflowId));
            for (var step : workflow.steps()) {
                send(emitter, "step_started", Map.of(
                    "event", "step_started",
                    "stepKey", step.stepKey(),
                    "taskId", step.taskId()
                ));
            }
            WorkflowRun run = workflowService.runSynchronously(workflowId);
            for (var stepRun : run.stepRuns()) {
                send(emitter, "step_completed", Map.of(
                    "event", "step_completed",
                    "stepKey", stepRun.stepKey(),
                    "taskRunId", stepRun.taskRunId(),
                    "status", stepRun.status().name()
                ));
            }
            String terminalEvent = run.status().name().equals("COMPLETED") ? "completed" : "failed";
            send(emitter, terminalEvent, Map.of(
                "event", terminalEvent,
                "runId", run.id(),
                "status", run.status().name(),
                "finalOutputs", run.finalOutputs(),
                "error", run.errorText() == null ? "" : run.errorText()
            ));
            emitter.complete();
        } catch (Exception exception) {
            try {
                send(emitter, "failed", Map.of("event", "failed", "error", exception.getMessage()));
            } catch (IOException ignoredError) {
            }
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }
}
