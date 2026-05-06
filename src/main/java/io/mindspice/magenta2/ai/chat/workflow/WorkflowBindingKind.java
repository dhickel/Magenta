package io.mindspice.magenta2.ai.chat.workflow;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum WorkflowBindingKind {
    LITERAL("literal"),
    STEP_OUTPUT("step_output");

    private final String wireName;

    WorkflowBindingKind(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static WorkflowBindingKind fromWireName(String value) {
        if (value == null || value.isBlank()) {
            return LITERAL;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (WorkflowBindingKind kind : values()) {
            if (kind.wireName.equals(normalized) || kind.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("Unknown workflow binding kind: " + value);
    }
}
