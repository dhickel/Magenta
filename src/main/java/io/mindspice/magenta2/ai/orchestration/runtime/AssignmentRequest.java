package io.mindspice.magenta2.ai.orchestration.runtime;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
    @NotBlank String agentId,
    String jobId,
    String jobItemId,
    @NotNull AssignmentType assignmentType,
    String runDisplayName,
    Integer priority,
    String modelOverride,
    String projectId,
    String workspaceId,
    String selectedWorkAreaId,
    String outputRouteType,
    String outputWorkAreaId,
    String outputDirectRelativePath,
    Map<String, Object> input
) {
    public static final String OUTPUT_ROUTE_DEFAULT = "DEFAULT";
    public static final String OUTPUT_ROUTE_WORK_AREA = "WORK_AREA";
    public static final String OUTPUT_ROUTE_DIRECT_DIRECTORY = "DIRECT_DIRECTORY";

    public AssignmentRequest(
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        String runDisplayName,
        Integer priority,
        String modelOverride,
        String projectId,
        String workspaceId,
        Map<String, Object> input
    ) {
        this(agentId, jobId, jobItemId, assignmentType, runDisplayName, priority, modelOverride, projectId, workspaceId,
            null, null, null, null, input);
    }

    public AssignmentRequest(
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        Integer priority,
        String modelOverride,
        String projectId,
        String workspaceId,
        Map<String, Object> input
    ) {
        this(agentId, jobId, jobItemId, assignmentType, null, priority, modelOverride, projectId, workspaceId,
            null, null, null, null, input);
    }

    public AssignmentRequest(
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        Integer priority,
        String modelOverride,
        String workspaceId,
        Map<String, Object> input
    ) {
        this(agentId, jobId, jobItemId, assignmentType, null, priority, modelOverride, null, workspaceId,
            null, null, null, null, input);
    }

    public AssignmentRequest(
        String agentId,
        String jobId,
        String jobItemId,
        AssignmentType assignmentType,
        Integer priority,
        String modelOverride,
        String projectId,
        String workspaceId,
        String selectedWorkAreaId,
        String outputRouteType,
        String outputWorkAreaId,
        String outputDirectRelativePath,
        Map<String, Object> input
    ) {
        this(agentId, jobId, jobItemId, assignmentType, null, priority, modelOverride, projectId, workspaceId,
            selectedWorkAreaId, outputRouteType, outputWorkAreaId, outputDirectRelativePath, input);
    }
}
