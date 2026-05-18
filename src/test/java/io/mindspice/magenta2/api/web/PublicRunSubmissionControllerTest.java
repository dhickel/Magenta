package io.mindspice.magenta2.api.web;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanKind;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.chat.plan.PlanStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowDefinition;
import io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRunSubmissionControllerTest {

    @Test
    void planRunStreamSubmitsHighPriorityTaskAssignment() {
        CapturingAssignmentService assignmentService = new CapturingAssignmentService();
        PlanController controller = new PlanController(
            new StubPlanService(),
            null,
            assignmentService,
            new StubAgentProfileService()
        );

        controller.streamRun("plan-1", new PlanController.PlanRunRequest(
            Map.of("prompt", "ship it"),
            "conversation-1",
            null,
            null,
            "workspace-1",
            "model-a",
            null
        ));

        assertThat(assignmentService.lastRequest.assignmentType()).isEqualTo(AssignmentType.TASK_RUN);
        assertThat(assignmentService.lastRequest.agentId()).isEqualTo("agent-1");
        assertThat(assignmentService.lastRequest.priority()).isEqualTo(9);
        assertThat(assignmentService.lastRequest.workspaceId()).isEqualTo("workspace-1");
        assertThat(assignmentService.lastRequest.modelOverride()).isEqualTo("model-a");
        assertThat(assignmentService.lastRequest.input()).containsEntry("taskId", "plan-1");
        assertThat(assignmentService.lastRequest.input()).containsEntry("conversationId", "conversation-1");
        assertThat(assignmentService.lastRequest.input()).containsEntry("inputValues", Map.of("prompt", "ship it"));
    }

    @Test
    void planRunStreamEmitsSubmittedSseEventName() throws Exception {
        PlanController controller = new PlanController(
            new StubPlanService(),
            null,
            new CapturingAssignmentService(),
            new StubAgentProfileService()
        );

        SseEmitter emitter = controller.streamRun("plan-1", new PlanController.PlanRunRequest(
            Map.of("prompt", "ship it"),
            "conversation-1",
            null,
            null,
            "workspace-1",
            "model-a",
            null
        ));
        CapturedSse captured = initializeEmitter(emitter);

        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.events).anyMatch(event -> event.contains("event:submitted"));
        assertThat(captured.events).noneMatch(event -> event.contains("event:TaskExecutionEvent"));
    }

    @Test
    void planRunStreamEmitsFailedSseEventNameForSubmissionErrors() throws Exception {
        PlanController controller = new PlanController(
            new StubPlanService(),
            null,
            new CapturingAssignmentService(),
            new EmptyAgentProfileService()
        );

        SseEmitter emitter = controller.streamRun("plan-1", new PlanController.PlanRunRequest(
            Map.of("prompt", "ship it"),
            "conversation-1",
            null,
            null,
            null,
            null,
            null
        ));
        CapturedSse captured = initializeEmitter(emitter);

        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();
        assertThat(captured.events).anyMatch(event -> event.contains("event:failed"));
        assertThat(captured.events).noneMatch(event -> event.contains("event:TaskExecutionEvent"));
    }

    @Test
    void taskRunStreamSubmitsHighPriorityTaskAssignment() {
        CapturingAssignmentService assignmentService = new CapturingAssignmentService();
        TaskController controller = new TaskController(
            new TaskService(new StubPlanService()),
            null,
            null,
            assignmentService,
            new StubAgentProfileService()
        );

        controller.streamRun("plan-1", new TaskController.TaskRunRequest(
            Map.of("prompt", "ship it"),
            null,
            null,
            "job-1",
            null,
            null,
            null
        ));

        assertThat(assignmentService.lastRequest.assignmentType()).isEqualTo(AssignmentType.TASK_RUN);
        assertThat(assignmentService.lastRequest.agentId()).isEqualTo("agent-1");
        assertThat(assignmentService.lastRequest.jobId()).isEqualTo("job-1");
        assertThat(assignmentService.lastRequest.priority()).isEqualTo(9);
        assertThat(assignmentService.lastRequest.input()).containsEntry("taskId", "plan-1");
        assertThat(assignmentService.lastRequest.input()).containsEntry("inputValues", Map.of("prompt", "ship it"));
    }

    @Test
    void workflowRunSubmitsHighPriorityWorkflowAssignment() {
        CapturingAssignmentService assignmentService = new CapturingAssignmentService();
        WorkflowController controller = new WorkflowController(
            new StubWorkflowService(),
            (InboxService) null,
            assignmentService,
            new StubAgentProfileService()
        );

        WorkAssignment assignment = controller.startRun("workflow-1", new WorkflowController.WorkflowRunRequest(
            null,
            null,
            "workspace-1",
            null,
            null
        ));

        assertThat(assignment.assignmentType()).isEqualTo(AssignmentType.WORKFLOW_RUN);
        assertThat(assignment.priority()).isEqualTo(9);
        assertThat(assignmentService.lastRequest.agentId()).isEqualTo("agent-1");
        assertThat(assignmentService.lastRequest.workspaceId()).isEqualTo("workspace-1");
        assertThat(assignmentService.lastRequest.input()).containsEntry("workflowId", "workflow-1");
    }

    @Test
    void planSubmitDefaultsToHighPriority() {
        CapturingAssignmentService assignmentService = new CapturingAssignmentService();
        PlanController controller = new PlanController(
            new StubPlanService(),
            null,
            assignmentService,
            new StubAgentProfileService()
        );

        WorkAssignment assignment = controller.submitToAgent("plan-1", new PlanController.SubmitRequest(
            "agent-1",
            null,
            null,
            null
        ));

        assertThat(assignment.priority()).isEqualTo(9);
        assertThat(assignmentService.lastRequest.priority()).isEqualTo(9);
    }

    @Test
    void workflowRunRejectsWhenNoAgentAvailable() {
        WorkflowController controller = new WorkflowController(
            new StubWorkflowService(),
            (InboxService) null,
            new CapturingAssignmentService(),
            new EmptyAgentProfileService()
        );

        assertThatThrownBy(() -> controller.startRun("workflow-1", null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("No active agents available");
    }

    private static PlanDefinition plan(String id) {
        return new PlanDefinition(
            id,
            PlanKind.TASK_TEMPLATE,
            PlanStatus.APPROVED,
            "Test Plan",
            null,
            "Goal",
            null,
            List.of("Deliverable"),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("Validate"),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            List.of(),
            0,
            0,
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    private static class StubPlanService extends PlanService {
        StubPlanService() {
            super(null, null);
        }

        @Override
        public PlanDefinition getTask(String id) {
            if ("plan-1".equals(id)) {
                return plan(id);
            }
            throw new IllegalStateException("Task not found: " + id);
        }
    }

    private static class StubWorkflowService extends WorkflowService {
        StubWorkflowService() {
            super(null, null, null);
        }

        @Override
        public WorkflowDefinition getDefinition(String id) {
            if ("workflow-1".equals(id)) {
                return new WorkflowDefinition(id, "Workflow", null, List.of(), Instant.now(), Instant.now());
            }
            throw new IllegalArgumentException("Workflow not found: " + id);
        }
    }

    private static class StubAgentProfileService extends AgentProfileService {
        StubAgentProfileService() {
            super(null, null, null);
        }

        @Override
        public List<AgentProfile> list() {
            return List.of(new AgentProfile(
                "agent-1",
                "Agent",
                AgentProfileStatus.ACTIVE,
                "model-a",
                null,
                List.of(),
                List.of(),
                true,
                Instant.now(),
                Instant.now()
            ));
        }
    }

    private static class EmptyAgentProfileService extends StubAgentProfileService {
        @Override
        public List<AgentProfile> list() {
            return List.of();
        }
    }

    private CapturedSse initializeEmitter(SseEmitter emitter) throws Exception {
        CapturedSse captured = new CapturedSse();
        Class<?> handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler"
        );
        Object handler = Proxy.newProxyInstance(
            handlerType.getClassLoader(),
            new Class<?>[] { handlerType },
            (proxy, method, args) -> {
                if ("send".equals(method.getName()) && args[0] instanceof Set<?> set) {
                    for (Object item : set) {
                        captured.events.add(String.valueOf(item.getClass().getMethod("getData").invoke(item)));
                    }
                } else if ("send".equals(method.getName())) {
                    captured.events.add(String.valueOf(args[0]));
                } else if ("complete".equals(method.getName()) || "completeWithError".equals(method.getName())) {
                    captured.completed.countDown();
                }
                return null;
            }
        );
        var initialize = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredMethod("initialize", handlerType);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
        return captured;
    }

    private static final class CapturedSse {
        private final List<String> events = new ArrayList<>();
        private final CountDownLatch completed = new CountDownLatch(1);
    }

    private static class CapturingAssignmentService extends AssignmentService {
        private AssignmentRequest lastRequest;

        CapturingAssignmentService() {
            super(null, null, null, null);
        }

        @Override
        public WorkAssignment create(AssignmentRequest request) {
            lastRequest = request;
            return new WorkAssignment(
                "assignment-1",
                request.agentId(),
                request.jobId(),
                request.jobItemId(),
                request.assignmentType(),
                request.priority() == null ? 0 : request.priority(),
                OrchestrationStatus.QUEUED,
                request.modelOverride(),
                request.workspaceId(),
                0,
                Map.of(),
                request.input() == null ? Map.of() : request.input(),
                Map.of(),
                Map.of(),
                null,
                null,
                null,
                Instant.now(),
                Instant.now(),
                null,
                null
            );
        }
    }
}
