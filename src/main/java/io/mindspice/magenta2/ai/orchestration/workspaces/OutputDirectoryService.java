package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Path;

import io.mindspice.magenta2.core.util.PlainPathSegmentValidator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OutputDirectoryService {
    private final EffectiveWorkspaceResolver effectiveWorkspaceResolver;
    private final WorkspaceDirectoryService workspaceDirectoryService;

    public OutputDirectoryService(
        EffectiveWorkspaceResolver effectiveWorkspaceResolver,
        WorkspaceDirectoryService workspaceDirectoryService
    ) {
        this.effectiveWorkspaceResolver = effectiveWorkspaceResolver;
        this.workspaceDirectoryService = workspaceDirectoryService;
    }

    public ResolvedOutputDirectory resolve(OutputPublicationTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        EffectiveWorkspace workspace = effectiveWorkspaceResolver.resolve(target.agentId(), target.projectId());
        Path outputDirectory = switch (target.kind()) {
            case TASK -> workspaceDirectoryService.taskOutput(
                workspace.root(), requireSegment(target.workUnitId(), "taskId"), requireSegment(target.runId(), "runId"));
            case WORKFLOW -> workspaceDirectoryService.workflowOutput(
                workspace.root(), requireSegment(target.workUnitId(), "workflowId"), requireSegment(target.runId(), "runId"));
            case JOB -> workspaceDirectoryService.jobAssignmentOutput(
                workspace.root(),
                requireSegment(target.jobAssignmentId(), "jobAssignmentId"),
                requireSegment(target.jobRunId(), "jobRunId"));
        };
        return new ResolvedOutputDirectory(
            workspace.ownerType(),
            workspace.ownerId(),
            workspace.agentId(),
            workspace.projectId(),
            workspace.workspaceId(),
            workspace.root(),
            outputDirectory,
            artifactContext(target, workspace)
        );
    }

    private OutputArtifactContext artifactContext(OutputPublicationTarget target, EffectiveWorkspace workspace) {
        return new OutputArtifactContext(
            workspace.agentId(),
            target.jobId(),
            target.jobAssignmentId(),
            target.jobRunId(),
            workspace.projectId(),
            workspace.workspaceId(),
            runType(target.kind())
        );
    }

    private String runType(OutputDirectoryKind kind) {
        return switch (kind) {
            case TASK -> "TASK_RUN";
            case WORKFLOW -> "WORKFLOW_RUN";
            case JOB -> "JOB_RUN";
        };
    }

    private String requireSegment(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        PlainPathSegmentValidator.requirePlainSegment(value, label);
        return value.trim();
    }
}
