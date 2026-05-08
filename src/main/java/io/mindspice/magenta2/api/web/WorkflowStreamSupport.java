package io.mindspice.magenta2.api.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import reactor.core.publisher.Flux;

/**
 * Event mapping and context conversion support for workflow run SSE streams.
 * Extracted from WorkflowController to keep controllers thin.
 */
public final class WorkflowStreamSupport {

    private WorkflowStreamSupport() {}

    /**
     * Maps an orchestration run result to started/terminal SSE events.
     */
    public static Flux<SsePayload> orchestrationRunEvents(String workflowId, OrchestrationRunResult result) {
        String terminalEvent = terminalEventName(result.assignment().status().name());
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
    }

    /**
     * Builds SSE events for a synchronous workflow execution.
     * Emits started, step_started, step_completed, and terminal events.
     */
    public static Flux<SsePayload> synchronousRunEvents(
        String workflowId,
        WorkflowService workflowService
    ) {
        return Flux.defer(() -> {
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
            String terminalEvent = terminalEventName(run.status().name());
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

    /**
     * Converts a WorkflowRunRequest to an OrchestrationRunContext.
     */
    public static OrchestrationRunContext toContext(WorkflowController.WorkflowRunRequest request) {
        if (request == null) {
            return new OrchestrationRunContext(null, null, null, null, null);
        }
        return new OrchestrationRunContext(
            request.agentId(), request.jobId(), request.workspaceId(),
            request.modelOverride(), request.priority()
        );
    }

    /**
     * Creates a failed-event SSE payload for error reporting.
     */
    public static SsePayload errorPayload(String errorMessage) {
        return new SsePayload("failed", Map.of(
            "event", "failed",
            "error", errorMessage
        ));
    }

    private static String terminalEventName(String statusName) {
        return "COMPLETED".equals(statusName) ? "completed" : "failed";
    }
}
