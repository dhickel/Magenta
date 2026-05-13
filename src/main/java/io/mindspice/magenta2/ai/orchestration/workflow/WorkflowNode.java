package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * A single node within a workflow definition. Nodes execute in dependency
 * order determined by {@link WorkflowRoute} connections rather than by
 * sequential index.
 *
 * @param key             unique key within the workflow (e.g. "draft_plan")
 * @param type            the node type determining execution behavior
 * @param planId          plan definition id (required for TASK nodes)
 * @param label           human-readable display label (defaults to key)
 * @param inputName       single input name for this node (replaces multi-binding)
 * @param config          opaque configuration map for node-level settings
 * @param parallel        for delegation nodes: enqueue children in parallel
 * @param inputBindings   [DEPRECATED] legacy multi-binding; use routes instead.
 *                        Converted to routes by compatibility importer on save.
 * @param messageTemplate message template for approval/message nodes
 * @param resumePolicy    for gate nodes: APPROVE_CONTINUE, APPROVE_CONTINUE_REJECT_NEEDS_REVIEW, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowNode(
    String key,
    WorkflowNodeType type,
    String planId,
    String label,
    String inputName,
    Map<String, Object> config,
    boolean parallel,
    List<WorkflowBinding> inputBindings,
    String messageTemplate,
    String resumePolicy
) {
    public WorkflowNode {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Workflow node key must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Workflow node type must not be null for node: " + key);
        }
        inputBindings = inputBindings == null ? List.of() : List.copyOf(inputBindings);
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    /**
     * Compatibility constructor for old code that did not pass label, inputName,
     * or config. Sets label to key, inputName to null, and config to empty map.
     *
     * @deprecated use the full constructor with {@code label}, {@code inputName},
     *             and {@code config}. Old {@code inputBindings} are converted to
     *             routes by {@link WorkflowService}.
     */
    @Deprecated
    public WorkflowNode(String key, WorkflowNodeType type, String planId,
                        List<WorkflowBinding> inputBindings,
                        String messageTemplate, String resumePolicy,
                        boolean parallel) {
        this(key, type, planId, key, null, Map.of(), parallel,
             inputBindings, messageTemplate, resumePolicy);
    }

    /**
     * Returns true if this node is a gate (pauses the workflow until an external response).
     */
    public boolean isGate() {
        return type.isGate();
    }

    /**
     * Returns true if this node is a one-way message (does not pause).
     */
    public boolean isMessage() {
        return type.isMessage();
    }

    /**
     * Returns the display label, falling back to key if label is blank.
     */
    public String displayLabel() {
        return label != null && !label.isBlank() ? label : key;
    }
}
