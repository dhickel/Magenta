package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class WorkspaceFileMetadataService {
    private final WorkspaceFileMetadataRepository repository;
    private final WorkspaceFileActionLogRepository actionLogRepository;

    public WorkspaceFileMetadataService(
        WorkspaceFileMetadataRepository repository,
        WorkspaceFileActionLogRepository actionLogRepository
    ) {
        this.repository = repository;
        this.actionLogRepository = actionLogRepository;
    }

    public WorkspaceFileLabel ensureTag(String labelSlug, String displayName) {
        return repository.ensureLabel(labelSlug, displayName, false);
    }

    public WorkspaceFileLabel ensureTag(
        String labelSlug,
        String displayName,
        WorkspaceFileLabelTargetType targetType
    ) {
        return repository.ensureLabel(labelSlug, displayName, false, targetType);
    }

    public WorkspaceFileLabel ensureTag(
        String labelSlug,
        String displayName,
        WorkspaceFileLabelTargetType targetType,
        String description
    ) {
        return repository.ensureLabel(labelSlug, displayName, false, targetType, description);
    }

    public WorkspaceFileLabelAssignment addLabel(WorkArea workArea, String rootRelativePath, String labelSlug) {
        return addLabel(workArea, rootRelativePath, labelSlug, null);
    }

    public WorkspaceFileLabelAssignment addLabel(
        WorkArea workArea,
        String rootRelativePath,
        String labelSlug,
        WorkspaceFileLabelTargetType targetType
    ) {
        WorkspaceFileLabelAssignment assignment = repository.addLabel(
            workArea,
            rootRelativePath,
            rootRelativePath,
            labelSlug,
            targetType
        );
        actionLogRepository.record(
            workArea,
            WorkspaceFileActionType.TAG_ADD,
            rootRelativePath,
            null,
            "SUCCEEDED",
            "{\"label\":\"" + assignment.label().slug() + "\"}"
        );
        return assignment;
    }

    public int removeLabel(WorkArea workArea, String rootRelativePath, String labelSlug) {
        int removed = repository.removeLabel(workArea, rootRelativePath, labelSlug);
        actionLogRepository.record(
            workArea,
            WorkspaceFileActionType.TAG_REMOVE,
            rootRelativePath,
            null,
            "SUCCEEDED",
            "{\"label\":\"" + labelSlug + "\",\"removed\":" + removed + "}"
        );
        return removed;
    }

    public List<WorkspaceFileLabelAssignment> labelsForPath(String workspaceId, String rootRelativePath) {
        return repository.labelsForPath(workspaceId, rootRelativePath);
    }

    public void onMove(WorkArea workArea, String sourceRootRelativePath, String targetRootRelativePath) {
        repository.moveSubtree(workArea, sourceRootRelativePath, targetRootRelativePath);
    }

    public void onCopy(WorkArea workArea, String sourceRootRelativePath, String targetRootRelativePath) {
        repository.copySubtree(workArea, sourceRootRelativePath, targetRootRelativePath);
    }

    public void onDelete(WorkArea workArea, String sourceRootRelativePath) {
        repository.deleteSubtree(workArea.workspaceId(), sourceRootRelativePath);
    }

    public List<WorkspaceFileLabel> listLabelsForTarget(
        WorkspaceFileLabelTargetType targetType,
        String query,
        int limit
    ) {
        return repository.listLabelsForTarget(targetType, query, limit);
    }

    public List<WorkspaceFileLabel> listLabels(String query, int limit) {
        return repository.listLabels(query, limit);
    }
}
