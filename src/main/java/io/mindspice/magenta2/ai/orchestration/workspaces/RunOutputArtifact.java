package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.time.Instant;

/**
 * A materialized output artifact written or copied into a run's output
 * directory. Persisted so outputs are traceable after temp dirs are cleaned.
 */
public record RunOutputArtifact(
    String id,
    String runId,
    String planId,
    String agentId,
    String jobId,
    String projectId,
    String workspaceId,
    String runType,
    String outputName,
    String artifactType,
    String fileName,
    String filePath,
    String contentJson,
    Instant createdAt
) {
}
