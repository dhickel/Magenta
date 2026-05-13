package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Materializes output values into a run's output directory and persists
 * artifact metadata. Each output value is written or copied into the
 * output directory and a {@link RunOutputArtifact} row is persisted.
 *
 * <p>Materialization rules by type:
 * <ul>
 *   <li>{@code file_path} — copy the source file into the output dir</li>
 *   <li>{@code user_message} — write {@code {outputName}.md}</li>
 *   <li>{@code json} — write {@code {outputName}.json}</li>
 *   <li>{@code string/number} — write {@code {outputName}.txt}</li>
 * </ul>
 */
@Service
public class OutputArtifactService {
    private final WorkspaceRepository repository;
    private final WorkspaceDirectoryService directoryService;
    private final ObjectMapper objectMapper;

    public OutputArtifactService(WorkspaceRepository repository,
                                 WorkspaceDirectoryService directoryService,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.directoryService = directoryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Materialize a single output value into the given output directory.
     *
     * @param runId          the plan run id
     * @param planId         the plan definition id
     * @param outputName     the declared output name
     * @param outputType     the declared PlanFieldType for this output
     * @param value          the value to materialize
     * @param outputDir      the output directory path
     * @return the persisted artifact
     */
    public RunOutputArtifact materialize(String runId, String planId,
                                          String outputName, PlanFieldType outputType,
                                          Object value, Path outputDir) throws IOException {
        requireId(runId, "runId");
        requireId(planId, "planId");
        requireId(outputName, "outputName");
        if (outputType == null) {
            outputType = PlanFieldType.STRING;
        }
        if (value == null) {
            throw new IllegalArgumentException("Output value is null for output: " + outputName);
        }

        Files.createDirectories(outputDir);

        return switch (outputType) {
            case FILE_PATH -> materializeFilePath(runId, planId, outputName, value, outputDir);
            case USER_MESSAGE -> materializeUserMessage(runId, planId, outputName, value, outputDir);
            case JSON -> materializeJson(runId, planId, outputName, value, outputDir);
            case STRING, NUMBER -> materializeText(runId, planId, outputName, value, outputDir);
        };
    }

    /**
     * Materialize all output values from a completed run. Uses the declared
     * output fields from the plan snapshot to determine types.
     *
     * @return list of persisted artifacts
     */
    public List<RunOutputArtifact> materializeAll(String runId, String planId,
                                                   Map<String, Object> outputValues,
                                                   Map<String, PlanFieldType> outputTypes,
                                                   Path outputDir) throws IOException {
        List<RunOutputArtifact> artifacts = new java.util.ArrayList<>();
        for (Map.Entry<String, Object> entry : outputValues.entrySet()) {
            String name = entry.getKey();
            PlanFieldType type = outputTypes.getOrDefault(name, PlanFieldType.STRING);
            artifacts.add(materialize(runId, planId, name, type, entry.getValue(), outputDir));
        }
        return artifacts;
    }

    public List<RunOutputArtifact> artifactsForRun(String runId) {
        return repository.findArtifactsByRunId(runId);
    }

    public List<RunOutputArtifact> artifactsForPlan(String planId) {
        return repository.findArtifactsByPlanId(planId);
    }

    public List<RunOutputArtifact> query(String runId, String planId, String artifactType, Integer limit) {
        int effectiveLimit = limit == null ? 50 : limit;
        return repository.findArtifacts(runId, planId, artifactType, effectiveLimit);
    }

    // ── Type-specific materialization ──

    private RunOutputArtifact materializeFilePath(String runId, String planId,
                                                    String outputName, Object value,
                                                    Path outputDir) throws IOException {
        String sourcePathStr = value.toString();
        Path sourcePath = directoryService.resolveInputPath(
            outputDir.getParent().toString(), sourcePathStr);
        String fileName = sourcePath.getFileName().toString();
        Path destPath = outputDir.resolve(sanitize(outputName) + "_" + fileName);

        Files.copy(sourcePath, destPath, StandardCopyOption.REPLACE_EXISTING);

        return saveArtifact(runId, planId, outputName, "file_path",
            destPath.getFileName().toString(),
            destPath.toString(), null);
    }

    private RunOutputArtifact materializeUserMessage(String runId, String planId,
                                                       String outputName, Object value,
                                                       Path outputDir) throws IOException {
        String fileName = sanitize(outputName) + ".md";
        Path filePath = outputDir.resolve(fileName);
        String content = value.toString();
        Files.writeString(filePath, content);

        return saveArtifact(runId, planId, outputName, "user_message",
            fileName, filePath.toString(), null);
    }

    private RunOutputArtifact materializeJson(String runId, String planId,
                                                String outputName, Object value,
                                                Path outputDir) throws IOException {
        String fileName = sanitize(outputName) + ".json";
        Path filePath = outputDir.resolve(fileName);
        String jsonContent;
        try {
            jsonContent = value instanceof String
                ? objectMapper.writeValueAsString(
                    objectMapper.readTree((String) value))
                : objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                "Output '" + outputName + "' is not valid JSON: " + e.getMessage(), e);
        }
        Files.writeString(filePath, jsonContent);

        return saveArtifact(runId, planId, outputName, "json",
            fileName, filePath.toString(), jsonContent);
    }

    private RunOutputArtifact materializeText(String runId, String planId,
                                                String outputName, Object value,
                                                Path outputDir) throws IOException {
        String fileName = sanitize(outputName) + ".txt";
        Path filePath = outputDir.resolve(fileName);
        String content = value.toString();
        Files.writeString(filePath, content);

        return saveArtifact(runId, planId, outputName, "text",
            fileName, filePath.toString(), null);
    }

    // ── Helpers ──

    private RunOutputArtifact saveArtifact(String runId, String planId,
                                            String outputName, String artifactType,
                                            String fileName, String filePath,
                                            String contentJson) {
        return repository.saveArtifact(new RunOutputArtifact(
            UUID.randomUUID().toString(),
            runId,
            planId,
            outputName,
            artifactType,
            fileName,
            filePath,
            contentJson,
            Instant.now()
        ));
    }

    private void requireId(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9_.-]", "_").replaceAll("_+", "_");
    }
}
