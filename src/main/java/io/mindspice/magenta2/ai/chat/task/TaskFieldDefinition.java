package io.mindspice.magenta2.ai.chat.task;

public record TaskFieldDefinition(
    String name,
    TaskValueType type,
    String description,
    boolean required,
    String schema,
    String example
) {
    public TaskFieldDefinition {
        type = type == null ? TaskValueType.STRING : type;
    }
}
