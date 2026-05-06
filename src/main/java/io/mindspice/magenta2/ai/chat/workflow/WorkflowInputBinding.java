package io.mindspice.magenta2.ai.chat.workflow;

public record WorkflowInputBinding(
    String inputName,
    WorkflowBindingKind kind,
    Object literalValue,
    String sourceStepKey,
    String sourceOutputName
) {
    public WorkflowInputBinding {
        kind = kind == null ? WorkflowBindingKind.LITERAL : kind;
    }
}
