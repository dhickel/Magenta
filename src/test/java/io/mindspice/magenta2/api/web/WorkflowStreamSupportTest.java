package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRunStatus;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStep;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStepRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStepRunStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowStreamSupportTest {

    @Test
    void orchestrationRunEventsEmitsStartedAndCompleted() {
        OrchestrationRunResult result = new OrchestrationRunResult(
            new WorkAssignment("assign-1", "agent-1", "job-1", null,
                AssignmentType.WORKFLOW_RUN, 0, OrchestrationStatus.COMPLETED, null,
                null, 0, Map.of(), Map.of(), Map.of(), Map.of(),
                null, null, null, null, null, null, null),
            "run-1", Map.of("output", "value")
        );

        List<SsePayload> events = WorkflowStreamSupport.orchestrationRunEvents("wf-1", result)
            .collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(0).name()).isEqualTo("started");
        assertThat(events.get(1).name()).isEqualTo("completed");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) events.get(0).data();
        assertThat(payload).containsEntry("workflowId", "wf-1");
    }

    @Test
    void orchestrationRunEventsEmitsFailedOnNonCompletedStatus() {
        OrchestrationRunResult result = new OrchestrationRunResult(
            new WorkAssignment("assign-2", "agent-1", "job-1", null,
                AssignmentType.WORKFLOW_RUN, 0, OrchestrationStatus.FAILED, null,
                null, 0, Map.of(), Map.of(), Map.of(), Map.of(),
                "error occurred", null, null, null, null, null, null),
            "run-2", Map.of()
        );

        List<SsePayload> events = WorkflowStreamSupport.orchestrationRunEvents("wf-1", result)
            .collectList().block();

        assertThat(events).hasSize(2);
        assertThat(events.get(1).name()).isEqualTo("failed");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) events.get(1).data();
        assertThat(payload).containsEntry("error", "error occurred");
    }

    @Test
    void synchronousRunEventsEmitsFullEventSequence() {
        WorkflowService stubService = new WorkflowService(null, null, null) {
            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                return new WorkflowDefinition(workflowId, "Test", "summary",
                    List.of(new WorkflowStep("step_1", "task-1", List.of())),
                    null, null);
            }

            @Override
            public WorkflowRun runSynchronously(String workflowId) {
                return new WorkflowRun(
                    "run-1", workflowId, WorkflowRunStatus.COMPLETED, getWorkflow(workflowId),
                    List.of(new WorkflowStepRun(
                        "step_1", "task-1", "task-run-1",
                        WorkflowStepRunStatus.COMPLETED, Map.of(), Map.of("output", "result"), null
                    )),
                    Map.of("output", "result"), "completed", null,
                    Instant.now(), Instant.now(), Instant.now(), Instant.now()
                );
            }
        };

        List<SsePayload> events = WorkflowStreamSupport.synchronousRunEvents("wf-1", stubService)
            .collectList().block();

        assertThat(events).isNotNull();
        assertThat(events).extracting(SsePayload::name)
            .containsExactly("started", "step_started", "step_completed", "completed");
    }

    @Test
    void toContextWithNullRequestReturnsEmptyContext() {
        OrchestrationRunContext context = WorkflowStreamSupport.toContext(null);
        assertThat(context.hasContext()).isFalse();
    }

    @Test
    void toContextWithRequestPopulatesFields() {
        var request = new WorkflowController.WorkflowRunRequest(
            "agent-1", "job-1", "ws-1", "model-1", 5
        );

        OrchestrationRunContext context = WorkflowStreamSupport.toContext(request);

        assertThat(context.agentId()).isEqualTo("agent-1");
        assertThat(context.jobId()).isEqualTo("job-1");
        assertThat(context.workspaceId()).isEqualTo("ws-1");
        assertThat(context.modelOverride()).isEqualTo("model-1");
        assertThat(context.priority()).isEqualTo(5);
    }

    @Test
    void errorPayloadCreatesFailedEvent() {
        SsePayload payload = WorkflowStreamSupport.errorPayload("workflow failed");

        assertThat(payload.name()).isEqualTo("failed");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) payload.data();
        assertThat(data).containsEntry("error", "workflow failed");
    }
}
