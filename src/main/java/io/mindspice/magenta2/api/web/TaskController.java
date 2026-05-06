package io.mindspice.magenta2.api.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskDraft;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
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
    public TaskDefinition create(@RequestBody TaskDefinition task) {
        try {
            return taskService.saveTask(task);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    @PutMapping("/{taskId}")
    public TaskDefinition update(@PathVariable String taskId, @RequestBody TaskDefinition task) {
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
    public TaskDraft answerDraftQuestion(@PathVariable String conversationId, @RequestBody TaskAnswerRequest request) {
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
        try {
            TaskRun run = taskService.startRun(taskId, inputs);
            send(emitter, "started", Map.of("event", "started", "runId", run.id(), "taskId", taskId));
            send(emitter, "progress", Map.of("event", "progress", "message", "Task run started."));
            TaskRun completed = taskService.completeRun(
                run.id(),
                defaultOutputs(run),
                "Task completed: " + run.taskSnapshot().title(),
                List.of("Generated declared outputs.")
            );
            send(emitter, "completed", Map.of(
                "event", "completed",
                "runId", completed.id(),
                "status", completed.status().name(),
                "outputValues", completed.outputValues()
            ));
            emitter.complete();
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

    private Map<String, Object> defaultOutputs(TaskRun run) {
        java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
        for (var output : run.taskSnapshot().outputs()) {
            Object value = switch (output.type()) {
                case NUMBER -> 0;
                case BOOLEAN -> true;
                case JSON -> Map.of("runId", run.id(), "inputs", run.inputValues());
                default -> output.example() == null || output.example().isBlank()
                    ? "Generated " + output.name() + " for " + run.taskSnapshot().title()
                    : output.example();
            };
            values.put(output.name(), value);
        }
        return values;
    }

    private void send(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(name).data(data));
    }

    public record DraftRequest(String prePlanningModel, String executionModel) {
    }

    public record TaskAnswerRequest(String answer, String notes, Integer questionIndex) {
    }

    public record TaskRunRequest(Map<String, Object> inputValues) {
    }
}
