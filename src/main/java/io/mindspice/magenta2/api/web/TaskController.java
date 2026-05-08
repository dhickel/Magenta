package io.mindspice.magenta2.api.web;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskDraft;
import io.mindspice.magenta2.ai.chat.task.TaskFieldDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.task.TaskStep;
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
    public TaskDefinition create(@Valid @RequestBody TaskCreateRequest request) {
        try {
            return taskService.saveTask(toDomain(request));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/{taskId}")
    public TaskDefinition update(@PathVariable String taskId, @Valid @RequestBody TaskUpdateRequest request) {
        try {
            return taskService.saveTask(toDomain(taskId, request));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static TaskDefinition toDomain(TaskCreateRequest request) {
        return new TaskDefinition(
            null,
            request.title(),
            request.summary(),
            request.goal(),
            request.notes(),
            request.inputDescription(),
            request.inputs(),
            request.outputDescription(),
            request.outputs(),
            request.assumptions(),
            request.steps(),
            request.validationCriteria(),
            null,
            null
        );
    }

    private static TaskDefinition toDomain(String taskId, TaskUpdateRequest request) {
        return new TaskDefinition(
            taskId,
            request.title(),
            request.summary(),
            request.goal(),
            request.notes(),
            request.inputDescription(),
            request.inputs(),
            request.outputDescription(),
            request.outputs(),
            request.assumptions(),
            request.steps(),
            request.validationCriteria(),
            null,
            null
        );
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
        SseEmitter emitter = SseStreamLifecycle.createEmitter();
        Map<String, Object> inputs = request == null || request.inputValues() == null ? Map.of() : request.inputValues();
        SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
        SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

        try {
            OrchestrationRunContext context = TaskStreamSupport.toContext(request);
            Flux<SsePayload> stream;
            if (context.hasContext()) {
                stream = Flux.defer(() -> {
                    OrchestrationRunResult result = orchestrationRunService.runTask(taskId, inputs, context);
                    return TaskStreamSupport.orchestrationRunEvents(taskId, result);
                });
            } else if (chatService == null) {
                throw new IllegalStateException("Task streaming requires model-backed chat execution");
            } else {
                stream = TaskStreamSupport.chatServiceRunEvents(
                    taskId,
                    chatService.streamTaskExecution(
                        taskId,
                        inputs,
                        request == null ? null : request.conversationId(),
                        request == null ? null : request.modelOverride()
                    )
                );
            }
            Disposable subscription = stream
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                    event -> {
                        try {
                            SseStreamLifecycle.sendSseEvent(emitter, event.name(), event.data());
                        } catch (java.io.IOException ioException) {
                            throw new RuntimeException(ioException);
                        }
                    },
                    error -> {
                        try {
                            SseStreamLifecycle.sendSseEvent(emitter, "failed",
                                Map.of("event", "failed", "error", error.getMessage()));
                            emitter.complete();
                        } catch (java.io.IOException ioException) {
                            emitter.completeWithError(ioException);
                        }
                    },
                    emitter::complete
                );
            guard.set(subscription);
        } catch (IllegalArgumentException exception) {
            try {
                SseStreamLifecycle.sendSseEvent(emitter, "failed",
                    Map.of("event", "failed", "error", exception.getMessage()));
            } catch (java.io.IOException ignored) {
            }
            emitter.complete();
        } catch (Exception exception) {
            try {
                SseStreamLifecycle.sendSseEvent(emitter, "failed",
                    Map.of("event", "failed", "error", exception.getMessage()));
            } catch (java.io.IOException ignored) {
            }
            emitter.completeWithError(exception);
        }
        return emitter;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskCreateRequest(
        @NotBlank String title,
        String summary,
        String goal,
        String notes,
        String inputDescription,
        List<TaskFieldDefinition> inputs,
        String outputDescription,
        List<TaskFieldDefinition> outputs,
        List<String> assumptions,
        List<TaskStep> steps,
        List<String> validationCriteria
    ) {
        public TaskCreateRequest {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
            steps = steps == null ? List.of() : List.copyOf(steps);
            validationCriteria = validationCriteria == null ? List.of() : List.copyOf(validationCriteria);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskUpdateRequest(
        @NotBlank String title,
        String summary,
        String goal,
        String notes,
        String inputDescription,
        List<TaskFieldDefinition> inputs,
        String outputDescription,
        List<TaskFieldDefinition> outputs,
        List<String> assumptions,
        List<TaskStep> steps,
        List<String> validationCriteria
    ) {
        public TaskUpdateRequest {
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
            steps = steps == null ? List.of() : List.copyOf(steps);
            validationCriteria = validationCriteria == null ? List.of() : List.copyOf(validationCriteria);
        }
    }
}
