package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactQuery;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@RestController
public class OutputController {
    private static final long MAX_CONTENT_BYTES = 10 * 1024 * 1024; // 10 MB

    private final OutputArtifactService outputArtifactService;
    private final JobService jobService;

    public OutputController(OutputArtifactService outputArtifactService, JobService jobService) {
        this.outputArtifactService = outputArtifactService;
        this.jobService = jobService;
    }

    @GetMapping("/api/outputs")
    public List<RunOutputArtifact> query(@RequestParam(required = false) String agentId,
                                         @RequestParam(required = false) String jobId,
                                         @RequestParam(required = false) String projectId,
                                         @RequestParam(required = false) String runId,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) Integer limit) {
        OutputArtifactQuery query = OutputArtifactQuery.of(
            agentId,
            jobId,
            projectId,
            null,
            runId,
            null,
            type,
            limit
        );
        List<RunOutputArtifact> direct = outputArtifactService.query(query);
        if (!direct.isEmpty() || StringUtils.hasText(runId)) {
            return direct;
        }
        if (StringUtils.hasText(jobId)) {
            try {
                return artifactsForJobs(List.of(jobService.getDefinition(jobId)), type, limit);
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
        }
        if (StringUtils.hasText(agentId) || StringUtils.hasText(projectId)) {
            return artifactsForJobs(jobService.listDefinitions(agentId, projectId, null), type, limit);
        }
        return direct;
    }

    @GetMapping("/api/outputs/{artifactId}/content")
    public ResponseEntity<?> content(@PathVariable String artifactId) {
        try {
            RunOutputArtifact artifact = outputArtifactService.getArtifact(artifactId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("artifactId", artifact.id());
            result.put("outputName", artifact.outputName());
            result.put("artifactType", artifact.artifactType());
            result.put("fileName", artifact.fileName());
            result.put("createdAt", artifact.createdAt() != null ? artifact.createdAt().toString() : null);

            // Load text content where safe (text, json, markdown)
            String type = artifact.artifactType();
            if ("text".equals(type) || "json".equals(type) || "user_message".equals(type)) {
                try {
                    String content = outputArtifactService.loadContent(artifactId, MAX_CONTENT_BYTES);
                    result.put("content", content);
                    result.put("contentTruncated", content.length() >= MAX_CONTENT_BYTES);
                } catch (IllegalArgumentException | IOException e) {
                    result.put("contentError", e.getMessage());
                }
            } else {
                result.put("contentAvailable", false);
                result.put("contentHint", "Use /api/outputs/" + artifactId + "/download for binary files");
            }
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/outputs/{artifactId}/download")
    public ResponseEntity<?> download(@PathVariable String artifactId) {
        try {
            RunOutputArtifact artifact = outputArtifactService.getArtifact(artifactId);
            String filePath = artifact.filePath();
            if (!StringUtils.hasText(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Artifact has no file path"));
            }
            Path path = Path.of(filePath).normalize().toRealPath();

            // Path confinement
            if (!path.startsWith(outputArtifactService.dataRoot())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Artifact path escapes data root"));
            }
            if (Files.isDirectory(path)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Artifact is a directory"));
            }
            long size = Files.size(path);
            if (size > MAX_CONTENT_BYTES) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Artifact file too large: " + size + " bytes"));
            }

            String fileName = artifact.fileName() != null ? artifact.fileName() : path.getFileName().toString();
            MediaType mediaType = resolveMediaType(fileName);
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(path));

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(fileName).build().toString())
                .contentType(mediaType)
                .contentLength(size)
                .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to read artifact: " + e.getMessage()));
        }
    }

    private MediaType resolveMediaType(String fileName) {
        if (fileName == null) return MediaType.APPLICATION_OCTET_STREAM;
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (lower.endsWith(".md")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN;
        if (lower.endsWith(".csv")) return MediaType.valueOf("text/csv");
        if (lower.endsWith(".html")) return MediaType.TEXT_HTML;
        if (lower.endsWith(".xml")) return MediaType.APPLICATION_XML;
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF;
        if (lower.endsWith(".zip")) return MediaType.valueOf("application/zip");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private List<RunOutputArtifact> artifactsForJobs(List<JobDefinition> jobs, String type, Integer limit) {
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 200));
        List<RunOutputArtifact> artifacts = new ArrayList<>();
        for (JobDefinition job : jobs) {
            for (String runId : jobService.outputRunIds(job.id())) {
                artifacts.addAll(outputArtifactService.query(runId, null, type, max));
                if (artifacts.size() >= max) {
                    return artifacts.subList(0, max);
                }
            }
        }
        return artifacts;
    }
}
