package io.mindspice.magenta2.avatar.dashboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.orchestration.runtime.Project;
import io.mindspice.magenta2.ai.orchestration.runtime.ProjectService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceOwnerType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProjectArtifactService {
    private static final String ARTIFACT_ROOT = ".magenta/project";
    private static final Map<String, ArtifactSpec> SPECS = Map.of(
        "goals", new ArtifactSpec("goals", "Goals", ARTIFACT_ROOT + "/goals.json", "goals"),
        "materials", new ArtifactSpec("materials", "Materials", ARTIFACT_ROOT + "/materials.json", "materials"),
        "contacts", new ArtifactSpec("contacts", "Contacts", ARTIFACT_ROOT + "/contacts.json", "contacts"),
        "blockers", new ArtifactSpec("blockers", "Blockers", ARTIFACT_ROOT + "/blockers.json", "blockers"),
        "next-actions", new ArtifactSpec("next-actions", "Next Actions", ARTIFACT_ROOT + "/next-actions.json", "nextActions"),
        "progress", new ArtifactSpec("progress", "Progress", ARTIFACT_ROOT + "/progress.json", "progress")
    );

    private final ProjectService projectService;
    private final WorkAreaService workAreaService;
    private final WorkAreaExplorerService explorerService;
    private final OutputArtifactService outputArtifactService;
    private final ObjectMapper objectMapper;

    public ProjectArtifactService(
        ProjectService projectService,
        WorkAreaService workAreaService,
        WorkAreaExplorerService explorerService,
        OutputArtifactService outputArtifactService,
        ObjectMapper objectMapper
    ) {
        this.projectService = projectService;
        this.workAreaService = workAreaService;
        this.explorerService = explorerService;
        this.outputArtifactService = outputArtifactService;
        this.objectMapper = objectMapper;
    }

    public DashboardProjectContextView context(String projectId) {
        if (!StringUtils.hasText(projectId)) {
            return missing("Project binding is required.");
        }
        Project project;
        try {
            project = projectService.getProject(projectId);
        } catch (RuntimeException exception) {
            return missing("Project binding is unavailable: " + projectId);
        }
        List<DashboardProjectArtifact> artifacts = SPECS.values().stream()
            .map(spec -> artifact(project, spec))
            .toList();
        return new DashboardProjectContextView(
            project,
            StringUtils.hasText(project.gitRepoUrl()),
            "projects/" + project.id() + "/" + ARTIFACT_ROOT,
            null,
            artifacts,
            projectNotes(project),
            outputs(project.id())
        );
    }

    public DashboardProjectArtifact updateArtifact(String projectId, String artifactType, String content) {
        ArtifactSpec spec = requireSpec(artifactType);
        Project project = projectService.getProject(projectId);
        Map<String, Object> parsed = parseAndValidate(spec, content);
        writeArtifact(project, spec, parsed);
        return artifact(project, spec);
    }

    public WorkAreaExplorerService.FilePreview readProjectFile(String projectId, String path) {
        Project project = projectService.getProject(projectId);
        String normalizedPath = normalizeProjectNotePath(path);
        return explorerService.previewOwnerRoot(WorkspaceOwnerType.PROJECT, project.id(), project.name(), normalizedPath);
    }

    public WorkAreaExplorerService.FilePreview saveProjectFile(String projectId, String path, String content) {
        Project project = projectService.getProject(projectId);
        String normalizedPath = normalizeProjectNotePath(path);
        return explorerService.saveTextOwnerRoot(
            WorkspaceOwnerType.PROJECT,
            project.id(),
            project.name(),
            normalizedPath,
            content
        );
    }

    private DashboardProjectContextView missing(String message) {
        return new DashboardProjectContextView(null, false, null, message, List.of(), List.of(), List.of());
    }

    private DashboardProjectArtifact artifact(Project project, ArtifactSpec spec) {
        try {
            Map<String, Object> json = ensureArtifact(project, spec);
            return new DashboardProjectArtifact(
                spec.type(),
                spec.title(),
                spec.path(),
                summarize(spec, json),
                progressStatus(spec, json),
                null
            );
        } catch (RuntimeException exception) {
            return new DashboardProjectArtifact(
                spec.type(),
                spec.title(),
                spec.path(),
                List.of(),
                "invalid",
                exception.getMessage()
            );
        }
    }

    private Map<String, Object> ensureArtifact(Project project, ArtifactSpec spec) {
        Path file = artifactPath(project, spec);
        try {
            if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                writeArtifact(project, spec, defaultPayload(spec));
            }
            return parseAndValidate(spec, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read project artifact " + spec.path(), exception);
        }
    }

    private void writeArtifact(Project project, ArtifactSpec spec, Map<String, Object> payload) {
        Path file = artifactPath(project, spec);
        try {
            Files.writeString(
                file,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload) + System.lineSeparator(),
                StandardCharsets.UTF_8
            );
        } catch (IOException exception) {
            throw new IllegalStateException("failed to write project artifact " + spec.path(), exception);
        }
    }

    private Path artifactPath(Project project, ArtifactSpec spec) {
        Path root = projectRoot(project);
        Path file = root.resolve(spec.path()).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("project artifact path escapes project root");
        }
        Path parent = file.getParent();
        try {
            ensureArtifactParent(root, parent == null ? root : parent);
            Path realParent = (parent == null ? root : parent.toRealPath(LinkOption.NOFOLLOW_LINKS));
            if (!realParent.startsWith(root)) {
                throw new IllegalArgumentException("project artifact path escapes project root");
            }
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(file)) {
                    throw new IllegalArgumentException("symbolic links are not allowed in project artifact paths");
                }
                Path realFile = file.toRealPath(LinkOption.NOFOLLOW_LINKS);
                if (!realFile.startsWith(root)) {
                    throw new IllegalArgumentException("project artifact path escapes project root");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("project artifact path cannot be resolved: " + spec.path(), exception);
        }
        return file;
    }

    private Path projectRoot(Project project) {
        try {
            return workAreaService.ownerRoot(WorkspaceOwnerType.PROJECT, project.id()).toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException exception) {
            throw new IllegalArgumentException("project root cannot be resolved: " + project.id(), exception);
        }
    }

    private void ensureArtifactParent(Path root, Path parent) throws IOException {
        Path normalized = parent.normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("project artifact path escapes project root");
        }
        Path current = root;
        for (Path segment : root.relativize(normalized)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) {
                    throw new IllegalArgumentException("symbolic links are not allowed in project artifact paths");
                }
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IllegalArgumentException("project artifact parent is not a directory");
                }
            } else {
                Files.createDirectory(current);
            }
        }
    }

    private Map<String, Object> parseAndValidate(ArtifactSpec spec, String content) {
        Map<String, Object> json;
        try {
            json = objectMapper.readValue(
                StringUtils.hasText(content) ? content : "{}",
                new TypeReference<LinkedHashMap<String, Object>>() {}
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(spec.title() + " artifact is not valid JSON", exception);
        }
        Object value = json.get(spec.arrayKey());
        if ("progress".equals(spec.type())) {
            if (!(value instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Progress artifact must contain object field progress");
            }
        } else if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException(spec.title() + " artifact must contain array field " + spec.arrayKey());
        }
        return json;
    }

    private Map<String, Object> defaultPayload(ArtifactSpec spec) {
        if ("progress".equals(spec.type())) {
            return Map.of("progress", Map.of("status", "planning", "percent", 0, "notes", ""));
        }
        return Map.of(spec.arrayKey(), List.of());
    }

    private List<String> summarize(ArtifactSpec spec, Map<String, Object> json) {
        if ("progress".equals(spec.type())) {
            Object progress = json.get("progress");
            if (progress instanceof Map<?, ?> map) {
                Object status = map.get("status");
                Object percent = map.get("percent");
                return List.of((status == null ? "planning" : status.toString()) + " / "
                    + (percent == null ? "0" : percent.toString()) + "%");
            }
            return List.of("planning / 0%");
        }
        Object value = json.get(spec.arrayKey());
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        return list.stream()
            .limit(5)
            .map(this::summaryText)
            .toList();
    }

    private String progressStatus(ArtifactSpec spec, Map<String, Object> json) {
        if (!"progress".equals(spec.type())) {
            Object value = json.get(spec.arrayKey());
            return value instanceof List<?> list ? Integer.toString(list.size()) : "0";
        }
        Object progress = json.get("progress");
        if (progress instanceof Map<?, ?> map && map.get("status") != null) {
            return map.get("status").toString();
        }
        return "planning";
    }

    private String summaryText(Object item) {
        if (item instanceof Map<?, ?> map) {
            for (String key : List.of("title", "name", "action", "contact", "status")) {
                Object value = map.get(key);
                if (value != null && StringUtils.hasText(value.toString())) {
                    return value.toString();
                }
            }
        }
        return item == null ? "" : item.toString();
    }

    private List<DashboardFileNote> projectNotes(Project project) {
        try {
            WorkAreaExplorerService.DirectoryListing listing = explorerService.listOwnerRoot(
                WorkspaceOwnerType.PROJECT,
                project.id(),
                project.name(),
                ARTIFACT_ROOT
            );
            return listing.entries().stream()
                .filter(WorkAreaExplorerService.Entry::regularFile)
                .filter(entry -> entry.path().endsWith(".md") || entry.tags().stream().anyMatch(tag -> "note".equals(tag.slug())))
                .map(entry -> new DashboardFileNote(
                    "project",
                    project.name(),
                    project.id(),
                    entry.path(),
                    entry.name(),
                    entry.sizeLabel(),
                    entry.tags().stream().map(tag -> tag.slug()).toList(),
                    entry.modifiedAt(),
                    entry.canView(),
                    entry.path().endsWith(".md")
                ))
                .toList();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private List<RunOutputArtifact> outputs(String projectId) {
        try {
            return outputArtifactService.query(OutputArtifactQuery.of(null, null, projectId, null, null, null, null, 10));
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private ArtifactSpec requireSpec(String type) {
        ArtifactSpec spec = SPECS.get(type);
        if (spec == null) {
            throw new IllegalArgumentException("unknown project artifact type: " + type);
        }
        return spec;
    }

    private String normalizeProjectNotePath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new IllegalArgumentException("project note path must stay under " + ARTIFACT_ROOT);
        }
        String text = path.trim().replace('\\', '/');
        if (text.matches("^[A-Za-z]:/.*")) {
            throw new IllegalArgumentException("absolute project note paths are not allowed");
        }
        Path normalized = Path.of(text).normalize();
        if (normalized.isAbsolute()) {
            throw new IllegalArgumentException("absolute project note paths are not allowed");
        }
        Path requiredRoot = Path.of(ARTIFACT_ROOT);
        if (!normalized.startsWith(requiredRoot) || normalized.equals(requiredRoot)) {
            throw new IllegalArgumentException("project note path must stay under " + ARTIFACT_ROOT);
        }
        return normalized.toString().replace('\\', '/');
    }

    private record ArtifactSpec(String type, String title, String path, String arrayKey) {
    }
}
