package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.List;

/**
 * Defines a durable job that coordinates multiple work items (plans or workflows).
 * Jobs can opt into a persistent per-assignment workspace and route child
 * outputs into the effective durable workspace.
 *
 * @param id              unique identifier
 * @param title           human-readable title
 * @param summary         short description of the job's purpose
 * @param items           ordered list of work items
 * @param promptProfile   steering profile for job-level agent behavior
 * @param model           default model for job items
 * @param settingsOverrideJson JSON settings overrides applied at the job level
 * @param createdAt       creation timestamp
 * @param updatedAt       last update timestamp
 */
public record JobDefinition(
    String id,
    String ownerAgentId,
    String projectId,
    String workspaceId,
    Boolean persistentWorkspaceEnabled,
    String status,
    String title,
    String summary,
    List<JobWorkItem> items,
    String promptProfile,
    String model,
    String settingsOverrideJson,
    Instant createdAt,
    Instant updatedAt
) {
    public JobDefinition(
        String id,
        String ownerAgentId,
        String projectId,
        String workspaceId,
        String status,
        String title,
        String summary,
        List<JobWorkItem> items,
        String promptProfile,
        String model,
        String settingsOverrideJson,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, ownerAgentId, projectId, workspaceId, false, status, title, summary, items,
            promptProfile, model, settingsOverrideJson, createdAt, updatedAt);
    }

    public JobDefinition(
        String id,
        String ownerAgentId,
        String projectId,
        String workspaceId,
        Boolean persistentWorkspaceEnabled,
        String title,
        String summary,
        List<JobWorkItem> items,
        String promptProfile,
        String model,
        String settingsOverrideJson,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, ownerAgentId, projectId, workspaceId, persistentWorkspaceEnabled, null, title, summary, items,
            promptProfile, model, settingsOverrideJson, createdAt, updatedAt);
    }

    public JobDefinition(
        String id,
        String title,
        String summary,
        List<JobWorkItem> items,
        String promptProfile,
        String model,
        String settingsOverrideJson,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, null, null, null, false, null, title, summary, items,
            promptProfile, model, settingsOverrideJson, createdAt, updatedAt);
    }
}
