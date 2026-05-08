package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskDraft;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunContext;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunResult;
import io.mindspice.magenta2.ai.orchestration.runtime.OrchestrationRunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final ChatService chatService;
    private final OrchestrationRunService orchestrationRunService;

    public TaskController(TaskService taskService, OrchestrationRunService orchestrationRunService) {
        this(taskService, null, orchestrationRunService);
    }

    @Autowired
    public TaskController(TaskService taskService, ChatService chatService, OrchestrationRunService orchestrationRunService) {
        this.taskService = taskService;
        this.chatService = chatService;
        this.orchestrationRunService = orchestrationRunService;
    }

    @GetMapping
    public List<TaskDefinition> list() {
        return taskService.listTasks();
    }

    @GetMapping("/{taskId}")
    public TaskDefinition get(@PathVariable String taskId) {
        try {
            return taskService.getTask(taskId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping
    public TaskDefinition create(@Valid @RequestBody TaskDefinition task) {
        try {
            return taskService.saveTask(task);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/{taskId}")
    public TaskDefinition update(@PathVariable String taskId, @Valid @RequestBody TaskDefinition task) {
        try {
            return taskService.saveTask(new TaskDefinition(
                taskId, task.title(), task.summary(), task.goal(), task.notes(), task.inputDescription(),
                task.inputs(), task.outputDescription(), task.outputs(), task.assumptions(), task.steps(),
                task.validationCriteria(), task.createdAt(), task.updatedAt()
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @DeleteMapping("/{taskId}")
    public void delete(@PathVariable String taskId) {
        taskService.deleteTask(taskId);
    }

    @PostMapping("/drafts/{conversationId}")
    public TaskDraft beginDraft(@PathVariable String conversationId, @RequestBody(required = false) DraftRequest request) {
        DraftRequest body = request == null ? new DraftRequest(null, null) : request;
        return taskService.beginDraft(conversationId, body.prePlanningModel(), body.executionModel());
    }

    @GetMapping("/drafts/{conversationId}")
    public TaskDraft getDraft(@PathVariable String conversationId) {
        return taskService.activeDraft(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task draft not found"));
    }

    @PostMapping("/drafts/{conversationId}/answers")
    public TaskDraft answerDraftQuestion(@PathVariable String conversationId, @Valid @RequestBody TaskAnswerRequest request) {
        try {
            return taskService.recordPromptAnswer(conversationId, request.answer(), request.notes(), request.questionIndex());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PostMapping("/drafts/{conversationId}/approve")
    public TaskDefinition approveDraft(@PathVariable String conversationId) {
        try {
            return taskService.approveDraft(conversationId);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @GetMapping("/{taskId}/runs")
    public List<TaskRun> listRuns(@PathVariable String taskId) {
        return taskService.listRuns(taskId);
    }

    @GetMapping("/runs/{runId}")
    public TaskRun getRun(@PathVariable String runId) {
        try {
            return taskService.getRun(runId);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping(value = "/{taskId}/runs/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRun(@PathVariable String taskId, @RequestBody(required = false) TaskRunRequest request) {
        SseEmitter emitter = new SseEmitter(0L);
        Map<String, Object> inputs = request == null || request.inputValues() == null ? Map.of() : request.inputValues();
        java.util.concurrent.atomic.AtomicReference<Disposable> subscriptionRef = new java.util.concurrent.atomic.AtomicReference<>();
        Runnable cancelSubscription = () -> {
            Disposable subscription = subscriptionRef.get();
            if (subscription != null && !subscription.isDisposed()) {
                subscription.dispose();
            }
        };
        emitter.onCompletion(cancelSubscription);
        emitter.onTimeout(cancelSubscription);
        emitter.onError(error -> cancelSubscription.run());

        try {
            OrchestrationRunContext context = context(request);
            Flux<SsePayload> stream;
            if (context.hasContext()) {
                stream = Flux.defer(() -> {
                    OrchestrationRunResult result = orchestrationRunService.runTask(taskId, inputs, context);
                    String terminalEvent = result.assignment().status().name().equals("COMPLETED") ? "completed" : "failed";
                    return Flux.just(
                        new SsePayload("started", Map.of(
                            "event", "started",
                            "assignmentId", result.assignment().id(),
                            "runId", result.runId() == null ? "" : result.runId(),
                            "taskId", taskId
                        )),
                        new SsePayload(terminalEvent, Map.of(
                            "event", terminalEvent,
                            "assignmentId", result.assignment().id(),
                            "runId", result.runId() == null ? "" : result.runId(),
                            "status", result.assignment().status().name(),
                            "outputValues", result.outputValues(),
                            "error", result.assignment().errorText() == null ? "" : result.assignment().errorText()
                        ))
                    );
                });
            } else if (chatService == null) {
                throw new IllegalStateException("Task streaming requires model-backed chat execution");
            } else {
                stream = chatService.streamTaskExecution(
                    taskId,
                    inputs,
                    request == null ? null : request.conversationId(),
                    request == null ? null : request.modelOverride()
                ).map(event -> {
                    if ("started".equals(event.event())) {
                        return new SsePayload("started", Map.of(
                            "event", "started",
                            "conversationId", event.conversationId(),
                            "runId", event.runId(),
                            "taskId", taskId
                        ));
                    }
                    if ("tool".equals(event.event()) || "progress".equals(event.event())) {
                        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                        payload.put("event", event.event());
                        payload.put("conversationId", event.conversationId());
                        payload.put("runId", event.runId());
                        payload.put("message", event.message() == null ? "" : event.message().text());
                        payload.put("toolActivity", event.message() == null ? null : event.message().toolActivity());
                        return new SsePayload(event.event(), payload);
                    }
                    TaskRun run = event.run();
                    return new SsePayload(event.event(), Map.of(
                        "event", event.event(),
                        "conversationId", event.conversationId(),
                        "runId", event.runId(),
                        "status", run.status().name(),
                        "outputValues", run.outputValues(),
                        "error", run.errorText() == null ? "" : run.errorText()
                    ));
                });
            }
            Disposable subscription = stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    event -> {
                        try {
                            send(emitter, event.name(), event.data());
                        } catch (IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    },
                    error -> {
                        try {
                            send(emitter, "failed", Map.of("event", "failed", "error", error.getMessage()));
                            emitter.complete();
                        } catch (IOException ioException) {
                            emitter.completeWithError(ioException);
                        }
                    },
                    emitter::complete
                );
            subscriptionRef.set(subscription);
        } catch (IllegalArgumentException exception) {
            try {
                send(emitter, "failed", Map.of("event", "failed", "error", exception.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.complete();
        } catch (Exception exception) {
            try {
                send(emitter, "failed", Map.of("event", "failed", "error", exception.getMessage()));
            } catch (IOException ignored) {
            }
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    private OrchestrationRunContext context(TaskRunRequest request) {
        if (request == null) {
            return new OrchestrationRunContext(null, null, null, null, null);
        }
        return new OrchestrationRunContext(
            request.agentId(), request.jobId(), request.workspaceId(), request.modelOverride(), request.priority()
        );
    }

    private record SsePayload(String name, Object data) {
    }

    public record DraftRequest(String prePlanningModel, String executionModel) {
    }

    public record TaskAnswerRequest(@NotBlank String answer, String notes, Integer questionIndex) {
    }

    public record TaskRunRequest(
        Map<String, Object> inputValues,
        String conversationId,
        String agentId,
        String jobId,
        String workspaceId,
        String modelOverride,
        Integer priority
    ) {
    }
}
