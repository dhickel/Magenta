package io.mindspice.magenta2.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import io.mindspice.magenta2.ai.orchestration.runtime.AssignmentService;
import io.mindspice.magenta2.ai.orchestration.runtime.JobDefinition;
import io.mindspice.magenta2.ai.orchestration.runtime.JobService;
import io.mindspice.magenta2.ai.orchestration.runtime.WorkAssignment;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessage;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessageToType;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxMessageType;
import io.mindspice.magenta2.ai.orchestration.workflow.InboxService;
import io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactService;
import io.mindspice.magenta2.ai.orchestration.workspaces.RunOutputArtifact;
import io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceDirectoryService;
import io.mindspice.magenta2.avatar.AvatarEvent;
import io.mindspice.magenta2.avatar.AvatarRepository;
import io.mindspice.magenta2.avatar.AvatarSchemaInitializer;
import io.mindspice.magenta2.avatar.AvatarService;
import io.mindspice.magenta2.avatar.AvatarTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.web.server.ResponseStatusException;

class AvatarDashboardControllerTest {
    @TempDir
    Path tempDir;

    private AvatarService avatarService;
    private AvatarDashboardController controller;

    @BeforeEach
    void setUp() throws IOException {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
            "jdbc:sqlite::memory:?foreign_keys=true",
            true
        );
        new AvatarSchemaInitializer(dataSource).initialize();
        avatarService = new AvatarService(new AvatarRepository(new JdbcTemplate(dataSource), new ObjectMapper()));
        avatarService.appendEvent(new AvatarEvent(
            "alert-1",
            "alert.manual",
            Map.of("body", "Check inbox"),
            Instant.parse("2026-05-22T10:00:00Z")
        ));
        controller = new AvatarDashboardController(
            avatarService,
            new StubChatService(),
            new StubOutputArtifactService(tempDir),
            new StubAgentProfileService(),
            new StubJobService(),
            new EmptyAssignmentProvider(),
            new StubInboxService()
        );
    }

    @Test
    void avatarShellRendersCompactChatWidgetRootsAndScopedAssets() {
        String html = controller.avatar();

        assertThat(html).contains("/css/avatar-dashboard.css?v=1");
        assertThat(html).contains("/js/avatar-chat.js?v=3");
        assertThat(html).doesNotContain("/js/chat-client.js");
        assertThat(html).contains("id=\"avatar-chat\"");
        assertThat(html).contains("data-avatar-chat=\"true\"");
        assertThat(html).contains("/dashboard");
        for (AvatarDashboardComponents.WidgetDefinition widget : AvatarDashboardComponents.WIDGETS) {
            assertThat(html).contains("id=\"avatar-widget-" + widget.key() + "\"");
        }
    }

    @Test
    void widgetFragmentsReturnStableTargets() {
        String grid = controller.widgets();
        String todos = controller.widget("todos");

        assertThat(grid).contains("id=\"avatar-widget-grid\"");
        assertThat(todos).contains("id=\"avatar-widget-todos\"");
        assertThat(todos).contains("hx-post=\"/avatar/_todos\"");
        assertThatThrownBy(() -> controller.widget("unknown"))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rowLayoutEditorAddsMovesResizesAndRemovesWidgets() {
        String emptyEditor = controller.edit(false);
        assertThat(emptyEditor).contains("/avatar/_layout/rows");

        String afterRow = controller.addLayoutRow();
        String rowId = avatarService.dashboardRows().getFirst().id();
        assertThat(afterRow).contains("hx-swap-oob=\"true\"");
        assertThat(afterRow).contains("/avatar/_layout/rows/" + rowId + "/catalog");

        String catalog = controller.widgetCatalog(rowId);
        assertThat(catalog).contains("Add Widget");
        assertThat(catalog).contains("daily-tasks");

        assertThatThrownBy(() -> controller.addLayoutWidget(rowId, "unknown", 4))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(error -> ((ResponseStatusException) error).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);

        controller.addLayoutWidget(rowId, "todos", 4);
        controller.addLayoutWidget(rowId, "notes", 4);
        String todosId = avatarService.dashboardRows().getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("todos"))
            .findFirst()
            .orElseThrow()
            .id();
        String notesId = avatarService.dashboardRows().getFirst().widgets().stream()
            .filter(widget -> widget.widgetKey().equals("notes"))
            .findFirst()
            .orElseThrow()
            .id();

        String resized = controller.resizeLayoutWidget(todosId, 6);
        assertThat(resized).contains("id=\"avatar-widget-todos\"");
        assertThat(avatarService.dashboardRows().getFirst().widgets()).anySatisfy(widget -> {
            assertThat(widget.widgetKey()).isEqualTo("todos");
            assertThat(widget.columnWidth()).isEqualTo(6);
        });

        controller.moveLayoutWidget(notesId, "left");
        assertThat(avatarService.dashboardRows().getFirst().widgets())
            .extracting(widget -> widget.widgetKey())
            .containsExactly("notes", "todos");

        controller.removeLayoutWidget(notesId);
        assertThat(avatarService.dashboardRows().getFirst().widgets())
            .extracting(widget -> widget.widgetKey())
            .containsExactly("todos");

        controller.addLayoutRow();
        String secondRowId = avatarService.dashboardRows().get(1).id();
        controller.moveLayoutRow(secondRowId, "up");
        assertThat(avatarService.dashboardRows().getFirst().id()).isEqualTo(secondRowId);
    }

    @Test
    void organizerEndpointsMutateAvatarServicesAndReturnWidgets() {
        String todoHtml = controller.createTodo("Pay bills", "checking", "HIGH");
        assertThat(todoHtml).contains("Pay bills");
        assertThat(avatarService.todos()).singleElement()
            .satisfies(todo -> assertThat(todo.priority().name()).isEqualTo("HIGH"));

        String dailyHtml = controller.createDailyTask("Review day", null);
        assertThat(dailyHtml).contains("Review day");
        assertThat(avatarService.dailyTasks(LocalDate.now())).singleElement()
            .satisfies(task -> assertThat(task.status()).isEqualTo(AvatarTaskStatus.PLANNED));

        String notesHtml = controller.createNote("Garden", "Water seedlings");
        assertThat(notesHtml).contains("Garden");
        assertThat(avatarService.notes(false)).singleElement()
            .satisfies(note -> assertThat(note.body()).contains("Water seedlings"));
    }

    @Test
    void outputPreviewUsesArtifactService() {
        String html = controller.outputPreview("artifact-1");

        assertThat(html).contains("summary");
        assertThat(html).contains("hello output");
        assertThat(html).contains("/api/outputs/artifact-1/download");
    }

    @Test
    void alertDismissAppendsInternalAvatarEventOnly() {
        String html = controller.dismissAlert("alert-1");

        assertThat(html).contains("id=\"avatar-widget-alerts\"");
        assertThat(avatarService.events()).anySatisfy(event -> {
            assertThat(event.eventType()).isEqualTo("alert.dismissed");
            assertThat(event.payload()).containsEntry("eventId", "alert-1");
        });
    }

    private static class StubChatService extends ChatService {
        StubChatService() {
            super(null, null, null, null, null);
        }

        @Override
        public String defaultModel() {
            return "qwen3";
        }
    }

    private static class StubOutputArtifactService extends OutputArtifactService {
        StubOutputArtifactService(Path tempDir) throws IOException {
            super(null, new WorkspaceDirectoryService(new AiConfig(
                null,
                null,
                null,
                10,
                tempDir,
                Map.of(),
                Map.of()
            )), new ObjectMapper());
        }

        @Override
        public List<RunOutputArtifact> query(String runId, String planId, String artifactType, Integer limit) {
            return List.of(artifact());
        }

        @Override
        public RunOutputArtifact getArtifact(String artifactId) {
            return artifact();
        }

        @Override
        public String loadContent(String artifactId, long maxBytes) {
            return "hello output";
        }

        private RunOutputArtifact artifact() {
            return new RunOutputArtifact(
                "artifact-1",
                "run-1",
                "plan-1",
                "avatar",
                "job-1",
                "project-1",
                "workspace-1",
                "PLAN",
                "summary",
                "text",
                "summary.txt",
                "outputs/summary.txt",
                null,
                Instant.parse("2026-05-22T10:00:00Z")
            );
        }
    }

    private static class StubAgentProfileService extends AgentProfileService {
        StubAgentProfileService() {
            super(null, null, null);
        }

        @Override
        public List<AgentProfile> list() {
            return List.of();
        }
    }

    private static class StubJobService extends JobService {
        StubJobService() {
            super(null, null, null, null);
        }

        @Override
        public List<JobDefinition> listDefinitions() {
            return List.of();
        }
    }

    private static class StubInboxService extends InboxService {
        StubInboxService() {
            super(null, null);
        }

        @Override
        public List<InboxMessage> userInbox() {
            return List.of(new InboxMessage(
                "message-1",
                InboxMessageToType.USER,
                null,
                "agent-1",
                InboxMessageType.INFO,
                "Internal inbox message",
                null,
                null,
                null,
                null,
                Instant.parse("2026-05-22T10:00:00Z"),
                Instant.parse("2026-05-22T10:00:00Z")
            ));
        }
    }

    private static class EmptyAssignmentProvider implements ObjectProvider<AssignmentService> {
        @Override
        public AssignmentService getObject(Object... args) {
            return null;
        }

        @Override
        public AssignmentService getIfAvailable() {
            return null;
        }

        @Override
        public AssignmentService getIfUnique() {
            return null;
        }

        @Override
        public AssignmentService getObject() {
            return null;
        }

        @Override
        public java.util.Iterator<AssignmentService> iterator() {
            return List.<AssignmentService>of().iterator();
        }

        @Override
        public java.util.stream.Stream<AssignmentService> stream() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.stream.Stream<AssignmentService> orderedStream() {
            return java.util.stream.Stream.empty();
        }
    }
}
