package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.Map;

public record WorkAssignment(
    String id,
    String agentId,
    String jobId,
    String jobItemId,
    AssignmentType assignmentType,
    String runDisplayName,
    int priority,
    OrchestrationStatus status,
    String modelOverride,
    String workspaceId,
    String projectId,
    String effectiveWorkspaceId,
    String effectiveWorkspaceKind,
    String selectedWorkAreaId,
    String outputRouteType,
    String outputWorkAreaId,
    String outputDirectRelativePath,
    int currentItemIndex,
    Map<String, Object> checkpoint,
    Map<String, Object> input,
    Map<String, Object> output,
    Map<String, Object> evidence,
    String errorText,
    String leaseOwner,
    Instant leaseExpiresAt,
    Instant createdAt,
    Instant updatedAt,
    Instant startedAt,
    Instant completedAt,
    Instant lastProgressAt,
    Instant lastHeartbeatAt
) {
    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        Instant lastProgressAt,
        Instant lastHeartbeatAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            null, null, null, null, null, null, null, currentItemIndex, checkpoint, input, output, evidence,
            errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt, startedAt, completedAt, lastProgressAt,
            lastHeartbeatAt
        );
    }

    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        String projectId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        Instant lastProgressAt,
        Instant lastHeartbeatAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            projectId, effectiveWorkspaceId, effectiveWorkspaceKind, null, null, null, null, currentItemIndex,
            checkpoint, input, output, evidence, errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt,
            startedAt, completedAt, lastProgressAt, lastHeartbeatAt
        );
    }

    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            null, null, null, null, null, null, null, currentItemIndex, checkpoint, input, output, evidence,
            errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt, startedAt, completedAt, null, null
        );
    }

    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        String projectId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            projectId, effectiveWorkspaceId, effectiveWorkspaceKind, null, null, null, null, currentItemIndex,
            checkpoint, input, output, evidence, errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt,
            startedAt, completedAt, null, null
        );
    }

    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        String projectId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        String selectedWorkAreaId,
        String outputRouteType,
        String outputWorkAreaId,
        String outputDirectRelativePath,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            projectId, effectiveWorkspaceId, effectiveWorkspaceKind, selectedWorkAreaId, outputRouteType,
            outputWorkAreaId, outputDirectRelativePath, currentItemIndex, checkpoint, input, output, evidence,
            errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt, startedAt, completedAt, null, null
        );
    }

    public WorkAssignment(
        String id,
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        int priority,
        OrchestrationStatus status,
        String modelOverride,
        String workspaceId,
        String projectId,
        String effectiveWorkspaceId,
        String effectiveWorkspaceKind,
        String selectedWorkAreaId,
        String outputRouteType,
        String outputWorkAreaId,
        String outputDirectRelativePath,
        int currentItemIndex,
        Map<String, Object> checkpoint,
        Map<String, Object> input,
        Map<String, Object> output,
        Map<String, Object> evidence,
        String errorText,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant completedAt,
        Instant lastProgressAt,
        Instant lastHeartbeatAt
    ) {
        this(
            id, agentId, jobId, jobItemId, assignmentType, null, priority, status, modelOverride, workspaceId,
            projectId, effectiveWorkspaceId, effectiveWorkspaceKind, selectedWorkAreaId, outputRouteType,
            outputWorkAreaId, outputDirectRelativePath, currentItemIndex, checkpoint, input, output, evidence,
            errorText, leaseOwner, leaseExpiresAt, createdAt, updatedAt, startedAt, completedAt, lastProgressAt,
            lastHeartbeatAt
        );
    }
}
