package io.mindspice.magenta2.api.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRun;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactContext;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class OutputControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void querySupportsDirectAttributionFilters() throws Exception {
        Services services = services();
        OutputController controller = new OutputController(services.outputArtifactService(), services.jobService());
        Path outputDir = Files.createDirectories(services.dataRoot().resolve("outputs-direct"));
        RunOutputArtifact artifact = services.outputArtifactService().materialize(
            "run-1",
            "plan-1",
            "summary",
            PlanFieldType.STRING,
            "done",
            outputDir,
            new OutputArtifactContext(
                "agent-1", "job-1", "assignment-1", "job-run-1",
                "project-1", "workspace-1", "JOB_WORKFLOW_ITEM")
        );

        assertThat(controller.query(null, null, "assignment-1", null, null, null, null, null, null, null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());
        assertThat(controller.query(null, null, null, "job-run-1", null, null, null, null, null, null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());
        assertThat(controller.query(null, null, null, null, null, "workspace-1", null, null, null, null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());
        assertThat(controller.query(null, null, null, null, null, null, null, "plan-1", null, null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());
        assertThat(controller.query(null, null, null, null, null, null, null, null, "JOB_WORKFLOW_ITEM", null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(artifact.id());

        ResponseEntity<?> response = controller.content(artifact.id());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
            .containsEntry("jobAssignmentId", "assignment-1")
            .containsEntry("jobRunId", "job-run-1")
            .containsEntry("workspaceId", "workspace-1")
            .containsEntry("runType", "JOB_WORKFLOW_ITEM");
    }

    @Test
    void jobFallbackDoesNotMaskMissingDirectAssignmentAttribution() throws Exception {
        Services services = services();
        OutputController controller = new OutputController(services.outputArtifactService(), services.jobService());
        JobDefinition job = services.jobService().saveDefinition(new JobDefinition(
            null, "agent-1", null, null, false, "ACTIVE", "Legacy Job", "", List.of(),
            null, null, null, null, null
        ));
        JobRun run = services.jobService().startRun(job.id(), "agent-1", null, "assignment-1");
        RunOutputArtifact legacyArtifact = services.outputArtifactService().materialize(
            run.id(),
            job.id(),
            "legacy",
            PlanFieldType.STRING,
            "legacy output",
            Path.of(run.outputDir()),
            OutputArtifactContext.EMPTY
        );

        assertThat(controller.query(null, job.id(), null, null, null, null, null, null, null, null, 20))
            .extracting(RunOutputArtifact::id)
            .containsExactly(legacyArtifact.id());
        assertThat(controller.query(null, null, "assignment-1", null, null, null, null, null, null, null, 20))
            .isEmpty();
    }

    private Services services() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
            new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true)
        );
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, null, null, null, dataRoot, null, null)
        );
        WorkspaceRepository workspaceRepository = new WorkspaceRepository(jdbcTemplate);
        OutputArtifactService outputArtifactService = new OutputArtifactService(
            workspaceRepository, directoryService, mapper);
        JobService jobService = new JobService(
            new JobRepository(jdbcTemplate, mapper), directoryService, null, null);
        return new Services(jobService, outputArtifactService, dataRoot);
    }

    private record Services(
        JobService jobService,
        OutputArtifactService outputArtifactService,
        Path dataRoot
    ) {
    }
}
