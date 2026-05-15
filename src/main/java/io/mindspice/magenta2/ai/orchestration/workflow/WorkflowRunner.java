package io.mindspice.magenta2.ai.orchestration.workflow;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanRun;
import io.mindspice.magenta2.ai.chat.plan.PlanRunStatus;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Executes workflow nodes through graph-traversal dependency ordering.
 * Nodes become ready when all their incoming dependency-creating routes
 * have been satisfied.
 *
 * <p>Allocates a shared temp workspace at run start that survives across
 * nodes and is deleted at terminal completion.
 *
 * <p>Thread safety: each run is executed on its own path; the runner
 * uses a single-threaded executor for async execution.
 */
@Service
public class WorkflowRunner {
    private static final Logger log = LoggerFactory.getLogger(WorkflowRunner.class);

    private final WorkflowRepository repository;
    private final PlanService planService;
    private final InboxService inboxService;
    private final WorkspaceDirectoryService workspaceDirectoryService;
    private final OutputArtifactService outputArtifactService;
    private final WorkflowTaskExecutor workflowTaskExecutor;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Callback for TASK node execution via the model. */
    private volatile TaskNodeExecutor taskNodeExecutor;

    public WorkflowRunner(WorkflowRepository repository, PlanService planService,
                          InboxService inboxService,
                          WorkspaceDirectoryService workspaceDirectoryService,
                          OutputArtifactService outputArtifactService) {
        this(repository, planService, inboxService, workspaceDirectoryService, outputArtifactService, null);
    }

    @Autowired
    public WorkflowRunner(WorkflowRepository repository, PlanService planService,
                          InboxService inboxService,
                          WorkspaceDirectoryService workspaceDirectoryService,
                          OutputArtifactService outputArtifactService,
                          ObjectProvider<WorkflowTaskExecutor> workflowTaskExecutorProvider) {
        this.repository = repository;
        this.planService = planService;
        this.inboxService = inboxService;
        this.workspaceDirectoryService = workspaceDirectoryService;
        this.outputArtifactService = outputArtifactService;
        this.workflowTaskExecutor = workflowTaskExecutorProvider == null ? null : workflowTaskExecutorProvider.getIfAvailable();
    }

    /**
     * Register a callback for executing TASK nodes through the chat model.
     * Called by ChatService during wiring to enable model-backed task execution.
     */
    public void setTaskNodeExecutor(TaskNodeExecutor executor) {
        this.taskNodeExecutor = executor;
    }

    /**
     * Functional interface for executing a TASK node through the model.
     */
    @FunctionalInterface
    public interface TaskNodeExecutor {
        PlanRun execute(String planId, String planRunId, Map<String, Object> inputs, String workspacePath);
    }

    // ════════════════════════════════════════════════════════════════
    //  Start / Resume
    // ════════════════════════════════════════════════════════════════

    /**
     * Start a new workflow run. Allocates workspace and initializes node runs.
     * Returns the run immediately; execution proceeds asynchronously.
     */
    public WorkflowRun startRun(WorkflowDefinition definition) {
        return startRun(definition, null);
    }

    public WorkflowRun startRun(WorkflowDefinition definition, String modelOverride) {
        String runId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Path workspacePath = workspaceDirectoryService.workflowTemp(runId);

        List<WorkflowNodeRun> nodeRuns = new ArrayList<>();
        for (WorkflowNode node : definition.nodes()) {
            nodeRuns.add(new WorkflowNodeRun(
                node.key(), node.type(), WorkflowNodeRunStatus.PENDING,
                Map.of(), Map.of(), null, null
            ));
        }

        WorkflowRun run = new WorkflowRun(
            runId, definition.id(), WorkflowRunStatus.RUNNING, 0,
            nodeRuns, workspacePath.toString(), null,
            definition, null, null,
            now, now, now, null
        );

        run = repository.saveRun(run);
        log.info("Workflow run {} started for definition '{}'", runId, definition.title());

        WorkflowRun finalRun = run;
        executor.submit(() -> {
            try {
                executeFromCheckpoint(finalRun, null, modelOverride);
            } catch (Exception e) {
                log.error("Workflow run {} failed", runId, e);
                WorkflowRun current = repository.findRun(runId).orElse(finalRun);
                repository.saveRun(new WorkflowRun(
                    current.id(), current.workflowId(), WorkflowRunStatus.FAILED,
                    current.currentNodeIndex(), current.nodeRuns(),
                    current.workspacePath(), current.outputDir(),
                    current.workflowSnapshot(), current.finalMessage(),
                    e.getMessage(), current.createdAt(), Instant.now(),
                    current.startedAt(), Instant.now()
                ));
                cleanupWorkspace(current);
            }
        });

        return run;
    }

