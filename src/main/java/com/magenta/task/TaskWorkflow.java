package com.magenta.task;

import java.util.List;
import java.util.Map;

public record TaskWorkflow(
    String id,
    String name,
    String description,
    String taskPrompt,
    List<String> requiredTools,
    Map<String, Object> parameters,
    WorkflowTaskType type
) {
    public enum WorkflowTaskType {
        CODE_GENERATION,
        CODE_ANALYSIS,
        REFACTORING,
        DOCUMENTATION,
        RESEARCH,
        FILE_MANIPULATION,
        CUSTOM
    }

    // Defensive copies
    public TaskWorkflow {
        requiredTools = List.copyOf(requiredTools);
        parameters = Map.copyOf(parameters);
    }

    // Apply parameter substitution {{key}} → value
    public String getResolvedTaskPrompt() {
        String resolved = taskPrompt;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            resolved = resolved.replace(placeholder, String.valueOf(entry.getValue()));
        }
        return resolved;
    }
}
