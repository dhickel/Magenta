package io.mindspice.magenta2.api.web;

import java.time.Instant;
import java.util.List;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentProfileControllerTest {

    @Test
    void createRejectsBlankName() {
        AgentProfileController controller = new AgentProfileController(stubService(), null);
        AgentProfile blankName = new AgentProfile(
            null, "  ", AgentProfileStatus.ACTIVE, "qwen3", null, List.of(), List.of(), true, null, null
        );

        assertThatThrownBy(() -> controller.create(blankName))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void getReturns404ForMissingId() {
        AgentProfileController controller = new AgentProfileController(missingService(), null);

        assertThatThrownBy(() -> controller.get("non-existent"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void updateReturns404ForMissingId() {
        AgentProfileController controller = new AgentProfileController(missingService(), null);
        AgentProfile profile = new AgentProfile(
            "non-existent", "Test", AgentProfileStatus.ACTIVE, "qwen3", null,
            List.of(), List.of(), true, null, null
        );

        assertThatThrownBy(() -> controller.update("non-existent", profile))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void createSucceedsWithValidProfile() {
        AgentProfileController controller = new AgentProfileController(stubService(), null);
        AgentProfile valid = new AgentProfile(
            null, "Valid Agent", AgentProfileStatus.ACTIVE, "qwen3", null,
            List.of(), List.of(), true, null, null
        );

        AgentProfile result = controller.create(valid);

        assertThat(result.name()).isEqualTo("Valid Agent");
        assertThat(result.id()).isEqualTo("agent-1");
    }

    @Test
    void listReturnsProfiles() {
        AgentProfileController controller = new AgentProfileController(stubService(), null);

        List<AgentProfile> result = controller.list();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Default Agent");
    }

    private static AgentProfileService stubService() {
        return new AgentProfileService(null, null, null) {
            @Override
            public List<AgentProfile> list() {
                return List.of(new AgentProfile(
                    "agent-1", "Default Agent", AgentProfileStatus.ACTIVE, "qwen3",
                    null, List.of(), List.of(), true, Instant.EPOCH, Instant.EPOCH
                ));
            }

            @Override
            public AgentProfile create(AgentProfile profile) {
                if (profile.name() == null || profile.name().isBlank()) {
                    throw new IllegalArgumentException("name is required");
                }
                return new AgentProfile(
                    "agent-1", profile.name(), profile.status(), profile.defaultModel(),
                    profile.systemPrompt(), profile.approvedTools(), profile.allowedShellCommands(),
                    profile.directLineEnabled(), Instant.EPOCH, Instant.EPOCH
                );
            }

            @Override
            public AgentProfile get(String id) {
                throw new IllegalStateException("agent not found: " + id);
            }
        };
    }

    private static AgentProfileService missingService() {
        return new AgentProfileService(null, null, null) {
            @Override
            public AgentProfile get(String id) {
                throw new IllegalStateException("agent not found: " + id);
            }

            @Override
            public AgentProfile update(String id, AgentProfile profile) {
                throw new IllegalStateException("agent not found: " + id);
            }
        };
    }
}
