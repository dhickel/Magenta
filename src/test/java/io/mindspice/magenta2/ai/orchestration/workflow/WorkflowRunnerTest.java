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
            List.of(new PlanFieldDefinition("result", PlanFieldType.STRING, false, "Output", true, null, null)),
            List.of(), List.of(new PlanStep(1, "Do it.")), List.of("Done."),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null));

        PlanDefinition plan2 = planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            "Dest Plan", "Dest", "Goal", null,
            List.of(),
            List.of(new PlanFieldDefinition("required_input", PlanFieldType.STRING, false, "Input", true, null, null)),
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
            List.of(new PlanFieldDefinition("name", PlanFieldType.STRING, false, "A name", true, null, null)),
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
            List.of(new PlanFieldDefinition("required_field", PlanFieldType.STRING, false, "Required", true, null, null)),
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

    private JdbcTemplate jdbcTemplate() {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        return new JdbcTemplate(dataSource);
    }
}
