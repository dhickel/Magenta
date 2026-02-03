package com.magenta.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class WorkflowTaskTemplate {
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("task_prompt")
    private String taskPrompt;

    @JsonProperty("required_tools")
    private List<String> requiredTools;

    @JsonProperty("parameters")
    private Map<String, ParameterSpec> parameterSpecs;

    @JsonProperty("type")
    private TaskWorkflow.WorkflowTaskType type;

    // Accessors
    public String name() { return name; }
    public String description() { return description; }
    public String taskPrompt() { return taskPrompt; }
    public List<String> requiredTools() { return requiredTools != null ? requiredTools : List.of(); }
    public Map<String, ParameterSpec> parameterSpecs() { return parameterSpecs != null ? parameterSpecs : Map.of(); }
    public TaskWorkflow.WorkflowTaskType type() { return type; }

    // Instantiate TaskWorkflow from template with parameter values
    public TaskWorkflow instantiate(String id, Map<String, Object> parameterValues) {
        validateParameters(parameterValues);
        return new TaskWorkflow(id, name, description, taskPrompt, requiredTools(), parameterValues, type);
    }

    private void validateParameters(Map<String, Object> values) {
        for (Map.Entry<String, ParameterSpec> spec : parameterSpecs().entrySet()) {
            if (spec.getValue().required() && !values.containsKey(spec.getKey())) {
                throw new IllegalArgumentException("Required parameter missing: " + spec.getKey());
            }
        }
    }

    public record ParameterSpec(
        @JsonProperty("type") String type,
        @JsonProperty("required") boolean required,
        @JsonProperty("default") Object defaultValue
    ) {}
}
