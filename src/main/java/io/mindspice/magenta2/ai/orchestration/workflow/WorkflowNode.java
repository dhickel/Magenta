package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * A single graph node in workflow v2.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WorkflowNode(
    String key,
    WorkflowNodeType type,
    String planId,
    String label,
    String inputName,
    List<WorkflowPort> inputPorts,
    List<WorkflowPort> outputPorts,
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
        inputPorts = inputPorts == null ? List.of() : List.copyOf(inputPorts);
        outputPorts = outputPorts == null ? List.of() : List.copyOf(outputPorts);
        inputBindings = inputBindings == null ? List.of() : List.copyOf(inputBindings);
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    /**
     * Compatibility constructor for old callers.
     */
    @Deprecated
    public WorkflowNode(String key, WorkflowNodeType type, String planId,
                        String label, String inputName, Map<String, Object> config,
                        boolean parallel, List<WorkflowBinding> inputBindings,
                        String messageTemplate, String resumePolicy) {
        this(key, type, planId, label, inputName, List.of(), List.of(),
            config, parallel, inputBindings, messageTemplate, resumePolicy);
    }

    /**
     * Compatibility constructor for old callers.
     */
    @Deprecated
    public WorkflowNode(String key, WorkflowNodeType type, String planId,
                        List<WorkflowBinding> inputBindings,
                        String messageTemplate, String resumePolicy,
                        boolean parallel) {
        this(key, type, planId, key, null, List.of(), List.of(), Map.of(), parallel,
            inputBindings, messageTemplate, resumePolicy);
    }

    public boolean isGate() {
        return type.isGate();
    }

    public boolean isMessage() {
        return type.isMessage();
    }

    public String displayLabel() {
        return label != null && !label.isBlank() ? label : key;
    }
}
