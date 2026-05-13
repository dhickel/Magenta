package io.mindspice.magenta2.ai.orchestration.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobServiceTest {

    @TempDir
    Path tempDir;

    private JobService jobService;
    private JobRepository jobRepository;

    @BeforeEach
    void setUp() throws IOException {
        Path dataRoot = Files.createDirectories(tempDir.resolve("data"));
        AiConfig aiConfig = new AiConfig(null, null, null, 10, dataRoot, Map.of(), Map.of());
        WorkspaceDirectoryService wsDirService = new WorkspaceDirectoryService(aiConfig);

        jobRepository = repository();
        jobService = new JobService(jobRepository, wsDirService, null, null);
    }

    @Test
    void createAndRetrieveJob() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "My Job", List.of(planItem("step1", "plan-1", 0)))
        );
        assertThat(def.id()).isNotNull();
        JobDefinition found = jobService.getDefinition(def.id());
        assertThat(found.title()).isEqualTo("My Job");
    }

    @Test
    void draftJobCanStartWithoutWorkItems() {
        JobDefinition definition = jobService.saveDefinition(jobDef("j1", "Empty", List.of()));

        assertThat(definition.items()).isEmpty();
        assertThat(definition.status()).isEqualTo("DRAFT");
    }

    @Test
    void planItemRequiresPlanId() {
        JobWorkItem badItem = new JobWorkItem("k", JobWorkItemType.PLAN, null, null,
            Map.of(), 0, null, null);
        assertThatThrownBy(() -> jobService.saveDefinition(
            jobDef("j2", "Bad", List.of(badItem))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no planId");
    }

    @Test
    void startRunCreatesRunWithWorkItems() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Runner", List.of(
                planItem("s1", "plan-1", 0),
                planItem("s2", "plan-2", 1)
            ))
        );

        JobRun run = jobService.startRun(def.id());
        assertThat(run.status()).isEqualTo(JobRunStatus.QUEUED);
        assertThat(run.workItemRuns()).hasSize(2);
        assertThat(run.workItemRuns().get(0).key()).isEqualTo("s1");
        assertThat(run.workItemRuns().get(1).key()).isEqualTo("s2");
    }

    @Test
    void startRunAllocatesWorkspaceAndOutputDir() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Workspace Job", List.of(planItem("s1", "plan-1", 0)))
        );

        JobRun run = jobService.startRun(def.id());
        assertThat(run.workspacePath()).contains(def.id());
        assertThat(run.workspacePath()).contains("workspace");
        assertThat(run.outputDir()).contains(def.id());
        assertThat(run.outputDir()).contains("outputs");
    }

    @Test
    void updateWorkItemComputesProgress() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Progress Job", List.of(
                planItem("a", "plan-1", 0),
                planItem("b", "plan-2", 1)
            ))
        );

        JobRun run = jobService.startRun(def.id());
        run = jobService.markRunning(run.id());
        assertThat(run.status()).isEqualTo(JobRunStatus.RUNNING);

        // Complete first item
        run = jobService.updateWorkItemRun(run.id(), "a", "COMPLETED", "pr-1",
            Map.of("out", "val"), null);
        assertThat(run.progress()).isEqualTo(0.5);

        // Complete second item → job run completes
        run = jobService.updateWorkItemRun(run.id(), "b", "COMPLETED", "pr-2",
            Map.of("out", "val"), null);
        assertThat(run.status()).isEqualTo(JobRunStatus.COMPLETED);
        assertThat(run.progress()).isEqualTo(1.0);
    }

    @Test
    void failedWorkItemResultsInFailedJob() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Fail Job", List.of(
                planItem("a", "plan-1", 0),
                planItem("b", "plan-2", 1)
            ))
        );

        JobRun run = jobService.startRun(def.id());
        run = jobService.markRunning(run.id());
        run = jobService.updateWorkItemRun(run.id(), "a", "COMPLETED", "pr-1",
            Map.of(), null);
        run = jobService.updateWorkItemRun(run.id(), "b", "FAILED", "pr-2",
            Map.of(), "error occurred");

        assertThat(run.status()).isEqualTo(JobRunStatus.FAILED);
    }

    @Test
    void recurrenceCreatesNewRunOnFire() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Recurring Job", List.of(planItem("s1", "plan-1", 0)))
        );

        Instant past = Instant.now().minusSeconds(3600);
        jobService.setRecurrence(def.id(), "0 9 * * *", "UTC", past);

        List<JobRun> newRuns = jobService.fireDueRecurrences(Instant.now().plusSeconds(10));
        assertThat(newRuns).hasSize(1);
        assertThat(newRuns.get(0).jobId()).isEqualTo(def.id());
        assertThat(newRuns.get(0).status()).isEqualTo(JobRunStatus.QUEUED);
    }

    @Test
    void cancelRunTransitionsToCancelled() {
        JobDefinition def = jobService.saveDefinition(
            jobDef(null, "Cancel Job", List.of(planItem("s1", "plan-1", 0)))
        );

        JobRun run = jobService.startRun(def.id());
        run = jobService.markRunning(run.id());

        JobRun cancelled = jobService.cancelRun(run.id());
        assertThat(cancelled.status()).isEqualTo(JobRunStatus.CANCELLED);
    }

    // ── Helpers ──

    private JobDefinition jobDef(String id, String title, List<JobWorkItem> items) {
        return new JobDefinition(id, title, "Summary", items,
            null, null, null, null, null);
    }

    private JobWorkItem planItem(String key, String planId, int order) {
        return new JobWorkItem(key, JobWorkItemType.PLAN, planId, null,
            Map.of(), order, null, null);
    }

    private JobRepository repository() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new JobRepository(new JdbcTemplate(ds), mapper);
    }
}
