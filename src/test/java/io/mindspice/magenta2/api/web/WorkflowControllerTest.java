package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowRun;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowService;
import io.mindspice.magenta2.ai.chat.workflow.WorkflowStep;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunnerService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowControllerTest {

    @Test
    void createRejectsBlankTitle() {
        WorkflowController controller = new WorkflowController(stubService(), null);
        WorkflowDefinition blankTitle = new WorkflowDefinition(
            null, "  ", "summary", List.of(), null, null
        );

        assertThatThrownBy(() -> controller.create(blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void getReturns404ForMissingId() {
        WorkflowController controller = new WorkflowController(missingService(), null);

        assertThatThrownBy(() -> controller.get("non-existent"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void getRunReturns404ForMissingRunId() {
        WorkflowController controller = new WorkflowController(missingRunService(), null);

        assertThatThrownBy(() -> controller.getRun("non-existent-run"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void updateRejectsBlankTitle() {
        WorkflowController controller = new WorkflowController(stubService(), null);
        WorkflowDefinition blankTitle = new WorkflowDefinition(
            null, "  ", "summary", List.of(), null, null
        );

        assertThatThrownBy(() -> controller.update("workflow-1", blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void streamRunAcceptsNullBody() {
        WorkflowController controller = new WorkflowController(
            stubService(),
            new StubOrchestrationRunService(true)
        );

        assertThat(controller.streamRun("workflow-1", null)).isNotNull();
    }

    @Test
    void listReturnsWorkflows() {
        WorkflowController controller = new WorkflowController(stubService(), null);

        List<WorkflowDefinition> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Test Workflow");
    }

    private static WorkflowService stubService() {
        return new WorkflowService(null, null, null) {
            @Override
            public List<WorkflowDefinition> listWorkflows() {
                return List.of(new WorkflowDefinition(
                    "wf-1", "Test Workflow", "summary",
                    List.of(new WorkflowStep("step_1", "task-1", List.of())),
                    null, null
                ));
            }

            @Override
            public WorkflowDefinition saveWorkflow(WorkflowDefinition workflow) {
                if (workflow.title() == null || workflow.title().isBlank()) {
                    throw new IllegalArgumentException("title is required");
                }
                return new WorkflowDefinition(
                    "wf-1", workflow.title(), workflow.summary(), workflow.steps(), null, null
                );
            }

            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                if (!"wf-1".equals(workflowId)) {
                    throw new IllegalStateException("workflow not found: " + workflowId);
                }
                return listWorkflows().get(0);
            }

            @Override
            public WorkflowRun getRun(String runId) {
                if (!"run-1".equals(runId)) {
                    throw new IllegalStateException("run not found: " + runId);
                }
                return null;
            }
        };
    }

    private static WorkflowService missingService() {
        return new WorkflowService(null, null, null) {
            @Override
            public WorkflowDefinition getWorkflow(String workflowId) {
                throw new IllegalStateException("workflow not found: " + workflowId);
            }
        };
    }

    private static WorkflowService missingRunService() {
        return new WorkflowService(null, null, null) {
            @Override
            public WorkflowRun getRun(String runId) {
                throw new IllegalStateException("run not found: " + runId);
            }
        };
    }

    private static class StubOrchestrationRunService extends OrchestrationRunService {
        private final boolean hasContext;

        StubOrchestrationRunService(boolean hasContext) {
            super(null, null, null);
            this.hasContext = hasContext;
        }

        @Override
        public OrchestrationRunResult runWorkflow(String workflowId, io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext context) {
            return new OrchestrationRunResult(
                new WorkAssignment("assign-1", "agent-1", "job-1", null,
                    io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType.WORKFLOW_RUN,
                    0, OrchestrationStatus.COMPLETED, null, null, 0,
                    Map.of(), Map.of(), Map.of(), Map.of(), null, null, null, null, null, null, null),
                "run-1", Map.of()
            );
        }
    }
}
