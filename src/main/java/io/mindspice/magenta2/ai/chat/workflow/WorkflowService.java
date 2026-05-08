package io.mindspice.magenta2.ai.chat.workflow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.mindspice.magenta2.ai.chat.task.TaskDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskFieldDefinition;
import io.mindspice.magenta2.ai.chat.task.TaskRun;
import io.mindspice.magenta2.ai.chat.task.TaskRunStatus;
import io.mindspice.magenta2.ai.chat.task.TaskService;
import io.mindspice.magenta2.ai.chat.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class WorkflowService {
    private final WorkflowRepository workflowRepository;
    private final TaskService taskService;
    private final ChatService chatService;

    public WorkflowService(WorkflowRepository workflowRepository, TaskService taskService) {
        this(workflowRepository, taskService, null);
    }

    @Autowired
    public WorkflowService(WorkflowRepository workflowRepository, TaskService taskService, ChatService chatService) {
        this.workflowRepository = workflowRepository;
        this.taskService = taskService;
        this.chatService = chatService;
    }

    public List<WorkflowDefinition> listWorkflows() {
        return workflowRepository.findAll();
    }

    public WorkflowDefinition getWorkflow(String id) {
        return workflowRepository.find(id).orElseThrow(() -> new IllegalStateException("Workflow not found: " + id));
    }

    public WorkflowDefinition saveWorkflow(WorkflowDefinition workflow) {
        String id = StringUtils.hasText(workflow.id()) ? workflow.id() : UUID.randomUUID().toString();
        String title = normalize(workflow.title());
        if (title == null) {
            throw new IllegalArgumentException("Workflow title is required");
        }
        List<WorkflowStep> steps = cleanSteps(workflow.steps());
        if (steps.size() < 2 || steps.size() > 3) {
            throw new IllegalArgumentException("Workflow v1 requires two or three linear steps");
        }
        for (WorkflowStep step : steps) {
            taskService.getTask(step.taskId());
        }
        return workflowRepository.save(new WorkflowDefinition(
            id, title, normalize(workflow.summary()), steps, workflow.createdAt(), workflow.updatedAt()
        ));
    }

    public void deleteWorkflow(String id) {
        workflowRepository.delete(id);
    }

    public List<String> compatibilityWarnings(WorkflowDefinition workflow) {
        List<String> warnings = new ArrayList<>();
        List<WorkflowStep> steps = workflow.steps();
        for (int i = 1; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            TaskDefinition downstream = taskService.getTask(step.taskId());
            for (WorkflowInputBinding binding : step.inputBindings()) {
                if (binding.kind() != WorkflowBindingKind.STEP_OUTPUT) {
                    continue;
                }
                WorkflowStep sourceStep = steps.stream()
                    .filter(candidate -> candidate.stepKey().equals(binding.sourceStepKey()))
                    .findFirst()
                    .orElse(null);
                if (sourceStep == null) {
                    warnings.add("Unknown source step: " + binding.sourceStepKey());
                    continue;
                }
                TaskDefinition upstream = taskService.getTask(sourceStep.taskId());
                TaskFieldDefinition output = fieldByName(upstream.outputs(), binding.sourceOutputName());
                TaskFieldDefinition input = fieldByName(downstream.inputs(), binding.inputName());
                if (output != null && input != null && output.type() != input.type()) {
                    warnings.add("Type mismatch: " + sourceStep.stepKey() + "." + output.name()
                        + " is " + output.type().wireName() + " but " + step.stepKey() + "." + input.name()
                        + " expects " + input.type().wireName());
                }
            }
        }
        return warnings;
    }

    public WorkflowRun startRun(String workflowId) {
        WorkflowDefinition workflow = getWorkflow(workflowId);
        Instant now = Instant.now();
        WorkflowRun run = workflowRepository.saveRun(new WorkflowRun(
            UUID.randomUUID().toString(),
            workflow.id(),
            WorkflowRunStatus.RUNNING,
            workflow,
            workflow.steps().stream()
                .map(step -> new WorkflowStepRun(step.stepKey(), step.taskId(), null, WorkflowStepRunStatus.PENDING, Map.of(), Map.of(), null))
                .toList(),
            Map.of(),
            null,
            null,
            now,
            now,
            now,
            null
        ));
        return run;
    }

    public WorkflowRun runSynchronously(String workflowId) {
        return runSynchronously(workflowId, null);
    }

    public WorkflowRun runSynchronously(String workflowId, String modelOverride) {
        if (chatService == null) {
            throw new IllegalStateException("Workflow execution requires model-backed task execution");
        }
        WorkflowRun run = startRun(workflowId);
        Map<String, Map<String, Object>> outputsByStep = new LinkedHashMap<>();
        List<WorkflowStepRun> stepRuns = new ArrayList<>();
        try {
            for (WorkflowStep step : run.workflowSnapshot().steps()) {
                Map<String, Object> inputs = resolveInputs(step, run.workflowSnapshot(), outputsByStep);
                TaskRun taskRun = chatService.executeTaskBlocking(
                    step.taskId(),
                    inputs,
                    UUID.randomUUID().toString(),
                    modelOverride
                ).run();
                if (taskRun.status() != TaskRunStatus.COMPLETED) {
                    throw new IllegalStateException("Task step " + step.stepKey() + " did not complete: "
                        + (taskRun.errorText() == null ? taskRun.status().name() : taskRun.errorText()));
                }
                WorkflowStepRun stepRun = new WorkflowStepRun(
                    step.stepKey(),
                    step.taskId(),
                    taskRun.id(),
                    WorkflowStepRunStatus.COMPLETED,
                    inputs,
                    taskRun.outputValues(),
                    null
                );
                stepRuns.add(stepRun);
                outputsByStep.put(step.stepKey(), taskRun.outputValues());
                run = workflowRepository.saveRun(copyRun(run, WorkflowRunStatus.RUNNING, stepRuns, Map.of(), null, null, null));
            }
            Map<String, Object> finalOutputs = stepRuns.isEmpty()
                ? Map.of()
                : stepRuns.get(stepRuns.size() - 1).outputValues();
            return workflowRepository.saveRun(copyRun(run, WorkflowRunStatus.COMPLETED, stepRuns, finalOutputs,
                "Workflow completed: " + run.workflowSnapshot().title(), null, Instant.now()));
        } catch (RuntimeException exception) {
            return workflowRepository.saveRun(copyRun(run, WorkflowRunStatus.FAILED, stepRuns, Map.of(), null,
                exception.getMessage(), Instant.now()));
        }
    }

    public WorkflowRun getRun(String runId) {
        return workflowRepository.findRun(runId).orElseThrow(() -> new IllegalStateException("Workflow run not found: " + runId));
    }

    public List<WorkflowRun> listRuns(String workflowId) {
        return workflowRepository.findRunsForWorkflow(workflowId);
    }

    Map<String, Object> resolveInputs(
        WorkflowStep step,
        WorkflowDefinition workflow,
        Map<String, Map<String, Object>> outputsByStep
    ) {
        TaskDefinition task = taskService.getTask(step.taskId());
        Map<String, Object> values = new LinkedHashMap<>();
        for (WorkflowInputBinding binding : step.inputBindings()) {
            if (!StringUtils.hasText(binding.inputName())) {
                continue;
            }
            if (binding.kind() == WorkflowBindingKind.STEP_OUTPUT) {
                Map<String, Object> sourceOutputs = outputsByStep.get(binding.sourceStepKey());
                if (sourceOutputs != null && sourceOutputs.containsKey(binding.sourceOutputName())) {
                    values.put(binding.inputName(), sourceOutputs.get(binding.sourceOutputName()));
                }
            } else {
                values.put(binding.inputName(), binding.literalValue());
            }
        }
        List<String> missing = task.inputs().stream()
            .filter(TaskFieldDefinition::required)
            .filter(input -> !values.containsKey(input.name()) || values.get(input.name()) == null
                || (values.get(input.name()) instanceof String text && !StringUtils.hasText(text)))
            .map(TaskFieldDefinition::name)
            .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                "Missing required workflow input(s) for step " + step.stepKey() + ": " + String.join(", ", missing)
            );
        }
        return values;
    }

    private WorkflowRun copyRun(
        WorkflowRun run,
        WorkflowRunStatus status,
        List<WorkflowStepRun> stepRuns,
        Map<String, Object> finalOutputs,
        String finalMessage,
        String errorText,
        Instant completedAt
    ) {
        return new WorkflowRun(run.id(), run.workflowId(), status, run.workflowSnapshot(), stepRuns, finalOutputs,
            finalMessage, errorText, run.createdAt(), Instant.now(), run.startedAt(), completedAt);
    }

    private List<WorkflowStep> cleanSteps(List<WorkflowStep> steps) {
        if (steps == null) {
            return List.of();
        }
        List<WorkflowStep> clean = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep step = steps.get(i);
            if (step == null || !StringUtils.hasText(step.taskId())) {
                continue;
            }
            clean.add(new WorkflowStep(
                StringUtils.hasText(step.stepKey()) ? step.stepKey().trim() : "step_" + (clean.size() + 1),
                step.taskId().trim(),
                cleanBindings(step.inputBindings())
            ));
        }
        return List.copyOf(clean);
    }

    private List<WorkflowInputBinding> cleanBindings(List<WorkflowInputBinding> bindings) {
        if (bindings == null) {
            return List.of();
        }
        return bindings.stream()
            .filter(binding -> binding != null && StringUtils.hasText(binding.inputName()))
            .map(binding -> new WorkflowInputBinding(
                binding.inputName().trim(),
                binding.kind(),
                binding.literalValue(),
                normalize(binding.sourceStepKey()),
                normalize(binding.sourceOutputName())
            ))
            .toList();
    }

    private TaskFieldDefinition fieldByName(List<TaskFieldDefinition> fields, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return fields.stream().filter(field -> name.equals(field.name())).findFirst().orElse(null);
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
