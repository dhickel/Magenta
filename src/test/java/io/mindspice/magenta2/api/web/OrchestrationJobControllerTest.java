package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.List;

import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentRequest;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJob;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobItem;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationJobService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRuntimeRepository;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationStatus;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrchestrationJobControllerTest {

    @Test
    void createRejectsBlankTitle() {
        OrchestrationJobController controller = new OrchestrationJobController(stubService(), null);
        OrchestrationJob blankTitle = new OrchestrationJob(
            null, "agent-1", "  ", "summary", "qwen3", "ws-1",
            OrchestrationStatus.QUEUED, null, null
        );

        assertThatThrownBy(() -> controller.create(blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void createRejectsBlankOwnerAgentId() {
        OrchestrationJobController controller = new OrchestrationJobController(stubService(), null);
        OrchestrationJob blankOwner = new OrchestrationJob(
            null, "  ", "Test Job", "summary", null, null,
            OrchestrationStatus.QUEUED, null, null
        );

        assertThatThrownBy(() -> controller.create(blankOwner))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void listRejectsBlankAgentId() {
        OrchestrationJobController controller = new OrchestrationJobController(stubService(), null);

        assertThatThrownBy(() -> controller.list("  "))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(exception.getReason()).contains("agentId");
            });
    }

    @Test
    void getReturns404ForMissingId() {
        OrchestrationJobController controller = new OrchestrationJobController(missingService(), null);

        assertThatThrownBy(() -> controller.get("non-existent"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void createSucceedsWithValidJob() {
        OrchestrationJobController controller = new OrchestrationJobController(stubService(), null);
        OrchestrationJob valid = new OrchestrationJob(
            null, "agent-1", "Valid Job", "summary", "qwen3", "ws-1",
            OrchestrationStatus.QUEUED, null, null
        );

        OrchestrationJob result = controller.create(valid);

        assertThat(result.title()).isEqualTo("Valid Job");
        assertThat(result.id()).isEqualTo("job-1");
    }

    @Test
    void addItemRejectsInvalidItem() {
        OrchestrationJobController controller = new OrchestrationJobController(stubService(), null);
        OrchestrationJobItem invalidItem = new OrchestrationJobItem(
            null, "job-1", 1, null, "task-1", null,
            null, 0, 0, false, java.util.Map.of(), null, null
        );

        assertThatThrownBy(() -> controller.addItem("job-1", invalidItem))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    private static OrchestrationJobService stubService() {
        return new OrchestrationJobService(null, null, null) {
            @Override
            public List<OrchestrationJob> jobs(String agentId) {
                return List.of(new OrchestrationJob(
                    "job-1", agentId, "Test Job", "summary", "qwen3", "ws-1",
                    OrchestrationStatus.QUEUED, Instant.EPOCH, Instant.EPOCH
                ));
            }

            @Override
            public OrchestrationJob save(OrchestrationJob job) {
                if (job.title() == null || job.title().isBlank()) {
                    throw new IllegalArgumentException("title is required");
                }
                if (job.ownerAgentId() == null || job.ownerAgentId().isBlank()) {
                    throw new IllegalArgumentException("ownerAgentId is required");
                }
                return new OrchestrationJob(
                    "job-1", job.ownerAgentId(), job.title(), job.summary(),
                    job.defaultModel(), job.workspaceId(), OrchestrationStatus.QUEUED,
                    Instant.EPOCH, Instant.EPOCH
                );
            }

            @Override
            public OrchestrationJob get(String jobId) {
                if (!"job-1".equals(jobId)) {
                    throw new IllegalStateException("job not found: " + jobId);
                }
                return jobs("agent-1").get(0);
            }

            @Override
            public OrchestrationJobItem saveItem(String jobId, OrchestrationJobItem item) {
                if (item.itemType() == null) {
                    throw new IllegalArgumentException("itemType is required");
                }
                return item;
            }
        };
    }

    private static OrchestrationJobService missingService() {
        return new OrchestrationJobService(null, null, null) {
            @Override
            public OrchestrationJob get(String jobId) {
                throw new IllegalStateException("job not found: " + jobId);
            }
        };
    }
}
