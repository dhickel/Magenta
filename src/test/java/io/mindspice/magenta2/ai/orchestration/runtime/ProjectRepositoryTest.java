package io.mindspice.magenta2.ai.orchestration.runtime;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectRepositoryTest {

    @Test
    void savesAndFindsProject() {
        ProjectRepository repo = repository();
        Project p = project("proj-1", "Alpha", "agent-1");
        repo.save(p);

        Project found = repo.findById("proj-1").orElseThrow();
        assertThat(found.name()).isEqualTo("Alpha");
        assertThat(found.ownerAgentId()).isEqualTo("agent-1");
    }

    @Test
    void findsByOwnerAgent() {
        ProjectRepository repo = repository();
        repo.save(project("proj-2", "Beta", "agent-2"));
        repo.save(project("proj-3", "Gamma", "agent-2"));
        repo.save(project("proj-4", "Delta", "agent-3"));

        List<Project> owned = repo.findByOwnerAgent("agent-2");
        assertThat(owned).hasSize(2);
        assertThat(owned).extracting(Project::name).contains("Beta", "Gamma");
    }

    @Test
    void managesMemberships() {
        ProjectRepository repo = repository();
        repo.save(project("proj-5", "Epsilon", "agent-4"));

        // Add membership
        ProjectAgentMembership m = new ProjectAgentMembership(
            UUID.randomUUID().toString(), "proj-5", "agent-5", "member", Instant.now()
        );
        repo.saveMembership(m);

        assertThat(repo.isMember("proj-5", "agent-5")).isTrue();
        assertThat(repo.isMember("proj-5", "agent-99")).isFalse();

        List<ProjectAgentMembership> members = repo.findMembershipsByProject("proj-5");
        assertThat(members).hasSize(1);
        assertThat(members.get(0).agentId()).isEqualTo("agent-5");
    }

    @Test
    void findProjectIdsByAgent() {
        ProjectRepository repo = repository();
        repo.save(project("proj-6", "Zeta", "agent-6"));
        repo.save(project("proj-7", "Eta", "agent-7"));

        repo.saveMembership(new ProjectAgentMembership(
            UUID.randomUUID().toString(), "proj-6", "agent-x", "member", Instant.now()));
        repo.saveMembership(new ProjectAgentMembership(
            UUID.randomUUID().toString(), "proj-7", "agent-x", "member", Instant.now()));

        List<String> projectIds = repo.findProjectIdsByAgent("agent-x");
        assertThat(projectIds).containsExactlyInAnyOrder("proj-6", "proj-7");
    }

    @Test
    void deletesMembership() {
        ProjectRepository repo = repository();
        repo.save(project("proj-8", "Theta", "agent-8"));

        repo.saveMembership(new ProjectAgentMembership(
            "mem-1", "proj-8", "agent-y", "member", Instant.now()));
        assertThat(repo.isMember("proj-8", "agent-y")).isTrue();

        repo.deleteMembership("proj-8", "agent-y");
        assertThat(repo.isMember("proj-8", "agent-y")).isFalse();
    }

    @Test
    void recordsAndFindsProjectEvents() {
        ProjectRepository repo = repository();
        repo.save(project("proj-9", "Iota", "agent-9"));

        repo.saveEvent(new ProjectEvent("evt-1", "proj-9", "created", "{}", Instant.now()));
        repo.saveEvent(new ProjectEvent("evt-2", "proj-9", "agent_added", "{\"agent\":\"x\"}", Instant.now()));

        List<ProjectEvent> events = repo.findEvents("proj-9");
        assertThat(events).hasSize(2);
        assertThat(events).extracting(ProjectEvent::type).contains("created", "agent_added");
    }

    @Test
    void deleteProjectCascades() {
        ProjectRepository repo = repository();
        repo.save(project("proj-10", "Kappa", "agent-10"));
        repo.saveMembership(new ProjectAgentMembership(
            "mem-2", "proj-10", "agent-z", "member", Instant.now()));
        repo.saveEvent(new ProjectEvent("evt-3", "proj-10", "note", "{}", Instant.now()));

        repo.delete("proj-10");

        assertThat(repo.findById("proj-10")).isEmpty();
        assertThat(repo.isMember("proj-10", "agent-z")).isFalse();
    }

    // ── Helpers ──

    private Project project(String id, String name, String ownerAgentId) {
        return new Project(id, name, "Description", ownerAgentId, null,
            null, null, null, Instant.now(), Instant.now());
    }

    private ProjectRepository repository() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:?foreign_keys=true", true);
        return new ProjectRepository(new JdbcTemplate(ds), new ObjectMapper());
    }
}
