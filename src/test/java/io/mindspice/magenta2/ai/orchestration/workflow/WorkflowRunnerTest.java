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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowRunnerTest {

    private JdbcTemplate jdbcTemplate;
    private PlanService planService;
    private WorkflowRunner workflowRunner;
    private WorkflowService workflowService;
    private InboxService inboxService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        jdbcTemplate = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));

        PlanRepository planRepository = new PlanRepository(jdbcTemplate, mapper);
        ChatMemoryRepository memoryRepository = new ChatMemoryRepository(jdbcTemplate, mapper);
        planService = new PlanService(planRepository, memoryRepository);

        Path dataRoot = tempDir.resolve("data");
        java.nio.file.Files.createDirectories(dataRoot);
        WorkspaceDirectoryService workspaceDirectoryService = new WorkspaceDirectoryService(
            new io.mindspice.magenta2.ai.config.user.AiConfig(
                null, null, null, null, dataRoot, null, null));

        OutputArtifactService outputArtifactService = new OutputArtifactService(
            new WorkspaceRepository(jdbcTemplate), workspaceDirectoryService, mapper);

        WorkflowRepository workflowRepository = new WorkflowRepository(jdbcTemplate, mapper);
        inboxService = new InboxService(workflowRepository, mapper);
        workflowRunner = new WorkflowRunner(workflowRepository, planService,
            inboxService, workspaceDirectoryService, outputArtifactService);
        workflowService = new WorkflowService(workflowRepository, planService, workflowRunner);
    }

    @Test
    void savesAndLoadsWorkflowV2Definition() {
        WorkflowDefinition saved = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null,
            2,
            "Workflow V2",
            "test",
            4,
            List.of(simpleFinalNode("final")),
            List.of(),
            Map.of("nodes", Map.of("final", Map.of("x", 40, "y", 40))),
            null,
            null
        ));

        WorkflowDefinition found = workflowService.getDefinition(saved.id());
        assertThat(found.schemaVersion()).isEqualTo(2);
        assertThat(found.maxConcurrency()).isEqualTo(4);
        assertThat(found.uiLayout()).containsKey("nodes");
    }

    @Test
    void rejectsLegacyInputBindingsInV2() {
        WorkflowNode legacy = new WorkflowNode(
            "legacy", WorkflowNodeType.TASK, "missing", "legacy", null,
            List.of(), List.of(), Map.of(), false,
            List.of(new WorkflowBinding("in", "source", "out", null)),
            null, null
        );

        assertThatThrownBy(() -> workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null, 2, "Bad", "", 4,
            List.of(legacy), List.of(), Map.of(), null, null
        ))).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("legacy inputBindings");
    }

    @Test
    void gateRequiresApproveAndRejectBranches() {
        WorkflowNode gate = new WorkflowNode("gate", WorkflowNodeType.USER_APPROVAL, null,
            "gate", null, List.of(), List.of(), Map.of(), false, List.of(), "approve?", null);
        WorkflowNode done = simpleFinalNode("done");

        WorkflowDefinition def = new WorkflowDefinition(
            null, 2, "Gate", "", 4,
            List.of(gate, done),
            List.of(new WorkflowRoute("r1", "gate", null, "done", null, WorkflowRouteType.CONTROL, "APPROVED")),
            Map.of(), null, null
        );

        assertThatThrownBy(() -> workflowService.saveDefinitionValidated(def))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing REJECTED control route");
    }

    @Test
    void draftSaveAllowsIncrementalApprovalWorkflowBeforeExecutableValidation() {
        WorkflowNode gate = new WorkflowNode("gate", WorkflowNodeType.USER_APPROVAL, null,
            "gate", null, List.of(), List.of(), Map.of(), false, List.of(), "approve?", null);
        WorkflowNode approved = simpleFinalNode("approved");
        WorkflowNode rejected = simpleFinalNode("rejected");

        WorkflowDefinition gateOnly = workflowService.saveDefinition(new WorkflowDefinition(
            null, 2, "Incremental Gate", "", 4,
            List.of(gate), List.of(), Map.of(), null, null
        ));
        WorkflowValidator.ValidationResult gateOnlyValidation = workflowService.validateGraph(gateOnly);
        assertThat(gateOnlyValidation.valid()).isFalse();
        assertThat(gateOnlyValidation.errors()).contains(
            "Gate node 'gate' is missing APPROVED control route",
            "Gate node 'gate' is missing REJECTED control route"
        );

        WorkflowDefinition oneBranch = workflowService.saveDefinition(new WorkflowDefinition(
            gateOnly.id(), 2, "Incremental Gate", "", 4,
            List.of(gate, approved, rejected),
            List.of(new WorkflowRoute("approved-route", "gate", null, "approved", null,
                WorkflowRouteType.CONTROL, WorkflowRoute.OUTCOME_APPROVED)),
            Map.of(), null, null
        ));
        WorkflowValidator.ValidationResult oneBranchValidation = workflowService.validateGraph(oneBranch);
        assertThat(oneBranchValidation.valid()).isFalse();
        assertThat(oneBranchValidation.errors()).contains("Gate node 'gate' is missing REJECTED control route");

        WorkflowDefinition complete = workflowService.saveDefinition(new WorkflowDefinition(
            gateOnly.id(), 2, "Incremental Gate", "", 4,
            List.of(gate, approved, rejected),
            List.of(
                new WorkflowRoute("approved-route", "gate", null, "approved", null,
                    WorkflowRouteType.CONTROL, WorkflowRoute.OUTCOME_APPROVED),
                new WorkflowRoute("rejected-route", "gate", null, "rejected", null,
                    WorkflowRouteType.CONTROL, WorkflowRoute.OUTCOME_REJECTED)
            ),
            Map.of(), null, null
        ));

        assertThat(workflowService.validateGraph(complete).valid()).isTrue();
        assertThat(workflowService.saveDefinitionValidated(complete).id()).isEqualTo(gateOnly.id());
    }

    @Test
    void draftSaveAllowsTaskBeforeRequiredRuntimeInputsAreConnected() {
        PlanDefinition source = task("source", List.of(), List.of(field("topic", PlanFieldType.STRING, true)));
        PlanDefinition worker = task("worker", List.of(field("topic", PlanFieldType.STRING, true)), List.of());

        WorkflowNode sourceNode = taskNode("source", source.id());
        WorkflowNode workerNode = taskNode("worker", worker.id());

        WorkflowDefinition incomplete = workflowService.saveDefinition(new WorkflowDefinition(
            null, 2, "Incremental Task Inputs", "", 4,
            List.of(sourceNode, workerNode), List.of(), Map.of(), null, null
        ));
        WorkflowValidator.ValidationResult incompleteValidation = workflowService.validateGraph(incomplete);
        assertThat(incompleteValidation.valid()).isFalse();
        assertThat(incompleteValidation.errors())
            .contains("TASK node 'worker': required input 'topic' is not satisfied by incoming routes or config");

        WorkflowDefinition complete = workflowService.saveDefinition(new WorkflowDefinition(
            incomplete.id(), 2, "Incremental Task Inputs", "", 4,
            List.of(sourceNode, workerNode),
            List.of(new WorkflowRoute("topic-route", "source", "topic", "worker", "topic",
                WorkflowRouteType.MAP_OUTPUT, null)),
            Map.of(), null, null
        ));

        assertThat(workflowService.validateGraph(complete).valid()).isTrue();
    }

    @Test
    void runsParallelFanOutAndProducesFinalOutputsAndArtifacts() throws Exception {
        PlanDefinition source = task("source", List.of(), List.of(field("result", PlanFieldType.STRING, true)));
        PlanDefinition worker = task("worker", List.of(field("in", PlanFieldType.STRING, true)),
            List.of(field("value", PlanFieldType.STRING, true)));

        WorkflowNode sourceNode = taskNode("source", source.id());
        WorkflowNode a = taskNode("a", worker.id());
        WorkflowNode b = taskNode("b", worker.id());
        WorkflowNode c = taskNode("c", worker.id());
        WorkflowNode d = taskNode("d", worker.id());
        WorkflowNode finalNode = new WorkflowNode(
            "final",
            WorkflowNodeType.FINAL_OUTPUT,
            null,
            "Final",
            null,
            List.of(
                new WorkflowPort("a_out", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("b_out", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("c_out", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("d_out", PlanFieldType.STRING, false, false, null)
            ),
            List.of(
                new WorkflowPort("a", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("b", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("c", PlanFieldType.STRING, false, false, null),
                new WorkflowPort("d", PlanFieldType.STRING, false, false, null)
            ),
            Map.of("finalOutputs", Map.of(
                "a", "a_out",
                "b", "b_out",
                "c", "c_out",
                "d", "d_out"
            )),
            false,
            List.of(),
            null,
            null
        );

        WorkflowDefinition def = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null, 2, "Parallel Fanout", "", 4,
            List.of(sourceNode, a, b, c, d, finalNode),
            List.of(
                new WorkflowRoute("r1", "source", "result", "a", "in", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r2", "source", "result", "b", "in", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r3", "source", "result", "c", "in", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r4", "source", "result", "d", "in", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r5", "a", "value", "final", "a_out", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r6", "b", "value", "final", "b_out", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r7", "c", "value", "final", "c_out", WorkflowRouteType.MAP_OUTPUT, null),
                new WorkflowRoute("r8", "d", "value", "final", "d_out", WorkflowRouteType.MAP_OUTPUT, null)
            ),
            Map.of(),
            null,
            null
        ));

        AtomicInteger counter = new AtomicInteger();
        workflowRunner.setTaskNodeExecutor((planId, planRunId, inputs, workspacePath) -> {
            try { Thread.sleep(200); } catch (InterruptedException ignored) { }
            if (planId.equals(source.id())) {
                return completedPlanRun(planRunId, planId, Map.of(), Map.of("result", "seed"));
            }
            int idx = counter.incrementAndGet();
            return completedPlanRun(planRunId, planId, inputs, Map.of("value", "worker-" + idx));
        });

        Instant started = Instant.now();
        WorkflowRun run = workflowService.startRun(def.id());
        WorkflowRun finished = pollForTerminal(run.id());
        long elapsedMs = Duration.between(started, Instant.now()).toMillis();

        assertThat(finished.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(elapsedMs).isLessThan(900); // source + parallel workers + final
        assertThat(finished.finalOutputs()).containsKeys("a", "b", "c", "d");
        assertThat(finished.artifactIds()).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void resumeFollowsApprovedBranchAndSkipsRejectedBranch() throws Exception {
        PlanDefinition branchTask = task("branch", List.of(), List.of(field("status", PlanFieldType.STRING, true)));

        WorkflowNode gate = new WorkflowNode("gate", WorkflowNodeType.USER_APPROVAL, null,
            "gate", null, List.of(), List.of(), Map.of(), false, List.of(), "approve?", null);
        WorkflowNode approved = taskNode("approved", branchTask.id());
        WorkflowNode rejected = taskNode("rejected", branchTask.id());

        WorkflowDefinition def = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null, 2, "Approval Branch", "", 2,
            List.of(gate, approved, rejected),
            List.of(
                new WorkflowRoute("c1", "gate", null, "approved", null, WorkflowRouteType.CONTROL, "APPROVED"),
                new WorkflowRoute("c2", "gate", null, "rejected", null, WorkflowRouteType.CONTROL, "REJECTED")
            ),
            Map.of(), null, null
        ));

        workflowRunner.setTaskNodeExecutor((planId, planRunId, inputs, workspacePath) ->
            completedPlanRun(planRunId, planId, inputs, Map.of("status", "ok")));

        WorkflowRun run = workflowService.startRun(def.id());
        WorkflowRun waiting = pollForWaiting(run.id());
        String messageId = String.valueOf(waiting.nodeRuns().stream()
            .filter(n -> n.nodeKey().equals("gate"))
            .findFirst().orElseThrow().outputValues().get("messageId"));

        inboxService.respondUserApproval(messageId, true, "yes");
        workflowService.resumeRun(waiting.id());

        WorkflowRun finished = pollForTerminal(waiting.id());
        assertThat(finished.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(nodeStatus(finished, "approved")).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
        assertThat(nodeStatus(finished, "rejected")).isEqualTo(WorkflowNodeRunStatus.SKIPPED);
    }

    @Test
    void resumeFollowsRejectedBranchAndSkipsApprovedBranch() throws Exception {
        PlanDefinition branchTask = task("branch", List.of(), List.of(field("status", PlanFieldType.STRING, true)));

        WorkflowNode gate = new WorkflowNode("gate", WorkflowNodeType.USER_APPROVAL, null,
            "gate", null, List.of(), List.of(), Map.of(), false, List.of(), "approve?", null);
        WorkflowNode approved = taskNode("approved", branchTask.id());
        WorkflowNode rejected = taskNode("rejected", branchTask.id());

        WorkflowDefinition def = workflowService.saveDefinitionValidated(new WorkflowDefinition(
            null, 2, "Reject Branch", "", 2,
            List.of(gate, approved, rejected),
            List.of(
                new WorkflowRoute("c1", "gate", null, "approved", null, WorkflowRouteType.CONTROL, "APPROVED"),
                new WorkflowRoute("c2", "gate", null, "rejected", null, WorkflowRouteType.CONTROL, "REJECTED")
            ),
            Map.of(), null, null
        ));

        workflowRunner.setTaskNodeExecutor((planId, planRunId, inputs, workspacePath) ->
            completedPlanRun(planRunId, planId, inputs, Map.of("status", "rejected-path")));

        WorkflowRun run = workflowService.startRun(def.id());
        WorkflowRun waiting = pollForWaiting(run.id());
        String messageId = String.valueOf(waiting.nodeRuns().stream()
            .filter(n -> n.nodeKey().equals("gate"))
            .findFirst().orElseThrow().outputValues().get("messageId"));

        inboxService.respondUserApproval(messageId, false, "no");
        workflowService.resumeRun(waiting.id());

        WorkflowRun finished = pollForTerminal(waiting.id());
        assertThat(finished.status()).isEqualTo(WorkflowRunStatus.COMPLETED);
        assertThat(nodeStatus(finished, "approved")).isEqualTo(WorkflowNodeRunStatus.SKIPPED);
        assertThat(nodeStatus(finished, "rejected")).isEqualTo(WorkflowNodeRunStatus.COMPLETED);
    }

    private WorkflowNode simpleFinalNode(String key) {
        return new WorkflowNode(key, WorkflowNodeType.FINAL_OUTPUT, null,
            key, null, List.of(), List.of(), Map.of(), false, List.of(), null, null);
    }

    private WorkflowNode taskNode(String key, String planId) {
        return new WorkflowNode(
            key,
            WorkflowNodeType.TASK,
            planId,
            key,
            null,
            List.of(),
            List.of(),
            Map.of(),
            false,
            List.of(),
            null,
            null
        );
    }

    private PlanFieldDefinition field(String name, PlanFieldType type, boolean required) {
        return new PlanFieldDefinition(name, type, false, name, required, null);
    }

    private PlanDefinition task(String title, List<PlanFieldDefinition> inputs, List<PlanFieldDefinition> outputs) {
        return planService.saveTask(new PlanDefinition(
            null, PlanKind.TASK_TEMPLATE, PlanStatus.APPROVED,
            title, title, "goal", null,
            List.of(), inputs, outputs,
            List.of(), List.of(new PlanStep(1, "step")), List.of("criterion"),
            List.of(), List.of(), null, null, null, null,
            null, List.of(), 0, 0, null, null, null, null
        ));
    }

    private PlanRun completedPlanRun(String runId, String planId, Map<String, Object> inputs, Map<String, Object> outputs) {
        return new PlanRun(
            runId,
            planId,
            PlanRunStatus.COMPLETED,
            inputs,
            outputs,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }

    private WorkflowRun pollForWaiting(String runId) throws Exception {
        for (int i = 0; i < 80; i++) {
            WorkflowRun run = workflowService.getRun(runId);
            if (run.status() == WorkflowRunStatus.WAITING) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Run did not reach WAITING: " + runId);
    }

    private WorkflowRun pollForTerminal(String runId) throws Exception {
        for (int i = 0; i < 120; i++) {
            WorkflowRun run = workflowService.getRun(runId);
            if (run.isTerminal()) {
                return run;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Run did not complete: " + runId);
    }

    private WorkflowNodeRunStatus nodeStatus(WorkflowRun run, String nodeKey) {
        return run.nodeRuns().stream()
            .filter(n -> n.nodeKey().equals(nodeKey))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Missing node: " + nodeKey))
            .status();
    }
}
