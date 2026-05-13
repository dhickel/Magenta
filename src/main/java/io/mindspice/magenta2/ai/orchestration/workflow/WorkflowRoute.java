package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Explicit route connecting workflow nodes. Replaces the legacy
 * {@link WorkflowBinding} multi-binding model with explicit edge semantics.
 *
 * @param id             unique route identifier within the workflow
 * @param fromNodeKey    source node key (null for root/external input routes)
 * @param fromOutputName source output name (null for pass-through or control)
 * @param toNodeKey      destination node key
 * @param toInputName    destination input name to populate
 * @param routeType      routing behavior
 * @param condition      optional condition expression for conditional routing
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowRoute(
    String id,
    String fromNodeKey,
    String fromOutputName,
    String toNodeKey,
    String toInputName,
    WorkflowRouteType routeType,
    String condition
) {
    public WorkflowRoute {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("WorkflowRoute id must not be blank");
        }
        if (toNodeKey == null || toNodeKey.isBlank()) {
            throw new IllegalArgumentException("WorkflowRoute toNodeKey must not be blank");
        }
        if (routeType == null) {
            routeType = WorkflowRouteType.MAP_OUTPUT;
        }
    }

    /**
     * Returns true if this route feeds a downstream node (creates a dependency).
     */
    public boolean createsDependency() {
        return routeType == WorkflowRouteType.MAP_OUTPUT
            || routeType == WorkflowRouteType.PASS_THROUGH
            || routeType == WorkflowRouteType.CONTROL;
    }
}
