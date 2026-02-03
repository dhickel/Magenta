package com.magenta.agent;

import com.magenta.task.TaskWorkflow;

import java.time.LocalDateTime;

/**
 * AgentMessage represents messages exchanged between agents in the network.
 * Uses sealed ADT pattern for type-safe message variants.
 */
public sealed interface AgentMessage {
    String from();
    String content();
    LocalDateTime timestamp();

    default String type() {
        return switch (this) {
            case Direct d -> "direct";
            case Broadcast b -> "broadcast";
            case Delegation d -> "delegation";
        };
    }

    /**
     * Direct point-to-point message between two agents.
     */
    record Direct(
            String from,
            String to,
            String content,
            LocalDateTime timestamp
    ) implements AgentMessage { }

    /**
     * Broadcast message sent to all agents in the network.
     */
    record Broadcast(
            String from,
            String content,
            LocalDateTime timestamp
    ) implements AgentMessage { }

    /**
     * Delegate a TaskWorkFlow to another agent.
     */
    record Delegation(
            String from,
            String to,
            TaskWorkflow task,
            LocalDateTime timestamp
    ) implements AgentMessage {
        @Override
        public String content() {
            return "Task delegation: " + task.name();
        }
    }
}