    /**
     * Resume a waiting workflow run after an approval response.
     * Checks the approval message response before allowing the gate to proceed.
     * If rejected, marks the gate FAILED and the workflow run FAILED.
     * If no response yet, refuses to resume.
     */
    public WorkflowRun resumeRun(WorkflowRun run) {
        log.info("Resuming workflow run {} from waiting state", run.id());
        WorkflowNodeRun waitingNode = findWaitingNode(run);
        if (waitingNode == null) {
            throw new IllegalStateException("No waiting node found in run: " + run.id());
        }

        int nodeIndex = nodeIndex(run, waitingNode.nodeKey());
        String nodeKey = waitingNode.nodeKey();

        // Retrieve the approval message
        Object messageIdObj = waitingNode.outputValues().get("messageId");
        if (!(messageIdObj instanceof String messageId) || messageId.isBlank()) {
            throw new IllegalStateException("Waiting node '" + nodeKey
                + "' has no messageId in output values");
        }

        InboxMessage message = inboxService.findMessageById(messageId)
            .orElseThrow(() -> new IllegalStateException(
                "Approval message not found: " + messageId));

        // If no response yet, refuse to resume
        if (!StringUtils.hasText(message.responseJson())) {
            throw new IllegalStateException(
                "Cannot resume workflow run " + run.id()
                    + ": approval message " + messageId + " has not been responded to yet");
        }

        boolean approved = inboxService.parseApprovalFromResponse(message.responseJson());

        if (approved) {
            // Mark gate as COMPLETED and continue execution
            List<WorkflowNodeRun> updatedRuns = new ArrayList<>(run.nodeRuns());
            updatedRuns.set(nodeIndex, new WorkflowNodeRun(
                waitingNode.nodeKey(), waitingNode.type(), WorkflowNodeRunStatus.COMPLETED,
                waitingNode.inputValues(), waitingNode.outputValues(),
                waitingNode.startedAt(), Instant.now()
            ));

            WorkflowRun resumed = new WorkflowRun(
                run.id(), run.workflowId(), WorkflowRunStatus.RUNNING,
                nodeIndex + 1, updatedRuns,
                run.workspacePath(), run.outputDir(), run.workflowSnapshot(),
                run.finalMessage(), run.errorText(),
                run.createdAt(), Instant.now(), run.startedAt(), null
            );
            resumed = repository.saveRun(resumed);

            // Mark the approval message handled after successful approval
            inboxService.markHandled(messageId);

            WorkflowRun finalRun = resumed;
            executor.submit(() -> {
                try {
                    executeFromCheckpoint(finalRun, null, null);
                } catch (Exception e) {
                    log.error("Workflow run {} failed after resume", finalRun.id(), e);
                    WorkflowRun current2 = repository.findRun(finalRun.id()).orElse(finalRun);
                    repository.saveRun(new WorkflowRun(
                        current2.id(), current2.workflowId(), WorkflowRunStatus.FAILED,
                        current2.currentNodeIndex(), current2.nodeRuns(),
                        current2.workspacePath(), current2.outputDir(),
                        current2.workflowSnapshot(), current2.finalMessage(),
                        e.getMessage(), current2.createdAt(), Instant.now(),
                        current2.startedAt(), Instant.now()
                    ));
                    cleanupWorkspace(current2);
                }
            });

            return resumed;
        } else {
            // Rejected — mark gate FAILED and workflow run FAILED
            String errorMsg = "Approval rejected for gate " + nodeKey;
            List<WorkflowNodeRun> updatedRuns = new ArrayList<>(run.nodeRuns());
            updatedRuns.set(nodeIndex, new WorkflowNodeRun(
                waitingNode.nodeKey(), waitingNode.type(), WorkflowNodeRunStatus.FAILED,
                waitingNode.inputValues(), waitingNode.outputValues(),
                waitingNode.startedAt(), Instant.now()
            ));

            WorkflowRun failed = new WorkflowRun(
                run.id(), run.workflowId(), WorkflowRunStatus.FAILED,
                nodeIndex, updatedRuns,
                run.workspacePath(), run.outputDir(), run.workflowSnapshot(),
                null, errorMsg,
                run.createdAt(), Instant.now(), run.startedAt(), Instant.now()
            );
            failed = repository.saveRun(failed);

            // Mark the rejection message handled now that terminal state is persisted
            inboxService.markHandled(messageId);

            log.info("Workflow run {} failed: {}", run.id(), errorMsg);
            cleanupWorkspace(failed);
            return failed;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Synchronous execution (for tests and simple integrations)
    // ════════════════════════════════════════════════════════════════

    public WorkflowRun runSynchronously(WorkflowDefinition definition) {
        return runSynchronously(definition, null);
    }

    public WorkflowRun runSynchronously(WorkflowDefinition definition, String modelOverride) {
        WorkflowRun run = startRun(definition, modelOverride);
        run = repository.findRun(run.id()).orElse(run);
        executeFromCheckpoint(run, null, modelOverride);
        return repository.findRun(run.id()).orElse(run);
    }

    // ════════════════════════════════════════════════════════════════
    //  Graph-traversal execution loop
    // ════════════════════════════════════════════════════════════════

    private void executeFromCheckpoint(WorkflowRun run, Consumer<String> sseEventCallback, String modelOverride) {
        WorkflowDefinition def = run.workflowSnapshot();
        Map<String, Map<String, Object>> outputsByNode = new LinkedHashMap<>();
        Set<String> completedNodes = new HashSet<>();

        // Collect outputs from already-completed nodes
        for (WorkflowNodeRun nr : run.nodeRuns()) {
            if (nr.status() == WorkflowNodeRunStatus.COMPLETED) {
                completedNodes.add(nr.nodeKey());
                if (!nr.outputValues().isEmpty()) {
                    outputsByNode.put(nr.nodeKey(), nr.outputValues());
                }
            }
        }

        // Compute initial ready nodes
        Set<String> readyNodeKeys = computeReadyNodes(def, completedNodes);

        while (!readyNodeKeys.isEmpty()) {
            // Sequential MVP: pick one ready node at a time
            // For parallel nodes, we could dispatch all at once (deferred)
            String nodeKey = readyNodeKeys.iterator().next();
            WorkflowNode node = def.nodeByKey(nodeKey);
            if (node == null) continue;

            int nodeIndex = nodeIndex(run, nodeKey);
            WorkflowNodeRun nodeRun = nodeIndex < run.nodeRuns().size() ? run.nodeRuns().get(nodeIndex) : null;
            if (nodeRun == null) continue;

            // Skip already-completed
            if (nodeRun.status() == WorkflowNodeRunStatus.COMPLETED) {
                completedNodes.add(nodeKey);
                readyNodeKeys.remove(nodeKey);
                readyNodeKeys.addAll(computeReadyNodes(def, completedNodes));
                continue;
            }

            log.info("Workflow run {} executing node {} (type={})",
                run.id(), node.key(), node.type().wireName());

            // Update node to RUNNING
            List<WorkflowNodeRun> updatedRuns = new ArrayList<>(run.nodeRuns());
            updatedRuns.set(nodeIndex, new WorkflowNodeRun(
                nodeRun.nodeKey(), nodeRun.type(), WorkflowNodeRunStatus.RUNNING,
                nodeRun.inputValues(), nodeRun.outputValues(),
                Instant.now(), null
            ));
            run = repository.saveRun(new WorkflowRun(
                run.id(), run.workflowId(), WorkflowRunStatus.RUNNING, nodeIndex,
                updatedRuns, run.workspacePath(), run.outputDir(),
                run.workflowSnapshot(), run.finalMessage(), run.errorText(),
                run.createdAt(), Instant.now(), run.startedAt(), null
            ));

            emitSse(sseEventCallback, "progress",
                Map.of("workflowRunId", run.id(), "nodeIndex", nodeIndex,
                       "nodeKey", node.key(), "nodeType", node.type().wireName()));

            try {
                switch (node.type()) {
                    case TASK -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeTaskNode(node, inputs, run, modelOverride);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED,
                            inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());

                        // Process LOG routes for this node
                        processLogRoutes(def, node.key(), outputs, run);

                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                    case REPORT -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeReportNode(node, inputs, run);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED,
                            inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                    case USER_APPROVAL, AGENT_APPROVAL -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        String messageId = executeGateNode(node, run);

                        Map<String, Object> gateOutputs = Map.of("messageId", messageId);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.WAITING, inputs, gateOutputs);
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.WAITING,
                            inputs, gateOutputs);

                        emitSse(sseEventCallback, "waiting",
                            Map.of("workflowRunId", run.id(), "nodeIndex", nodeIndex,
                                   "nodeType", node.type().wireName(), "waitingMessageId", messageId));

                        // Stop execution; waiting for resume
                        return;
                    }
                    case USER_MESSAGE, AGENT_MESSAGE -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        executeMessageNode(node, run);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, Map.of());
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, Map.of());
                    }
                    case DELEGATION -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeDelegationNode(node, inputs, run);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                    case VALIDATION -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeValidationNode(node, inputs);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                    case COPY -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeCopyNode(node, inputs);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                    case LOG -> {
                        Map<String, Object> inputs = resolveNodeInputs(node, def, outputsByNode);
                        Map<String, Object> outputs = executeLogNode(node, inputs, run);
                        updateNodeRun(run, nodeKey, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                        outputsByNode.put(node.key(), outputs);
                        completedNodes.add(node.key());
                        run = saveRunWithNodeStatus(run, nodeIndex, WorkflowNodeRunStatus.COMPLETED, inputs, outputs);
                    }
                }
            } catch (Exception e) {
                log.error("Workflow run {} failed at node {}: {}",
                    run.id(), node.key(), e.getMessage(), e);

                updatedRuns = new ArrayList<>(run.nodeRuns());
                if (nodeIndex < updatedRuns.size()) {
                    updatedRuns.set(nodeIndex, new WorkflowNodeRun(
                        nodeRun.nodeKey(), nodeRun.type(), WorkflowNodeRunStatus.FAILED,
                        resolveNodeInputs(node, def, outputsByNode), Map.of(),
                        nodeRun.startedAt(), Instant.now()
                    ));
                }
                run = repository.saveRun(new WorkflowRun(
                    run.id(), run.workflowId(), WorkflowRunStatus.FAILED, nodeIndex,
                    updatedRuns, run.workspacePath(), run.outputDir(),
                    run.workflowSnapshot(), run.finalMessage(), e.getMessage(),
                    run.createdAt(), Instant.now(), run.startedAt(), Instant.now()
                ));

                emitSse(sseEventCallback, "failed",
                    Map.of("workflowRunId", run.id(), "nodeIndex", nodeIndex, "error", e.getMessage()));

                cleanupWorkspace(run);
                return;
            }

            // Compute next ready nodes
            readyNodeKeys = computeReadyNodes(def, completedNodes);
        }

        // Check if all nodes completed (no remaining executable nodes)
        boolean allDone = def.nodes().stream()
            .allMatch(n -> completedNodes.contains(n.key()));

        if (allDone) {
            run = repository.saveRun(new WorkflowRun(
                run.id(), run.workflowId(), WorkflowRunStatus.COMPLETED,
                run.currentNodeIndex(), run.nodeRuns(),
                run.workspacePath(), run.outputDir(), run.workflowSnapshot(),
                "Workflow completed: " + run.workflowSnapshot().title(),
                null,
                run.createdAt(), Instant.now(), run.startedAt(), Instant.now()
            ));

            emitSse(sseEventCallback, "completed",
                Map.of("workflowRunId", run.id(), "title", run.workflowSnapshot().title()));

            cleanupWorkspace(run);
            log.info("Workflow run {} completed successfully", run.id());
        }
    }

    /**
     * Compute ready nodes: nodes where all incoming dependency-creating
     * route sources have completed.
     */
    private Set<String> computeReadyNodes(WorkflowDefinition def, Set<String> completed) {
        Set<String> ready = new HashSet<>();
        for (WorkflowNode node : def.nodes()) {
            if (completed.contains(node.key())) continue;

            List<WorkflowRoute> incoming = def.incomingRoutes(node.key());
            // Filter to only dependency-creating routes
            List<String> requiredSources = incoming.stream()
                .filter(WorkflowRoute::createsDependency)
                .map(WorkflowRoute::fromNodeKey)
                .filter(k -> k != null)
                .distinct()
                .toList();

            if (requiredSources.isEmpty()) {
                // Root node: no dependencies
                ready.add(node.key());
            } else if (completed.containsAll(requiredSources)) {
                ready.add(node.key());
            }
        }
        return ready;
    }

    /**
     * Process LOG routes: materialize outputs but don't create downstream dependencies.
     */
    private void processLogRoutes(WorkflowDefinition def, String nodeKey,
                                   Map<String, Object> outputs, WorkflowRun run) {
        for (WorkflowRoute route : def.outgoingRoutes(nodeKey)) {
            if (route.routeType() == WorkflowRouteType.LOG) {
                try {
                    Path outputDir = workspaceDirectoryService.workflowTemp(run.id());
                    Object value = route.fromOutputName() != null
                        ? outputs.get(route.fromOutputName())
                        : outputs;
                    if (value != null) {
                        outputArtifactService.materialize(
                            run.id(), "log-" + route.id(), route.fromOutputName(),
                            io.mindspice.magenta2.ai.chat.plan.PlanFieldType.STRING,
                            value, outputDir
                        );
                    }
                } catch (Exception e) {
                    log.warn("LOG route {} failed to materialize: {}", route.id(), e.getMessage());
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Node type executors
    // ════════════════════════════════════════════════════════════════

    private Map<String, Object> executeTaskNode(
        WorkflowNode node, Map<String, Object> inputs, WorkflowRun run, String modelOverride
    ) {
        PlanDefinition plan = planService.getTask(node.planId());

        if (workflowTaskExecutor != null) {
            var taskRun = workflowTaskExecutor.execute(
                node.planId(), inputs, UUID.randomUUID().toString(), modelOverride);
            if (!workflowTaskExecutor.succeeded(taskRun.status())) {
                throw new RuntimeException("Task node '" + node.key()
                    + "' failed with status " + taskRun.status().name()
                    + (taskRun.errorText() != null ? ": " + taskRun.errorText() : ""));
            }
            return taskRun.outputValues();
        }

        if (taskNodeExecutor == null) {
            throw new RuntimeException(
                "Task node execution requires model-backed task execution");
        }

        String planRunId = UUID.randomUUID().toString();
        PlanRun planRun = taskNodeExecutor.execute(
            node.planId(), planRunId, inputs, run.workspacePath());

        if (planRun.status() == PlanRunStatus.FAILED
            || planRun.status() == PlanRunStatus.NEEDS_REVIEW) {
            throw new RuntimeException("Task node '" + node.key()
                + "' failed with status " + planRun.status().name()
                + (planRun.errorText() != null ? ": " + planRun.errorText() : ""));
        }

        return planRun.outputValues();
    }

    private Map<String, Object> executeReportNode(
        WorkflowNode node, Map<String, Object> inputs, WorkflowRun run
    ) {
        Map<String, Object> outputs = new LinkedHashMap<>();

        if (StringUtils.hasText(node.planId())) {
            PlanDefinition plan = planService.getTask(node.planId());
            try {
                Path outputDir = workspaceDirectoryService.workflowTemp(run.id());
                for (var output : plan.outputs()) {
                    Object value = inputs.get(output.name());
                    if (value != null) {
                        outputArtifactService.materialize(
                            run.id(), plan.id(), output.name(), output.type(),
                            value, outputDir
                        );
                        outputs.put(output.name(), value);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to materialize report outputs: " + e.getMessage(), e);
            }
        }

        if (StringUtils.hasText(node.messageTemplate())) {
            outputs.put("report_text", node.messageTemplate());
        }

        return outputs;
    }

    private String executeGateNode(WorkflowNode node, WorkflowRun run) {
        InboxMessageToType toType = node.type() == WorkflowNodeType.USER_APPROVAL
            ? InboxMessageToType.USER : InboxMessageToType.AGENT;

        String body = StringUtils.hasText(node.messageTemplate())
            ? node.messageTemplate()
            : "Approval required for workflow step: " + node.key();

        InboxMessage message = inboxService.createApprovalMessage(
            toType,
            toType == InboxMessageToType.AGENT ? node.planId() : null,
            null,
            body,
            run.id(),
            run.currentNodeIndex()
        );

        log.info("Gate node '{}' created approval message {}", node.key(), message.id());
        return message.id();
    }

    private void executeMessageNode(WorkflowNode node, WorkflowRun run) {
        InboxMessageToType toType = node.type() == WorkflowNodeType.USER_MESSAGE
            ? InboxMessageToType.USER : InboxMessageToType.AGENT;

        String body = StringUtils.hasText(node.messageTemplate())
            ? node.messageTemplate()
            : "Message from workflow: " + node.key();

        inboxService.createInfoMessage(
            toType,
            toType == InboxMessageToType.AGENT ? node.planId() : null,
            null,
            body,
            toJson(Map.of("workflowRunId", run.id(), "nodeKey", node.key()))
        );

        log.info("Message node '{}' sent to {}", node.key(), toType.wireName());
    }

    private Map<String, Object> executeDelegationNode(
        WorkflowNode node, Map<String, Object> inputs, WorkflowRun run
    ) {
        Map<String, Object> childOutputs = new LinkedHashMap<>();

        if (StringUtils.hasText(node.planId())) {
            try {
                PlanRun childRun = planService.startRun(node.planId(), inputs);
                PlanRun completed = planService.completeRun(
                    childRun.id(), Map.of(), "Delegated run completed", List.of());
                childOutputs.put("childRunId", completed.id());
                childOutputs.put("childStatus", completed.status().name());
            } catch (Exception e) {
                throw new RuntimeException("Delegation failed for node '"
                    + node.key() + "': " + e.getMessage(), e);
            }
        }

        return childOutputs;
    }

    private Map<String, Object> executeValidationNode(WorkflowNode node, Map<String, Object> inputs) {
        Object requiredObj = node.config().get("requiredInputs");
        if (requiredObj instanceof List<?> requiredInputs) {
            List<String> missing = requiredInputs.stream()
                .map(Object::toString)
                .filter(name -> !inputs.containsKey(name) || inputs.get(name) == null
                    || (inputs.get(name) instanceof String text && !StringUtils.hasText(text)))
                .toList();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Validation node '" + node.key()
                    + "' missing required value(s): " + String.join(", ", missing));
            }
        }
        Map<String, Object> outputs = new LinkedHashMap<>(inputs);
        outputs.put("valid", true);
        return outputs;
    }

    private Map<String, Object> executeCopyNode(WorkflowNode node, Map<String, Object> inputs) {
        Map<String, Object> outputs = new LinkedHashMap<>(inputs);
        Object copiesObj = node.config().get("copies");
        if (copiesObj instanceof Map<?, ?> copies) {
            for (var entry : copies.entrySet()) {
                String target = String.valueOf(entry.getKey());
                String source = String.valueOf(entry.getValue());
                if (inputs.containsKey(source)) {
                    outputs.put(target, inputs.get(source));
                }
            }
        }
        return outputs;
    }

    private Map<String, Object> executeLogNode(WorkflowNode node, Map<String, Object> inputs, WorkflowRun run) {
        try {
            Path outputDir = workspaceDirectoryService.workflowTemp(run.id());
            for (var entry : inputs.entrySet()) {
                outputArtifactService.materialize(
                    run.id(), node.key(), entry.getKey(),
                    io.mindspice.magenta2.ai.chat.plan.PlanFieldType.STRING,
                    entry.getValue(), outputDir
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to materialize log node '" + node.key()
                + "': " + e.getMessage(), e);
        }
        return new LinkedHashMap<>(inputs);
    }

    // ════════════════════════════════════════════════════════════════
    //  Input resolution (route-aware)
    // ════════════════════════════════════════════════════════════════

    /**
     * Resolve a node's inputs from its incoming routes and literal config.
     * Uses both new routes and legacy bindings.
     */
    private Map<String, Object> resolveNodeInputs(
        WorkflowNode node,
        WorkflowDefinition def,
        Map<String, Map<String, Object>> outputsByNode
    ) {
        Map<String, Object> values = new LinkedHashMap<>();

        // 1. Apply route-based inputs
        for (WorkflowRoute route : def.incomingRoutes(node.key())) {
            switch (route.routeType()) {
                case MAP_OUTPUT -> {
                    if (route.fromNodeKey() == null) continue;
                    Map<String, Object> sourceOutputs = outputsByNode.get(route.fromNodeKey());
                    if (sourceOutputs == null) continue;
                    String outputName = route.fromOutputName();
                    if (outputName != null && sourceOutputs.containsKey(outputName)) {
                        values.put(route.toInputName() != null ? route.toInputName() : outputName,
                            sourceOutputs.get(outputName));
                    }
                }
                case PASS_THROUGH -> {
                    if (route.fromNodeKey() == null) continue;
                    Map<String, Object> sourceOutputs = outputsByNode.get(route.fromNodeKey());
                    if (sourceOutputs == null) continue;
                    // Forward all source outputs as a single map under the input name
                    String inputName = StringUtils.hasText(route.toInputName())
                        ? route.toInputName() : route.fromNodeKey();
                    values.put(inputName, new LinkedHashMap<>(sourceOutputs));
                }
                case CONTROL -> {
                    // Control routes don't carry data; handled at execution level
                }
                case LOG -> {
                    // LOG routes don't produce inputs; handled separately
                }
            }
        }

        // 2. Apply literal config from node
        if (node.config() != null) {
            values.putAll(node.config());
        }

        // 3. Legacy binding resolution fallback
        if (!node.inputBindings().isEmpty()) {
            PlanDefinition plan = null;
            if (StringUtils.hasText(node.planId())) {
                try { plan = planService.getTask(node.planId()); }
                catch (Exception ignored) { }
            }

            Map<String, Object> legacyValues = BindingResolver.resolve(
                node.inputBindings(),
                plan != null ? plan.inputs() : List.of(),
                outputsByNode
            );
            // Legacy values overlay route values
            values.putAll(legacyValues);
        }

        return values;
    }

    // ════════════════════════════════════════════════════════════════
    //  Helpers
    // ════════════════════════════════════════════════════════════════

    private int nodeIndex(WorkflowRun run, String nodeKey) {
        for (int i = 0; i < run.nodeRuns().size(); i++) {
            if (run.nodeRuns().get(i).nodeKey().equals(nodeKey)) return i;
        }
        return -1;
    }

    private WorkflowNodeRun findWaitingNode(WorkflowRun run) {
        return run.nodeRuns().stream()
            .filter(nr -> nr.status() == WorkflowNodeRunStatus.WAITING)
            .findFirst()
            .orElse(null);
    }

    private void updateNodeRun(WorkflowRun run, String nodeKey, int nodeIndex,
                                WorkflowNodeRunStatus status,
                                Map<String, Object> inputs, Map<String, Object> outputs) {
        // Run state is re-loaded on each loop iteration; in-memory update only for tracking
    }

    private WorkflowRun saveRunWithNodeStatus(WorkflowRun run, int nodeIndex,
                                               WorkflowNodeRunStatus status,
                                               Map<String, Object> inputs,
                                               Map<String, Object> outputs) {
        List<WorkflowNodeRun> updatedRuns = new ArrayList<>(run.nodeRuns());
        if (nodeIndex >= 0 && nodeIndex < updatedRuns.size()) {
            WorkflowNodeRun existing = updatedRuns.get(nodeIndex);
            updatedRuns.set(nodeIndex, new WorkflowNodeRun(
                existing.nodeKey(), existing.type(), status,
                inputs, outputs,
                existing.startedAt(),
                status == WorkflowNodeRunStatus.COMPLETED || status == WorkflowNodeRunStatus.FAILED
                    ? Instant.now() : null
            ));
        }
        return repository.saveRun(new WorkflowRun(
            run.id(), run.workflowId(),
            status == WorkflowNodeRunStatus.WAITING ? WorkflowRunStatus.WAITING : WorkflowRunStatus.RUNNING,
            nodeIndex + 1, updatedRuns,
            run.workspacePath(), run.outputDir(), run.workflowSnapshot(),
            run.finalMessage(), run.errorText(),
            run.createdAt(), Instant.now(), run.startedAt(), null
        ));
    }

    private void cleanupWorkspace(WorkflowRun run) {
        if (!StringUtils.hasText(run.workspacePath())) return;
        try {
            Path path = Path.of(run.workspacePath());
            if (java.nio.file.Files.exists(path)) {
                try (var files = java.nio.file.Files.walk(path)) {
                    files.sorted(java.util.Comparator.reverseOrder())
                        .forEach(p -> {
                            try { java.nio.file.Files.deleteIfExists(p); }
                            catch (IOException ignored) { }
                        });
                }
                log.info("Cleaned up workflow temp workspace: {}", run.workspacePath());
            }
        } catch (IOException e) {
            log.warn("Failed to clean up workspace {}: {}", run.workspacePath(), e.getMessage());
        }
    }

    private void emitSse(Consumer<String> callback, String event, Map<String, Object> data) {
        if (callback != null) {
            try {
                String json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .writeValueAsString(Map.of("event", event, "data", data));
                callback.accept(json);
            } catch (Exception e) {
                log.warn("Failed to emit SSE event: {}", e.getMessage());
            }
        }
    }

    private String toJson(Object value) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }
}
