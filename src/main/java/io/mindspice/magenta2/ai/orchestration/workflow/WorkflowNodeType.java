package io.mindspice.magenta2.ai.orchestration.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/**
 * Types of nodes that can appear in a workflow definition.
 */
public enum WorkflowNodeType {
    /** Execute a finalized {@link io.mindspice.magenta2.ai.chat.plan.PlanDefinition}. */
    TASK("task"),
    /** Pause for user approval; creates a user inbox message and waits. */
    USER_APPROVAL("user_approval"),
    /** Pause for agent approval; creates an agent inbox message and waits. */
    AGENT_APPROVAL("agent_approval"),
    /** Send a one-way message to the user inbox; does not wait. */
    USER_MESSAGE("user_message"),
    /** Send a one-way message to an agent inbox; does not wait. */
    AGENT_MESSAGE("agent_message"),
    /** Start child plan/workflow runs and gather outputs. */
    DELEGATION("delegation"),
    /** Validate incoming values against configured criteria and stop on failure. */
    VALIDATION("validation"),
    /** Copy or fan out incoming values to downstream routes. */
    COPY("copy"),
    /** Materialize incoming values as run evidence without changing them. */
    LOG("log"),
    /** Materialize declared report/message outputs via OutputArtifactService. */
    REPORT("report");

    private final String wireName;

    WorkflowNodeType(String wireName) {
        this.wireName = wireName;
    }

    @JsonValue
    public String wireName() {
        return wireName;
    }

    @JsonCreator
    public static WorkflowNodeType fromWireName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Workflow node type must not be blank");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (WorkflowNodeType type : values()) {
            if (type.wireName.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown workflow node type: " + value);
    }

    public boolean isGate() {
        return this == USER_APPROVAL || this == AGENT_APPROVAL;
    }

    public boolean isMessage() {
        return this == USER_MESSAGE || this == AGENT_MESSAGE;
    }
}
