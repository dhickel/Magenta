import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.plan.PlanFieldType;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactContext;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

public class Phase04OutputSeeder {
    public static void main(String[] args) throws Exception {
        String database = args[0];
        String dataRoot = args[1];
        String projectId = args[2];
        String workspaceId = args[3];
        String jobId = args[4];

        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setUrl("jdbc:sqlite:" + database + "?foreign_keys=true");

        WorkspaceRepository repository = new WorkspaceRepository(new JdbcTemplate(dataSource));
        WorkspaceDirectoryService directoryService = new WorkspaceDirectoryService(
            new AiConfig(null, "local-qwen", 10, Path.of(dataRoot), java.util.Map.of(), java.util.Map.of())
        );
        OutputArtifactService service = new OutputArtifactService(repository, directoryService, new ObjectMapper());
        Path outputDir = Files.createDirectories(Path.of(dataRoot, "phase-04-output-seed"));

        List<RunOutputArtifact> artifacts = List.of(
            service.materialize("phase04-agent-run-2", "phase04-plan", "agent browser summary", PlanFieldType.STRING,
                "Agent scoped summary for Phase 04 browser proof.", outputDir,
                new OutputArtifactContext("agent-phase04-alpha", null, null, null, "task")),
            service.materialize("phase04-project-run-2", "phase04-plan", "project browser summary", PlanFieldType.STRING,
                "Project scoped summary should not appear in agent-only mode.", outputDir,
                new OutputArtifactContext(null, null, projectId, null, "task")),
            service.materialize("phase04-job-run-2", "phase04-plan", "job browser summary", PlanFieldType.STRING,
                "Job scoped summary for Phase 04 browser proof.", outputDir,
                new OutputArtifactContext("agent-phase04-alpha", jobId, projectId, null, "job")),
            service.materialize("phase04-workarea-run-2", "phase04-plan", "work area browser summary", PlanFieldType.STRING,
                "Work Area scoped summary for Phase 04 browser proof.", outputDir,
                new OutputArtifactContext("agent-phase04-alpha", null, projectId, workspaceId, "task"))
        );

        for (RunOutputArtifact artifact : artifacts) {
            System.out.println(artifact.id() + "|" + artifact.outputName() + "|" + artifact.filePath());
        }
    }
}
