package io.mindspice.magenta2.ai.orchestration.workspaces;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutputArtifactServiceAttributionTest {

    @TempDir
    Path tempDir;

    @Test
    void materializePreservesAttributionContext() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("data")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("outputs"));
        service.materialize(
            "run-1",
            "plan-1",
            "summary",
            PlanFieldType.STRING,
            "done",
            outputDir,
            new OutputArtifactContext("agent-1", "job-1", "project-1", "workspace-1", "TASK_RUN")
        );

        RunOutputArtifact artifact = repository.findArtifactsByRunId("run-1").get(0);
        assertThat(artifact.agentId()).isEqualTo("agent-1");
        assertThat(artifact.jobId()).isEqualTo("job-1");
        assertThat(artifact.projectId()).isEqualTo("project-1");
        assertThat(artifact.workspaceId()).isEqualTo("workspace-1");
        assertThat(artifact.runType()).isEqualTo("TASK_RUN");
    }

    // ════════════════════════════════════════════════════════════════
    //  Phase 2: Container path resolution
    // ════════════════════════════════════════════════════════════════

    @Test
    void containerOutputPathResolvesToRunOutputDirectory() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("root")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("outputs"));
        // Create a file at the output dir as if the container wrote it to /output/
        Files.writeString(outputDir.resolve("container-result.txt"), "container output");

        // Materialize with /output/container-result.txt as the value
        RunOutputArtifact artifact = service.materialize(
            "run-1",
            "plan-1",
            "result",
            PlanFieldType.FILE_PATH,
            "/output/container-result.txt",
            outputDir,
            OutputArtifactContext.EMPTY
        );

        assertThat(artifact.fileName()).isEqualTo("container-result.txt");
        assertThat(artifact.artifactType()).isEqualTo("file_path");
        assertThat(Files.exists(Path.of(artifact.filePath()))).isTrue();
        // When source == dest (file already in output dir), filename is preserved
        assertThat(artifact.filePath()).contains("container-result.txt");
    }

    @Test
    void runScopedContainerOutputPathStripsRunDirectoryPrefix() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("root-run-scoped")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("my-task-run-123"));
        Files.writeString(outputDir.resolve("result.json"), "{\"ok\":true}");

        RunOutputArtifact artifact = service.materialize(
            "run-123",
            "plan-123",
            "result",
            PlanFieldType.FILE_PATH,
            "/output/my-task-run-123/result.json",
            outputDir,
            OutputArtifactContext.EMPTY
        );

        assertThat(artifact.fileName()).isEqualTo("result.json");
        assertThat(Path.of(artifact.filePath())).isEqualTo(outputDir.resolve("result.json"));
    }

    @Test
    void bareFilenameResolvesRelativeToOutputDirectory() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("root2")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("outputs2"));
        Files.writeString(outputDir.resolve("hello.txt"), "hello world");

        // Materialize with bare filename (model might report just "hello.txt")
        RunOutputArtifact artifact = service.materialize(
            "run-2",
            "plan-2",
            "greeting",
            PlanFieldType.FILE_PATH,
            "hello.txt",
            outputDir,
            OutputArtifactContext.EMPTY
        );

        assertThat(artifact.fileName()).isEqualTo("hello.txt");
        assertThat(Files.exists(Path.of(artifact.filePath()))).isTrue();
    }

    @Test
    void rejectsContainerOutputPathEscape() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("root3")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("outputs3"));

        assertThatThrownBy(() ->
            service.materialize(
                "run-3",
                "plan-3",
                "bad",
                PlanFieldType.FILE_PATH,
                "/output/../../../etc/passwd",
                outputDir,
                OutputArtifactContext.EMPTY
            )
        ).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("escapes output directory");
    }

    @Test
    void discoversLooseArtifactsInOutputDirectory() throws Exception {
        JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource("jdbc:sqlite::memory:", true));
        WorkspaceRepository repository = new WorkspaceRepository(jdbc);
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, Files.createDirectories(tempDir.resolve("root4")), null, null)
        );
        OutputArtifactService service = new OutputArtifactService(
            repository,
            directoryService,
            new ObjectMapper().findAndRegisterModules()
        );

        Path outputDir = Files.createDirectories(tempDir.resolve("outputs4"));
        // Create loose files that weren't registered as outputs
        Files.writeString(outputDir.resolve("notes.md"), "# Notes");
        Files.writeString(outputDir.resolve("data.json"), "{\"key\": 1}");

        int discovered = service.discoverLooseArtifacts(
            "run-4", "plan-4", outputDir, OutputArtifactContext.EMPTY);

        assertThat(discovered).isEqualTo(2);

        List<RunOutputArtifact> artifacts = repository.findArtifactsByRunId("run-4");
        assertThat(artifacts).hasSize(2);
        assertThat(artifacts).extracting(RunOutputArtifact::fileName)
            .containsExactlyInAnyOrder("notes.md", "data.json");
    }
}
