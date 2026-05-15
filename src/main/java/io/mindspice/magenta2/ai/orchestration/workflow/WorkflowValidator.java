package io.mindspice.magenta2.ai.orchestration.workflow;

import io.mindspice.magenta2.ai.chat.plan.PlanDefinition;
import io.mindspice.magenta2.ai.chat.plan.PlanService;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates workflow definitions against graph-structure rules.
 * Checks node uniqueness, route endpoint existence, cycle detection,
 * and required input satisfaction.
 */
public class WorkflowValidator {

    private final PlanService planService;

    public WorkflowValidator(PlanService planService) {
        this.planService = planService;
    }

    /**
     * Structured validation result.
     */
    public record ValidationResult(List<String> errors, List<String> warnings) {
        public ValidationResult {
            errors = errors == null ? List.of() : List.copyOf(errors);
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }

        public boolean valid() {
            return errors.isEmpty();
        }
    }

    /**
     * Validate a workflow definition and return structured errors/warnings.
     */
    public ValidationResult validate(WorkflowDefinition definition) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Node key uniqueness
        validateNodeUniqueness(definition, errors);

        // 2. Route endpoint existence
        validateRouteEndpoints(definition, errors);

        // 3. Route id uniqueness
        validateRouteUniqueness(definition, errors);

        // 4. Duplicate route detection
        validateDuplicateRoutes(definition, errors);

        // 5. Every route has a route type (enforced by record, but double-check)
        for (WorkflowRoute route : definition.routes()) {
            if (route.routeType() == null) {
                errors.add("Route '" + route.id() + "' has no route type");
            }
        }

        // 4. Type compatibility
        validateTypeCompatibility(definition, errors, warnings);

        // 5. Cycle detection
        if (!hasCycle(definition)) {
            // Only check input requirements if graph is acyclic
            validateInputRequirements(definition, errors, warnings);
        } else {
            errors.add("Workflow contains a cycle; graph must be acyclic");
        }

        // 6. Required task inputs satisfied
        validateTaskInputSatisfaction(definition, errors, warnings);

