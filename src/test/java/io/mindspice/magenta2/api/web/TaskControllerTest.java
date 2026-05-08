package io.mindspice.magenta2.api.web;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.model.ChatMessage;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskFieldDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRepository;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.task.TaskStep;
import io.mindspice.magenta2.ai.chat.task.TaskValueType;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskControllerTest {

    @Test
    void taskApiIgnoresLegacyDeliverablesAndDoesNotExposeThem() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        TaskController controller = new TaskController(taskService(), null);
        TaskDefinition request = objectMapper.readValue(
            """
                {
                  "title": "Research task",
                  "goal": "Collect notes.",
                  "deliverables": ["Legacy deliverable"],
                  "outputs": [{"name":"research_notes","type":"long_text","description":"Notes.","required":true}],
                  "steps": [{"order":1,"text":"Collect notes."}],
                  "validationCriteria": ["research_notes is present."]
                }
                """,
            TaskDefinition.class
        );

        TaskDefinition response = controller.create(request);
        String json = objectMapper.writeValueAsString(response);

        assertThat(response.outputs()).extracting(TaskFieldDefinition::name).containsExactly("research_notes");
        assertThat(json).contains("outputs");
        assertThat(json).doesNotContain("deliverables");
    }

    @Test
    void updateUsesPathIdWithoutDeliverables() throws Exception {
        TaskController controller = new TaskController(taskService(), null);

        TaskDefinition response = controller.update("task-1", new TaskDefinition(
            null,
            "Research task",
            null,
            "Collect notes.",
            null,
            null,
            List.of(),
            null,
            List.of(new TaskFieldDefinition("research_notes", TaskValueType.LONG_TEXT, "Notes.", true, null, null)),
            List.of(),
            List.of(new TaskStep(1, "Collect notes.")),
            List.of("research_notes is present."),
            null,
            null
        ));

        assertThat(response.id()).isEqualTo("task-1");
        assertThat(response.outputs()).extracting(TaskFieldDefinition::name).containsExactly("research_notes");
    }

    @Test
    void streamRunReturnsBeforeTaskExecutionCompletesAndEmitsTerminalStatus() throws Exception {
        BlockingTaskChatService chatService = new BlockingTaskChatService();
        TaskController controller = new TaskController(taskService(), chatService, nullOrchestrationRunService());

        CompletableFuture<SseEmitter> response = CompletableFuture.supplyAsync(() ->
            controller.streamRun("task-1", new TaskController.TaskRunRequest(
                Map.of("topic", "SQLite"), "conversation-1", null, null, null, null, null
            ))
        );

        SseEmitter emitter = response.get(200, TimeUnit.MILLISECONDS);
        CapturedSse captured = initializeEmitter(emitter);
        assertThat(chatService.subscribed.await(1, TimeUnit.SECONDS)).isTrue();
        chatService.release.countDown();
        assertThat(captured.completed.await(1, TimeUnit.SECONDS)).isTrue();

        String events = String.join("\n", captured.events);
        assertThat(events).contains("completed");
        assertThat(events).contains("COMPLETED");
    }

    private TaskService taskService() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        for (String statement : Files.readString(Path.of("src/main/resources/schema.sql")).split(";")) {
            if (!statement.isBlank()) {
                jdbcTemplate.execute(statement);
            }
        }
        return new TaskService(new TaskRepository(jdbcTemplate, new ObjectMapper()));
    }

    private OrchestrationRunService nullOrchestrationRunService() {
        return null;
    }

    private CapturedSse initializeEmitter(SseEmitter emitter) throws Exception {
        CapturedSse captured = new CapturedSse();
        Class<?> handlerType = Class.forName(
            "org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter$Handler"
        );
        Object handler = Proxy.newProxyInstance(
            handlerType.getClassLoader(),
            new Class<?>[] { handlerType },
            (proxy, method, args) -> {
                if ("send".equals(method.getName()) && args[0] instanceof Set<?> set) {
                    for (Object item : set) {
                        captured.events.add(String.valueOf(item.getClass().getMethod("getData").invoke(item)));
                    }
                } else if ("send".equals(method.getName())) {
                    captured.events.add(String.valueOf(args[0]));
                } else if ("complete".equals(method.getName())) {
                    captured.completed.countDown();
                } else if ("completeWithError".equals(method.getName())) {
                    captured.completed.countDown();
                }
                return null;
            }
        );
        var initialize = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class
            .getDeclaredMethod("initialize", handlerType);
        initialize.setAccessible(true);
        initialize.invoke(emitter, handler);
        return captured;
    }

    private static final class CapturedSse {
        private final List<String> events = java.util.Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch completed = new CountDownLatch(1);
    }

    @Test
    void createTaskRejectsBlankTitle() throws Exception {
        TaskController controller = new TaskController(taskService(), null);
        TaskDefinition blankTitle = new TaskDefinition(
            null, "  ", null, "goal", null, null, List.of(), null,
            List.of(), List.of(), List.of(), List.of(), null, null
        );

        assertThatThrownBy(() -> controller.create(blankTitle))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            });
    }

    @Test
    void getTaskReturns404ForMissingId() throws Exception {
        TaskController controller = new TaskController(taskService(), null);

        assertThatThrownBy(() -> controller.get("non-existent-id"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void getRunReturns404ForMissingRunId() throws Exception {
        TaskController controller = new TaskController(taskService(), null);

        assertThatThrownBy(() -> controller.getRun("non-existent-run"))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            });
    }

    @Test
    void beginDraftAcceptsNullBody() throws Exception {
        TaskController controller = new TaskController(taskService(), null);
        assertThat(controller.beginDraft("conversation-id", null)).isNotNull();
    }

    @Test
    void streamRunEmitterHasNoTimeout() throws Exception {
        BlockingTaskChatService chatService = new BlockingTaskChatService();
        TaskController controller = new TaskController(taskService(), chatService, nullOrchestrationRunService());

        SseEmitter emitter = controller.streamRun("task-1", new TaskController.TaskRunRequest(
            Map.of("topic", "SQLite"), "conversation-1", null, null, null, null, null
        ));

        assertThat(emitter.getTimeout()).isZero();
        chatService.release.countDown();
    }

    @Test
    void streamRunHandlesIllegalArgumentError() throws Exception {
        TaskController controller = new TaskController(taskService(), null, nullOrchestrationRunService());

        SseEmitter emitter = controller.streamRun("task-1", new TaskController.TaskRunRequest(
            Map.of("topic", "SQLite"), "conversation-1", null, null, null, null, null
        ));

        assertThat(emitter).isNotNull();
    }

    @Test
    void streamRunAcceptsNullBody() throws Exception {
        BlockingTaskChatService chatService = new BlockingTaskChatService();
        TaskController controller = new TaskController(taskService(), chatService, nullOrchestrationRunService());

        SseEmitter emitter = controller.streamRun("task-1", null);

        assertThat(emitter).isNotNull();
        chatService.release.countDown();
    }

    private static final class BlockingTaskChatService extends ChatService {
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        BlockingTaskChatService() {
            super(null, null, null, new io.mindspice.magenta2.ai.chat.rendering.ChatMarkdownRenderer(), null);
        }

        @Override
        public Flux<TaskExecutionEvent> streamTaskExecution(
            String taskId,
            Map<String, Object> inputValues,
            String conversationId,
            String modelOverride
        ) {
            return Flux.create(sink -> {
                subscribed.countDown();
                sink.next(new TaskExecutionEvent("started", conversationId, "run-1", null, null));
                try {
                    release.await(2, TimeUnit.SECONDS);
                    sink.next(new TaskExecutionEvent(
                        "progress", conversationId, "run-1", new ChatMessage("assistant", "working", "<p>working</p>", null), null
                    ));
                    sink.next(new TaskExecutionEvent(
                        "completed",
                        conversationId,
                        "run-1",
                        null,
                        new TaskRun(
                            "run-1", taskId, TaskRunStatus.COMPLETED, inputValues, Map.of("notes", "done"),
                            null, List.of("evidence"), List.of(), "done", null,
                            Instant.now(), Instant.now(), Instant.now(), Instant.now()
                        )
                    ));
                    sink.complete();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    sink.error(exception);
                }
            });
        }
    }
}
