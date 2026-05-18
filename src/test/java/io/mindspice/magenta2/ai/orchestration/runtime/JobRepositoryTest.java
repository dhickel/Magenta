package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRun;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRunStatus;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItem;
import io.mindspice.magenta2.ai.orchestration.runtime.JobWorkItemType;
import io.mindspice.magenta2.ai.orchestration.runtime.JobRecurrence;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobRepositoryTest {

    @Test
    void savesAndFindsJobDefinition() {
        JobRepository repo = repository();
        JobDefinition def = jobDef("job-1", "Test Job", List.of(
            planItem("step1", "plan-1", 0)
        ));
        repo.saveDefinition(def);

        JobDefinition found = repo.findDefinition("job-1").orElseThrow();
        assertThat(found.title()).isEqualTo("Test Job");
        assertThat(found.items()).hasSize(1);
        assertThat(found.items().get(0).key()).isEqualTo("step1");
    }

    @Test
    void listsAllDefinitions() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-a", "A", List.of(planItem("a1", "plan-1", 0))));
        repo.saveDefinition(jobDef("job-b", "B", List.of(planItem("b1", "plan-2", 0))));

        List<JobDefinition> all = repo.findAllDefinitions();
        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all).extracting(JobDefinition::title).contains("A", "B");
    }

    @Test
    void savesAndFindsJobRun() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-2", "Run Job", List.of(planItem("s1", "plan-3", 0))));

        JobRun run = new JobRun("run-1", "job-2", JobRunStatus.QUEUED,
            List.of(), "/ws/job-2", "/out/job-2", null, null,
            Instant.now(), Instant.now(), null, null);
        repo.saveRun(run);

        JobRun found = repo.findRun("run-1").orElseThrow();
        assertThat(found.status()).isEqualTo(JobRunStatus.QUEUED);
        assertThat(found.jobId()).isEqualTo("job-2");
    }

    @Test
    void findsRunsByJobId() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-3", "Multi Run", List.of(planItem("s1", "plan-4", 0))));

        repo.saveRun(new JobRun("run-a", "job-3", JobRunStatus.COMPLETED,
            List.of(), null, null, null, null,
            Instant.now(), Instant.now(), null, Instant.now()));
        repo.saveRun(new JobRun("run-b", "job-3", JobRunStatus.FAILED,
            List.of(), null, null, null, "error",
            Instant.now(), Instant.now(), null, Instant.now()));

        List<JobRun> runs = repo.findRunsByJobId("job-3");
        assertThat(runs).hasSize(2);
    }

    @Test
    void deleteDefinitionCascadesToRuns() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-4", "Delete Me", List.of(planItem("s1", "plan-5", 0))));
        repo.saveRun(new JobRun("run-x", "job-4", JobRunStatus.QUEUED,
            List.of(), null, null, null, null,
            Instant.now(), Instant.now(), null, null));

        repo.deleteDefinition("job-4");
        assertThat(repo.findDefinition("job-4")).isEmpty();
        assertThat(repo.findRun("run-x")).isEmpty();
    }

    @Test
    void savesAndFindsRecurrence() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-5", "Recurring", List.of(planItem("s1", "plan-6", 0))));

        JobRecurrence rec = new JobRecurrence(
            "rec-1", "job-5", "0 9 * * *", "UTC",
            Instant.now().plusSeconds(3600), true,
            Instant.now(), Instant.now()
        );
        repo.saveRecurrence(rec);

        JobRecurrence found = repo.findRecurrence("job-5").orElseThrow();
        assertThat(found.cronExpression()).isEqualTo("0 9 * * *");
        assertThat(found.enabled()).isTrue();
    }

    @Test
    void findsDueRecurrences() {
        JobRepository repo = repository();
        repo.saveDefinition(jobDef("job-6", "Due Soon", List.of(planItem("s1", "plan-7", 0))));

        Instant past = Instant.now().minusSeconds(60);
        Instant future = Instant.now().plusSeconds(86400);

        repo.saveRecurrence(new JobRecurrence(
            "rec-2", "job-6", "*/5 * * * *", "UTC",
            past, true,
            Instant.now(), Instant.now()
        ));

        List<JobRecurrence> due = repo.findDueRecurrences(Instant.now().plusSeconds(10));
        assertThat(due).hasSize(1);
        assertThat(due.get(0).jobId()).isEqualTo("job-6");
    }

    // ── Helpers ──

    private JobDefinition jobDef(String id, String title, List<JobWorkItem> items) {
        return new JobDefinition(id, title, "Summary", items,
            null, null, null, Instant.now(), Instant.now());
    }

    private JobWorkItem planItem(String key, String planId, int order) {
        return new JobWorkItem(key, JobWorkItemType.PLAN, planId, null,
            Map.of(), order, null, null);
    }

    private JobRepository repository() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new JobRepository(new JdbcTemplate(ds), mapper);
    }
}