        return new ValidationResult(errors, warnings);
    }

    private void validateNodeUniqueness(WorkflowDefinition def, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (WorkflowNode node : def.nodes()) {
            if (!seen.add(node.key())) {
                errors.add("Duplicate node key: '" + node.key() + "'");
            }
        }
    }

    private void validateRouteEndpoints(WorkflowDefinition def, List<String> errors) {
        Set<String> nodeKeys = new HashSet<>();
        for (WorkflowNode node : def.nodes()) {
            nodeKeys.add(node.key());
        }

        for (WorkflowRoute route : def.routes()) {
            if (route.fromNodeKey() != null && !nodeKeys.contains(route.fromNodeKey())) {
                errors.add("Route '" + route.id() + "': source node '" + route.fromNodeKey() + "' not found");
            }
            if (!nodeKeys.contains(route.toNodeKey())) {
                errors.add("Route '" + route.id() + "': destination node '" + route.toNodeKey() + "' not found");
            }
        }
    }

    private void validateRouteUniqueness(WorkflowDefinition def, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (WorkflowRoute route : def.routes()) {
            if (!seen.add(route.id())) {
                errors.add("Duplicate route id: '" + route.id() + "'");
            }
        }
    }

    private void validateDuplicateRoutes(WorkflowDefinition def, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (WorkflowRoute route : def.routes()) {
            String identity = route.fromNodeKey() + "::"
                + route.fromOutputName() + "::"
                + route.toNodeKey() + "::"
                + route.toInputName() + "::"
                + route.routeType().name();
            if (!seen.add(identity)) {
                errors.add("Duplicate route detected: fromNodeKey='" + route.fromNodeKey()
                    + "', fromOutputName='" + route.fromOutputName()
                    + "', toNodeKey='" + route.toNodeKey()
                    + "', toInputName='" + route.toInputName()
                    + "', routeType=" + route.routeType().name());
            }
        }
    }

    private void validateTypeCompatibility(WorkflowDefinition def, List<String> errors, List<String> warnings) {
        for (WorkflowRoute route : def.routes()) {
            if (route.routeType() != WorkflowRouteType.MAP_OUTPUT) continue;
            if (!StringUtils.hasText(route.fromNodeKey())) continue;

            WorkflowNode sourceNode = def.nodeByKey(route.fromNodeKey());
            WorkflowNode destNode = def.nodeByKey(route.toNodeKey());
            if (sourceNode == null || destNode == null) continue;

            // Check plan types if both nodes are TASK nodes
            if (sourceNode.type() == WorkflowNodeType.TASK && StringUtils.hasText(sourceNode.planId())
                && destNode.type() == WorkflowNodeType.TASK && StringUtils.hasText(destNode.planId())) {
                try {
                    PlanDefinition sourcePlan = planService.getTask(sourceNode.planId());
                    PlanDefinition destPlan = planService.getTask(destNode.planId());

                    var sourceOutput = sourcePlan.outputs().stream()
                        .filter(o -> o.name().equals(route.fromOutputName()))
                        .findFirst().orElse(null);
                    var destInput = destPlan.inputs().stream()
                        .filter(i -> i.name().equals(route.toInputName()))
                        .findFirst().orElse(null);

                    if (sourceOutput != null && destInput != null
                        && sourceOutput.type() != destInput.type()) {
                        warnings.add("Type mismatch on route '" + route.id() + "': "
                            + sourceNode.key() + "." + sourceOutput.name() + " is "
                            + sourceOutput.type().wireName() + " but "
                            + destNode.key() + "." + destInput.name() + " expects "
                            + destInput.type().wireName());
                    }
                } catch (Exception e) {
                    warnings.add("Cannot validate type compatibility for route '" + route.id()
                        + "': " + e.getMessage());
                }
            }
        }
    }

    private boolean hasCycle(WorkflowDefinition def) {
        // Build adjacency list from routes that create dependencies
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (WorkflowNode node : def.nodes()) {
            adjacency.put(node.key(), new ArrayList<>());
        }
        for (WorkflowRoute route : def.routes()) {
            if (route.createsDependency() && route.fromNodeKey() != null) {
                adjacency.computeIfAbsent(route.fromNodeKey(), k -> new ArrayList<>())
                    .add(route.toNodeKey());
            }
        }

        // DFS with colors: 0=unvisited, 1=visiting, 2=done
        Map<String, Integer> color = new HashMap<>();
        for (String nodeKey : adjacency.keySet()) {
            color.put(nodeKey, 0);
        }

        for (String nodeKey : adjacency.keySet()) {
            if (color.get(nodeKey) == 0) {
                if (dfsVisit(nodeKey, adjacency, color)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsVisit(String node, Map<String, List<String>> adjacency, Map<String, Integer> color) {
        color.put(node, 1);
        for (String neighbor : adjacency.getOrDefault(node, List.of())) {
            Integer c = color.get(neighbor);
            if (c == null) continue;
            if (c == 1) return true;
            if (c == 0 && dfsVisit(neighbor, adjacency, color)) return true;
        }
        color.put(node, 2);
        return false;
    }

    private void validateInputRequirements(WorkflowDefinition def, List<String> errors, List<String> warnings) {
        Set<String> rootNodes = findRootNodes(def);

        for (WorkflowNode node : def.nodes()) {
            // Skip non-executable nodes
            if (node.isMessage()) continue;

            // Root nodes don't need incoming routes
            if (rootNodes.contains(node.key())) continue;

            // Check if this node has at least one incoming dependency-creating route
            boolean hasIncoming = def.routes().stream()
                .anyMatch(r -> r.createsDependency() && r.toNodeKey().equals(node.key()));
            // Or has literal config input
            boolean hasLiteralInput = node.config() != null && !node.config().isEmpty();

            if (!hasIncoming && !hasLiteralInput) {
                if (node.type() == WorkflowNodeType.TASK) {
                    errors.add("TASK node '" + node.key() + "' has no incoming routes or literal config; "
                        + "required inputs cannot be satisfied");
                } else {
                    warnings.add("Node '" + node.key() + "' has no incoming routes; "
                        + "it may not receive any input");
                }
            }
        }
    }

    /**
     * Find root nodes: nodes with no incoming dependency-creating routes.
     */
    private Set<String> findRootNodes(WorkflowDefinition def) {
        Set<String> hasIncoming = new HashSet<>();
        for (WorkflowRoute route : def.routes()) {
            if (route.createsDependency()) {
                hasIncoming.add(route.toNodeKey());
            }
        }
        Set<String> roots = new HashSet<>();
        for (WorkflowNode node : def.nodes()) {
            if (!hasIncoming.contains(node.key())) {
                roots.add(node.key());
            }
        }
        return roots;
    }

    private void validateTaskInputSatisfaction(WorkflowDefinition def, List<String> errors, List<String> warnings) {
        for (WorkflowNode node : def.nodes()) {
            if (node.type() != WorkflowNodeType.TASK) continue;
            if (!StringUtils.hasText(node.planId())) continue;

            try {
                PlanDefinition plan = planService.getTask(node.planId());
                if (plan.inputs() == null || plan.inputs().isEmpty()) continue;

                // Collect incoming mapped inputs from routes
                Set<String> mappedInputs = new HashSet<>();
                for (WorkflowRoute route : def.incomingRoutes(node.key())) {
                    if (route.routeType() == WorkflowRouteType.MAP_OUTPUT
                        && StringUtils.hasText(route.toInputName())) {
                        mappedInputs.add(route.toInputName());
                    }
                }

                // Also check literal config
                if (node.config() != null) {
                    mappedInputs.addAll(node.config().keySet());
                }

                // Also check deprecated inputBindings for compat
                for (WorkflowBinding binding : node.inputBindings()) {
                    mappedInputs.add(binding.inputName());
                }

                for (var input : plan.inputs()) {
                    if (input.required() && !mappedInputs.contains(input.name())) {
                        errors.add("TASK node '" + node.key() + "': required input '"
                            + input.name() + "' is not satisfied by any route or config");
                    }
                }
            } catch (Exception e) {
                warnings.add("Cannot validate task inputs for node '" + node.key()
                    + "': " + e.getMessage());
            }
        }
    }
}
