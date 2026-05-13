package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;

/**
 * A project is a durable data-space and tracking wrapper with one owner agent.
 * Projects can link a git repository and own a persistent workspace.
 */
public record Project(
    String id,
    String name,
    String description,
    String ownerAgentId,
    String gitRepoUrl,
    String promptProfile,
    String model,
    String settingsOverrideJson,
    Instant createdAt,
    Instant updatedAt
) {}
