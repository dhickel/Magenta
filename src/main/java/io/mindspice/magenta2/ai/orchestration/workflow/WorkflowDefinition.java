package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * A workflow definition composed of nodes and explicit routes.
 * Routes determine execution order through a dependency graph rather
 * than sequential node index.
 *
 * @param id        unique identifier
 * @param title     human-readable title
 * @param summary   brief description of the workflow's purpose
 * @param nodes     list of workflow nodes
 * @param routes    explicit routes connecting node outputs to node inputs
 * @param createdAt creation timestamp
 * @param updatedAt last-update timestamp
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowDefinition(
    String id,
    String title,
    String summary,
    List<WorkflowNode> nodes,
    List<WorkflowRoute> routes,
    Instant createdAt,
    Instant updatedAt
) {
    public WorkflowDefinition {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Workflow definition title must not be blank");
        }
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    /**
     * Compatibility constructor for old code without routes.
     * @deprecated use the full constructor with {@code routes}.
     */
    @Deprecated
    public WorkflowDefinition(String id, String title, String summary,
                              List<WorkflowNode> nodes, Instant createdAt, Instant updatedAt) {
        this(id, title, summary, nodes, List.of(), createdAt, updatedAt);
    }

    /**
     * Find a node by its key in this definition.
     */
    public WorkflowNode nodeByKey(String key) {
        return nodes.stream()
            .filter(n -> n.key().equals(key))
            .findFirst()
            .orElse(null);
    }

    /**
     * Find a route by its id in this definition.
     */
    public WorkflowRoute routeById(String routeId) {
        return routes.stream()
            .filter(r -> r.id().equals(routeId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Find all incoming routes for a node (routes where toNodeKey matches).
     */
    public List<WorkflowRoute> incomingRoutes(String nodeKey) {
        return routes.stream()
            .filter(r -> r.toNodeKey().equals(nodeKey))
            .toList();
    }

    /**
     * Find all outgoing routes from a node (routes where fromNodeKey matches).
     */
    public List<WorkflowRoute> outgoingRoutes(String nodeKey) {
        return routes.stream()
            .filter(r -> nodeKey.equals(r.fromNodeKey()))
            .toList();
    }
}
