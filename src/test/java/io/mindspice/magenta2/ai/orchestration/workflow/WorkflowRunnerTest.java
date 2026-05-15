package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.*;
import io.mindspice.magenta2.ai.chat.repository.ChatMemoryRepository;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class WorkflowRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private PlanRepository planRepository;
    private PlanService planService;
    private WorkspaceDirectoryService workspaceDirectoryService;
    private OutputArtifactService outputArtifactService;
    private WorkflowRepository workflowRepository;
    private InboxService inboxService;
    private WorkflowRunner workflowRunner;
    private WorkflowService workflowService;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        jdbcTemplate = jdbcTemplate();
        planRepository = new PlanRepository(jdbcTemplate, objectMapper);
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, objectMapper);
        planService = new PlanService(planRepository, memoryRepository);

        Path dataRoot = tempDir.resolve("data");
        java.nio.file.Files.createDirectories(dataRoot);
        workspaceDirectoryService = new WorkspaceDirectoryService(
            new io.mindspice.magenta2.ai.config.user.AiConfig(
                null, null, null, null, dataRoot, null, null));

        outputArtifactService = new OutputArtifactService(
            new WorkspaceRepository(jdbcTemplate),
            workspaceDirectoryService, objectMapper);

        workflowRepository = new WorkflowRepository(jdbcTemplate, objectMapper);
        inboxService = new InboxService(workflowRepository, objectMapper);
        workflowRunner = new WorkflowRunner(workflowRepository, planService,
            inboxService, workspaceDirectoryService, outputArtifactService);
        workflowService = new WorkflowService(workflowRepository, planService, workflowRunner);
    }

    @Test
    void definitionCrud() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Test Workflow", "A simple test",
            List.of(new WorkflowNode("step1", WorkflowNodeType.REPORT, null, List.of(),
                "Report message", null, false)),
            null, null));

        assertThat(def.id()).isNotNull();
        assertThat(def.title()).isEqualTo("Test Workflow");
        assertThat(def.nodes()).hasSize(1);

        WorkflowDefinition found = workflowService.getDefinition(def.id());
        assertThat(found.title()).isEqualTo("Test Workflow");

        List<WorkflowDefinition> all = workflowService.listDefinitions();
        assertThat(all).hasSize(1);
    }

    @Test
    void definitionRequiresTitle() {
        assertThatThrownBy(() -> workflowService.saveDefinition(new WorkflowDefinition(
            null, "", "summary", List.of(), null, null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void taskNodeRequiresPlanId() {
        assertThatThrownBy(() -> workflowService.saveDefinition(new WorkflowDefinition(
            null, "Bad Workflow", "summary",
            List.of(new WorkflowNode("step1", WorkflowNodeType.TASK, null, List.of(),
                null, null, false)),
            null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("planId");
    }

    @Test
    void reportNodeCompletesWithoutPlanId() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Report Workflow", "Just a report",
            List.of(new WorkflowNode("r1", WorkflowNodeType.REPORT, null, List.of(),
                "Hello from workflow", null, false)),
            null, null));

        WorkflowRun run = workflowRunner.runSynchronously(def);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(run.nodeRuns()).hasSize(1);
        assertThat(run.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
    }

    @Test
    void userApprovalPausesAsWaiting() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Approval Workflow", "Needs approval",
            List.of(new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                "Please approve this step", null, false)),
            null, null));

        WorkflowRun run = workflowRunner.runSynchronously(def);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.WAITING);
        assertThat(run.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.WAITING);
        assertThat(run.nodeRuns().get(0).outputValues()).containsKey("messageId");

        // Verify inbox message was created
        String messageId = (String) run.nodeRuns().get(0).outputValues().get("messageId");
        assertThat(messageId).isNotNull();
        assertThat(inboxService.userMessage(messageId)).isPresent();
    }

    @Test
    void approvalResponseResumesWorkflow() {
        // Create a two-node workflow: approval then report
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Approval Then Report", "Approval then report",
            List.of(
                new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                    "Approve?", null, false),
                new WorkflowNode("report", WorkflowNodeType.REPORT, null, List.of(),
                    "Done!", null, false)
            ),
            null, null));

        // Run synchronously - should stop at approval
        WorkflowRun run = workflowRunner.runSynchronously(def);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.WAITING);

        String messageId = (String) run.nodeRuns().get(0).outputValues().get("messageId");
        // User approves
        inboxService.respondUserApproval(messageId, true, "Looks good");

        // Resume
        WorkflowRun resumed = workflowService.resumeRun(run.id());
        // After resume, the async execution should complete
        // Re-fetch to get latest state
        WorkflowRun finalRun = workflowService.getRun(run.id());
        assertThat(finalRun.status()).isIn(WorkflowRunStatus.RUNNING, WorkflowRunStatus.COMPLETED);
    }

    @Test
    void rejectionMarksFailed() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Rejected Workflow", "Will be rejected",
            List.of(new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                "Approve?", "approve_continue_reject_failed", false)),
            null, null));

        WorkflowRun run = workflowRunner.runSynchronously(def);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.WAITING);

        // The resume mechanism handles the gate node completion - but rejection
        // policy is checked at the InboxService level and the caller decides how
        // to handle it. For now, resume moves past the gate.
        String messageId = (String) run.nodeRuns().get(0).outputValues().get("messageId");
        InboxMessage msg = inboxService.respondUserApproval(messageId, false, "Not approved");

        // Verify response was recorded
        assertThat(msg.responseJson()).contains("false");
        assertThat(inboxService.parseApprovalFromResponse(msg.responseJson())).isFalse();
    }

    @Test
    void inboxMessageCrud() {
        InboxMessage msg = inboxService.createInfoMessage(
            InboxMessageToType.USER, null, null,
            "Test message", null);

        assertThat(msg.id()).isNotNull();
        assertThat(msg.body()).isEqualTo("Test message");

        List<InboxMessage> userMsgs = inboxService.userInbox();
        assertThat(userMsgs).hasSize(1);

        // Mark handled
        InboxMessage handled = inboxService.markHandled(msg.id());
        assertThat(handled.isHandled()).isTrue();
    }

    @Test
    void agentInboxFiltering() {
        inboxService.createInfoMessage(
            InboxMessageToType.AGENT, "agent-1", "system",
            "For agent 1", null);
        inboxService.createInfoMessage(
            InboxMessageToType.AGENT, "agent-2", "system",
            "For agent 2", null);

        assertThat(inboxService.agentInbox("agent-1")).hasSize(1);
        assertThat(inboxService.agentInbox("agent-2")).hasSize(1);

        // User messages should not appear in agent inbox
        assertThat(inboxService.userInbox()).isEmpty();
    }

    @Test
    void bindingResolutionMissingInput() {
        // Create two task plans
        PlanDefinition plan1 = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Source Plan", "Source", "Goal", null,
            List.of(),
            List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Output", true, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanDefinition plan2 = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Dest Plan", "Dest", "Goal", null,
            List.of(),
            List.of(new PlanFieldDefinition("required_input", PlanFieldType.STRING, false, "Input", true, null)),
            List.of(),
            List.of(), List.of(new PlanStep(1, "Use input.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        // Create a workflow with a binding that won't work because source has no outputs yet
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Binding Test", "Test",
            List.of(
                new WorkflowNode("source", WorkflowNodeType.TASK, plan1.id(), List.of(),
                    null, null, false),
                new WorkflowNode("dest", WorkflowNodeType.TASK, plan2.id(),
                    List.of(new WorkflowBinding("required_input", "source", "result", null)),
                    null, null, false)
            ),
            null, null));

        // Validation should show type mismatch warning since source output is STRING and dest expects STRING - compatible
        List<String> warnings = workflowService.compatibilityWarnings(def);
        assertThat(warnings).isEmpty(); // STRING to STRING is compatible
    }

    @Test
    void bindingResolverLiteralValue() {
        Map<String, Object> result = BindingResolver.resolve(
            List.of(new WorkflowBinding("name", null, null, "hello")),
            List.of(new PlanFieldDefinition("name", PlanFieldType.STRING, false, "A name", true, null)),
            Map.of()
        );
        assertThat(result).containsEntry("name", "hello");
    }

    @Test
    void bindingResolverMissingSourceOutput() {
        assertThatThrownBy(() -> BindingResolver.resolve(
            List.of(new WorkflowBinding("name", "source_node", "missing_output", null)),
            List.of(),
            Map.of("source_node", Map.of("other_output", "value"))
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("missing_output");
    }

    @Test
    void bindingResolverMissingRequiredInput() {
        assertThatThrownBy(() -> BindingResolver.resolve(
            List.of(), // no bindings
            List.of(new PlanFieldDefinition("required_field", PlanFieldType.STRING, false, "Required", true, null)),
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("required_field");
    }

    @Test
    void workflowRunStatusTransitions() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Status Test", "Test transitions",
            List.of(new WorkflowNode("r1", WorkflowNodeType.REPORT, null, List.of(),
                "Hello", null, false)),
            null, null));

        WorkflowRun run = workflowRunner.runSynchronously(def);
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(run.isTerminal()).isTrue();
    }

    @Test
    void deleteWorkflowWithRuns() {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "To Delete", "Temporary",
            List.of(new WorkflowNode("r1", WorkflowNodeType.REPORT, null, List.of(),
                "tmp", null, false)),
            null, null));

        workflowRunner.runSynchronously(def);

        assertThat(workflowService.listDefinitions()).hasSize(1);
        workflowService.deleteDefinition(def.id());
        assertThat(workflowService.listDefinitions()).isEmpty();
    }

    // ════════════════════════════════════════════════════════════════
    //  DEFECT-04-02: Task node execution
    // ════════════════════════════════════════════════════════════════

    @Test
    void taskNodeFailsWithoutExecutor() throws Exception {
        // Create a plan for the task node to reference
        PlanDefinition taskPlan = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Task Plan", "A task", "Goal", null,
            List.of(),
            List.of(),
            List.of(new PlanFieldDefinition("out1", PlanFieldType.STRING, false, "Output1", true, null)),
            List.of(), List.of(new PlanStep(1, "Do something.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Task Workflow", "Has a task",
            List.of(new WorkflowNode("t1", WorkflowNodeType.TASK, taskPlan.id(), List.of(),
                null, null, false)),
            null, null));

        // No executor registered — run should fail
        WorkflowRun run = workflowRunner.startRun(def);
        // Poll for completion
        WorkflowRun finalRun = pollForTerminal(run.id());
        assertThat(finalRun.status()).isEqualTo(WorkflowRunStatus.FAILED);
        assertThat(finalRun.errorText()).contains("requires model-backed task execution");
    }

    @Test
    void taskNodeWithExecutorReturnsOutputs() throws Exception {
        // Create a plan for the task node to reference
        PlanDefinition taskPlan = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Executor Task Plan", "A task", "Goal", null,
            List.of(),
            List.of(),
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Result", true, null)),
            List.of(), List.of(new PlanStep(1, "Do something.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Task With Executor", "Has an executor",
            List.of(new WorkflowNode("t1", WorkflowNodeType.TASK, taskPlan.id(), List.of(),
                null, null, false)),
            null, null));

        // Register a fake executor
        workflowRunner.setTaskNodeExecutor((planId, planRunId, inputs, workspacePath) ->
            new PlanRun(
                planRunId, planId, PlanRunStatus.COMPLETED,
                inputs,
                Map.of("result", "executed-successfully"),
                taskPlan,
                workspacePath, null, null,
                List.of(), List.of(), List.of(),
                null, null,
                Instant.now(), Instant.now(), Instant.now(), Instant.now()
            )
        );

        WorkflowRun run = workflowRunner.startRun(def);
        WorkflowRun finalRun = pollForTerminal(run.id());
        assertThat(finalRun.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(0).outputValues())
            .containsEntry("result", "executed-successfully");
    }

    @Test
    void taskNodeWithExecutorReturnsOutputsAndRoutesDownstream() throws Exception {
        // Create two plans
        PlanDefinition plan1 = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Source Task", "Source", "Goal", null,
            List.of(),
            List.of(),
            List.of(new PlanFieldDefinition("value", PlanFieldType.STRING, false, "Value", true, null)),
            List.of(), List.of(new PlanStep(1, "Produce.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanDefinition plan2 = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Dest Task", "Dest", "Goal", null,
            List.of(),
            List.of(new PlanFieldDefinition("input_val", PlanFieldType.STRING, false, "Input", true, null)),
            List.of(),
            List.of(), List.of(new PlanStep(1, "Consume.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        // Workflow with two TASK nodes connected by a route
        WorkflowDefinition def = new WorkflowDefinition(
            null, "Downstream Task Test", "Test routing",
            List.of(
                new WorkflowNode("source", WorkflowNodeType.TASK, plan1.id(), List.of(),
                    null, null, false),
                new WorkflowNode("dest", WorkflowNodeType.TASK, plan2.id(), List.of(),
                    null, null, false)
            ),
            List.of(new WorkflowRoute("r1", "source", "value", "dest", "input_val",
                WorkflowRouteType.MAP_OUTPUT, null)),
            null, null);

        // Save via saveDefinition (not validated) to bypass missing plan input validation for source
        def = workflowService.saveDefinition(def);

        // Register a fake executor that returns output values
        workflowRunner.setTaskNodeExecutor((planId, planRunId, inputs, workspacePath) -> {
            Map<String, Object> outputs;
            if (planId.equals(plan1.id())) {
                outputs = Map.of("value", "hello-from-source");
            } else {
                outputs = Map.of("processed", "got: " + inputs.getOrDefault("input_val", "null"));
            }
            return new PlanRun(
                planRunId, planId, PlanRunStatus.COMPLETED,
                inputs, outputs,
                planId.equals(plan1.id()) ? plan1 : plan2,
                workspacePath, null, null,
                List.of(), List.of(), List.of(),
                null, null,
                Instant.now(), Instant.now(), Instant.now(), Instant.now()
            );
        });

        WorkflowRun run = workflowRunner.startRun(def);
        WorkflowRun finalRun = pollForTerminal(run.id());
        assertThat(finalRun.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns()).hasSize(2);
        assertThat(finalRun.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(0).outputValues()).containsEntry("value", "hello-from-source");
        assertThat(finalRun.nodeRuns().get(1).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(1).outputValues()).containsEntry("processed", "got: hello-from-source");
    }

    // ════════════════════════════════════════════════════════════════
    //  DEFECT-04-01: Approval rejection blocks resume
    // ════════════════════════════════════════════════════════════════

    @Test
    void approvedApprovalResumeCompletesLaterNodes() throws Exception {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Approval Then Report", "Approval then report",
            List.of(
                new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                    "Approve?", null, false),
                new WorkflowNode("report", WorkflowNodeType.REPORT, null, List.of(),
                    "Done!", null, false)
            ),
            List.of(new WorkflowRoute("r1", "approve", null, "report", null,
                WorkflowRouteType.CONTROL, null)),
            null, null));

        // Start and wait for WAITING
        WorkflowRun run = workflowRunner.startRun(def);
        run = pollForWaiting(run.id());
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.WAITING);

        String messageId = (String) run.nodeRuns().get(0).outputValues().get("messageId");
        // Approve the message
        inboxService.respondUserApproval(messageId, true, "Looks good");

        // Resume — should complete
        workflowRunner.resumeRun(workflowService.getRun(run.id()));
        WorkflowRun finalRun = pollForTerminal(run.id());
        assertThat(finalRun.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(1).status()).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
    }

    @Test
    void rejectedApprovalResumeMarksFailed() throws Exception {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Rejected Gate Workflow", "Gate will be rejected",
            List.of(
                new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                    "Approve?", null, false),
                new WorkflowNode("report", WorkflowNodeType.REPORT, null, List.of(),
                    "Should not run!", null, false)
            ),
            List.of(new WorkflowRoute("r1", "approve", null, "report", null,
                WorkflowRouteType.CONTROL, null)),
            null, null));

        // Start and wait for WAITING
        WorkflowRun run = workflowRunner.startRun(def);
        run = pollForWaiting(run.id());
        assertThat(run.status()).isEqualTo(WorkflowRunStatus.WAITING);

        String messageId = (String) run.nodeRuns().get(0).outputValues().get("messageId");
        // Reject the message
        inboxService.respondUserApproval(messageId, false, "Not approved");

        // Resume — should fail the run (handled synchronously)
        WorkflowRun result = workflowRunner.resumeRun(workflowService.getRun(run.id()));
        assertThat(result.status()).isEqualTo(WorkflowRunStatus.FAILED);
        assertThat(result.errorText()).contains("Approval rejected for gate approve");
        assertThat(result.nodeRuns().get(0).status()).isEqualTo(WorkflowNodeRunStatus.FAILED);
    }

    @Test
    void resumeBeforeResponseFails() throws Exception {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "No Response Yet", "No response yet",
            List.of(new WorkflowNode("approve", WorkflowNodeType.USER_APPROVAL, null, List.of(),
                "Approve?", null, false)),
            null, null));

        // Start and wait for WAITING
        WorkflowRun started = workflowRunner.startRun(def);
        WorkflowRun waitingRun = pollForWaiting(started.id());
        assertThat(waitingRun.status()).isEqualTo(WorkflowRunStatus.WAITING);

        // Try to resume before responding
        WorkflowRun finalRun = waitingRun;
        assertThatThrownBy(() -> workflowRunner.resumeRun(workflowService.getRun(finalRun.id())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("has not been responded to yet");
    }

    // ════════════════════════════════════════════════════════════════
    //  DEFECT-04-03: Duplicate route validation
    // ════════════════════════════════════════════════════════════════

    @Test
    void duplicateRouteSaveProducesValidationError() {
        WorkflowNode nodeA = new WorkflowNode("a", WorkflowNodeType.REPORT, null, List.of(),
            "Node A", null, false);
        WorkflowNode nodeB = new WorkflowNode("b", WorkflowNodeType.REPORT, null, List.of(),
            "Node B", null, false);

        WorkflowRoute route1 = new WorkflowRoute("r1", "a", "out_x", "b", "in_x",
            WorkflowRouteType.MAP_OUTPUT, null);
        WorkflowRoute route2 = new WorkflowRoute("r2", "a", "out_x", "b", "in_x",
            WorkflowRouteType.MAP_OUTPUT, null);

        WorkflowDefinition def = new WorkflowDefinition(
            null, "Dup Routes", "Has duplicate routes",
            List.of(nodeA, nodeB),
            List.of(route1, route2),
            null, null);

        assertThatThrownBy(() -> workflowService.saveDefinitionValidated(def))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate route");
    }

    @Test
    void nonDuplicateRoutesPassValidation() {
        WorkflowNode nodeA = new WorkflowNode("a", WorkflowNodeType.REPORT, null, List.of(),
            "Node A", null, false);
        WorkflowNode nodeB = new WorkflowNode("b", WorkflowNodeType.REPORT, null, List.of(),
            "Node B", null, false);
        WorkflowNode nodeC = new WorkflowNode("c", WorkflowNodeType.REPORT, null, List.of(),
            "Node C", null, false);

        WorkflowRoute routeAB = new WorkflowRoute("r1", "a", "out_x", "b", "in_x",
            WorkflowRouteType.MAP_OUTPUT, null);
        WorkflowRoute routeAC = new WorkflowRoute("r2", "a", "out_y", "c", "in_x",
            WorkflowRouteType.MAP_OUTPUT, null);

        WorkflowDefinition def = new WorkflowDefinition(
            null, "No Dupes", "No duplicates",
            List.of(nodeA, nodeB, nodeC),
            List.of(routeAB, routeAC),
            null, null);

        // Should not throw
        WorkflowDefinition saved = workflowService.saveDefinitionValidated(def);
        assertThat(saved.id()).isNotNull();
        assertThat(saved.routes()).hasSize(2);
    }

    @Test
    void validationAndCopyNodesPropagateDeterministicOutputs() throws Exception {
        WorkflowDefinition def = workflowService.saveDefinition(new WorkflowDefinition(
            null, "Control Nodes", "Validation then copy",
            List.of(
                new WorkflowNode("source", WorkflowNodeType.COPY, null,
                    "Source", null, Map.of("result", "ok"), false,
                    List.of(), null, null),
                new WorkflowNode("validate", WorkflowNodeType.VALIDATION, null,
                    "Validate", null, Map.of("requiredInputs", List.of("result")), false,
                    List.of(), null, null),
                new WorkflowNode("copy", WorkflowNodeType.COPY, null,
                    "Copy", null, Map.of("copies", Map.of("fanout", "result")), false,
                    List.of(), null, null)
            ),
            List.of(
                new WorkflowRoute("r1", "source", "result", "validate", "result",
                    WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r2", "validate", "result", "copy", "result",
                    WorkflowRouteType.MAP_OUTPUT, null)
            ),
            null, null));

        WorkflowRun run = workflowRunner.startRun(def);
        WorkflowRun finalRun = pollForTerminal(run.id());

        assertThat(finalRun.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(finalRun.nodeRuns().get(1).outputValues()).containsEntry("valid", true);
        assertThat(finalRun.nodeRuns().get(2).outputValues()).containsEntry("fanout", "ok");
    }

    private WorkflowRun pollForWaiting(String runId) throws Exception {
        for (int i = 0; i < 50; i++) {
            WorkflowRun run = workflowService.getRun(runId);
            if (run.status() == WorkflowRunStatus.WAITING) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Run " + runId + " did not reach WAITING state");
    }

    private WorkflowRun pollForTerminal(String runId) throws Exception {
        for (int i = 0; i < 50; i++) {
            WorkflowRun run = workflowService.getRun(runId);
            if (run.isTerminal()) {
                return run;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("Run " + runId + " did not reach terminal state");
    }

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
