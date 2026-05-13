package io.mindspice.magenta2.ai.orchestration.workflow;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Service for workflow definition CRUD and run lifecycle.
 * Delegates node execution to {@link WorkflowRunner}.
 *
 * <p>Phase 04: Routes are now the primary connection model. Legacy
 * inputBindings are imported to routes on save.
 */
@Service("orchestrationWorkflowService")
public class WorkflowService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowService.class);

    private final WorkflowRepository repository;
    private final PlanService planService;
    private final WorkflowRunner workflowRunner;
    private final WorkflowValidator validator;

    public WorkflowService(WorkflowRepository repository, PlanService planService,
                           WorkflowRunner workflowRunner) {
        this.repository = repository;
        this.planService = planService;
        this.workflowRunner = workflowRunner;
        this.validator = new WorkflowValidator(planService);
    }

    // ════════════════════════════════════════════════════════════════
    //  Definition CRUD
    // ════════════════════════════════════════════════════════════════

    public List<WorkflowDefinition> listDefinitions() {
        return repository.findAllDefinitions();
    }

    public WorkflowDefinition getDefinition(String id) {
        return repository.findDefinition(id)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + id));
    }

    public Optional<WorkflowDefinition> findDefinition(String id) {
        return repository.findDefinition(id);
    }

    /**
     * Save a workflow definition. Automatically imports legacy inputBindings
     * to routes before persisting.
     */
    public WorkflowDefinition saveDefinition(WorkflowDefinition definition) {
        String id = StringUtils.hasText(definition.id()) ? definition.id() : UUID.randomUUID().toString();

        // Auto-import legacy bindings to routes
        List<WorkflowRoute> mergedRoutes = importBindingsToRoutes(definition);

        // Validate node references
        for (WorkflowNode node : definition.nodes()) {
            if (node.type() == WorkflowNodeType.TASK) {
                if (!StringUtils.hasText(node.planId())) {
                    throw new IllegalArgumentException(
                        "TASK node '" + node.key() + "' requires a planId");
                }
                // Verify plan exists
                try {
                    planService.getTask(node.planId());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "TASK node '" + node.key() + "' references unknown plan: " + node.planId());
                }
            }
            // Validate legacy bindings reference valid prior nodes
            for (WorkflowBinding binding : node.inputBindings()) {
                if (binding.isStepOutput()) {
                    boolean found = definition.nodes().stream()
                        .anyMatch(n -> n.key().equals(binding.sourceNodeKey()));
                    if (!found) {
                        throw new IllegalArgumentException(
                            "Node '" + node.key() + "' binding references unknown source node: "
                                + binding.sourceNodeKey());
                    }
                }
            }
        }

        // Validate routes reference valid nodes
        for (WorkflowRoute route : mergedRoutes) {
            if (route.fromNodeKey() != null) {
                boolean sourceFound = definition.nodes().stream()
                    .anyMatch(n -> n.key().equals(route.fromNodeKey()));
                if (!sourceFound) {
                    throw new IllegalArgumentException(
                        "Route '" + route.id() + "' references unknown source node: "
                            + route.fromNodeKey());
                }
            }
            boolean destFound = definition.nodes().stream()
                .anyMatch(n -> n.key().equals(route.toNodeKey()));
            if (!destFound) {
                throw new IllegalArgumentException(
                    "Route '" + route.id() + "' references unknown destination node: "
                        + route.toNodeKey());
            }
        }

        return repository.saveDefinition(new WorkflowDefinition(
            id, definition.title(), definition.summary(),
            definition.nodes(), mergedRoutes,
            definition.createdAt(), definition.updatedAt()
        ));
    }

    /**
     * Save a workflow definition with full graph validation as the durable save gate.
     * Rejects blocking validation errors before persisting.
     */
    public WorkflowDefinition saveDefinitionValidated(WorkflowDefinition definition) {
        String id = StringUtils.hasText(definition.id()) ? definition.id() : UUID.randomUUID().toString();
        List<WorkflowRoute> mergedRoutes = importBindingsToRoutes(definition);

        WorkflowDefinition candidate = new WorkflowDefinition(
            id, definition.title(), definition.summary(),
            definition.nodes(), mergedRoutes,
            definition.createdAt(), definition.updatedAt()
        );

        // Full graph validation gate — reject blocking errors before durable save
        WorkflowValidator.ValidationResult result = validator.validate(candidate);
        if (!result.valid()) {
            throw new IllegalArgumentException(String.join("; ", result.errors()));
        }

        return repository.saveDefinition(candidate);
    }

    public void deleteDefinition(String id) {
        repository.deleteDefinition(id);
    }

    // ════════════════════════════════════════════════════════════════
    //  Compatibility: import old bindings to routes
    // ════════════════════════════════════════════════════════════════

    /**
     * Convert legacy {@link WorkflowNode#inputBindings()} into
     * {@link WorkflowRoute} entries.
     *
     * <ul>
     *   <li>LITERAL bindings become config entries on the target node.</li>
     *   <li>STEP_OUTPUT bindings become MAP_OUTPUT routes.</li>
     *   <li>Existing explicit routes are preserved.</li>
     * </ul>
     */
    public List<WorkflowRoute> importBindingsToRoutes(WorkflowDefinition definition) {
        List<WorkflowRoute> routes = new ArrayList<>(definition.routes());
        Set<String> existingRouteKeys = new HashSet<>();
        for (WorkflowRoute r : routes) {
            existingRouteKeys.add(r.id());
        }

        for (WorkflowNode node : definition.nodes()) {
            for (WorkflowBinding binding : node.inputBindings()) {
                if (binding.isStepOutput()) {
                    // Generate a route id
                    String routeId = "imported-" + binding.sourceNodeKey() + "-to-" + node.key() + "-" + binding.inputName();
                    if (existingRouteKeys.contains(routeId)) continue;

                    String sourceOutputName = StringUtils.hasText(binding.sourceOutputName())
                        ? binding.sourceOutputName() : binding.inputName();

                    routes.add(new WorkflowRoute(
                        routeId,
                        binding.sourceNodeKey(),
                        sourceOutputName,
                        node.key(),
                        binding.inputName(),
                        WorkflowRouteType.MAP_OUTPUT,
                        null
                    ));
                    existingRouteKeys.add(routeId);
                }
                // LITERAL bindings: the runner reads from node.config(),
                // so old LITERAL bindings without a route are still supported
                // via the BindingResolver fallback in the runner.
            }
        }

        return routes;
    }

    // ════════════════════════════════════════════════════════════════
    //  Compatibility warnings / validation
    // ════════════════════════════════════════════════════════════════

    /**
     * Check a workflow definition for compatibility issues before saving.
     * Returns warnings for type mismatches, missing nodes, etc.
     * Uses both legacy binding checks and new route validation.
     */
    public List<String> compatibilityWarnings(WorkflowDefinition definition) {
        List<String> warnings = new ArrayList<>();

        // Legacy binding warnings
        for (WorkflowNode node : definition.nodes()) {
            for (WorkflowBinding binding : node.inputBindings()) {
                if (!binding.isStepOutput()) continue;

                WorkflowNode sourceNode = definition.nodeByKey(binding.sourceNodeKey());
                if (sourceNode == null) {
                    warnings.add("Unknown source node: " + binding.sourceNodeKey());
                    continue;
                }

                if (sourceNode.type() != WorkflowNodeType.TASK) {
                    warnings.add("Source node '" + binding.sourceNodeKey()
                        + "' is " + sourceNode.type().wireName() + " but binding expects outputs");
                    continue;
                }

                try {
                    PlanDefinition sourcePlan = planService.getTask(sourceNode.planId());
                    var sourceOutput = sourcePlan.outputs().stream()
                        .filter(o -> o.name().equals(binding.sourceOutputName()))
                        .findFirst().orElse(null);

                    if (sourceOutput == null) {
                        warnings.add("Source node '" + sourceNode.key() + "' has no output named '"
                            + binding.sourceOutputName() + "'");
                    }

                    if (node.type() == WorkflowNodeType.TASK && StringUtils.hasText(node.planId())) {
                        PlanDefinition destPlan = planService.getTask(node.planId());
                        var destInput = destPlan.inputs().stream()
                            .filter(i -> i.name().equals(binding.inputName()))
                            .findFirst().orElse(null);

                        if (sourceOutput != null && destInput != null
                            && sourceOutput.type() != destInput.type()) {
                            warnings.add("Type mismatch: " + sourceNode.key() + "."
                                + sourceOutput.name() + " is " + sourceOutput.type().wireName()
                                + " but " + node.key() + "." + destInput.name()
                                + " expects " + destInput.type().wireName());
                        }
                    }
                } catch (Exception e) {
                    warnings.add("Cannot validate binding for node '" + node.key()
                        + "': " + e.getMessage());
                }
            }
        }

        // New route validation — include both errors and warnings as warnings for compat
        WorkflowValidator.ValidationResult result = validator.validate(definition);
        warnings.addAll(result.warnings());
        warnings.addAll(result.errors());

        return warnings;
    }

    /**
     * Full graph validation returning structured errors and warnings.
     */
    public WorkflowValidator.ValidationResult validateGraph(WorkflowDefinition definition) {
        return validator.validate(definition);
    }

    // ════════════════════════════════════════════════════════════════
    //  Runs
    // ════════════════════════════════════════════════════════════════

    public WorkflowRun startRun(String workflowId) {
        WorkflowDefinition definition = getDefinition(workflowId);
        return workflowRunner.startRun(definition);
    }

    public WorkflowRun getRun(String runId) {
        return repository.findRun(runId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow run not found: " + runId));
    }

    public List<WorkflowRun> listRuns(String workflowId) {
        return repository.findRunsByWorkflowId(workflowId);
    }

    /**
     * Resume a waiting workflow run after an approval response.
     */
    public WorkflowRun resumeRun(String runId) {
        WorkflowRun run = getRun(runId);
        if (run.status() != WorkflowRunStatus.WAITING) {
            throw new IllegalStateException("Workflow run is not waiting: " + runId + " (status: " + run.status().wireName() + ")");
        }
        return workflowRunner.resumeRun(run);
    }

    /**
     * Execute a workflow synchronously (used by tests and simple integrations).
     */
    public WorkflowRun runSynchronously(String workflowId) {
        WorkflowDefinition definition = getDefinition(workflowId);
        return workflowRunner.runSynchronously(definition);
    }
}
