package io.mindspice.magenta2.ai.chat.task;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import io.mindspice.magenta2.ai.chat.repository.ChatSessionMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TaskService {
    private static final int MAX_QUEUED_QUESTIONS = 5;

    private final TaskRepository taskRepository;
    private final ChatSessionMetadataRepository chatSessionMetadataRepository;
    private final Map<String, String> executionRunsByConversationId = new ConcurrentHashMap<>();

    public TaskService(TaskRepository taskRepository) {
        this(taskRepository, null);
    }

    @Autowired
    public TaskService(TaskRepository taskRepository, ChatSessionMetadataRepository chatSessionMetadataRepository) {
        this.taskRepository = taskRepository;
        this.chatSessionMetadataRepository = chatSessionMetadataRepository;
    }

    public List<TaskDefinition> listTasks() {
        return taskRepository.findAll();
    }

    public TaskDefinition getTask(String id) {
        return taskRepository.find(id).orElseThrow(() -> new IllegalStateException("Task not found: " + id));
    }

    public TaskDefinition saveTask(TaskDefinition task) {
        String id = StringUtils.hasText(task.id()) ? task.id() : UUID.randomUUID().toString();
        String title = normalize(task.title());
        if (title == null) {
            throw new IllegalArgumentException("Task title is required");
        }
        validateFieldNames(task.inputs(), "input");
        validateFieldNames(task.outputs(), "output");
        return taskRepository.save(new TaskDefinition(
            id,
            title,
            normalize(task.summary()),
            normalize(task.goal()),
            normalize(task.notes()),
            normalize(task.inputDescription()),
            cleanFields(task.inputs()),
            normalize(task.outputDescription()),
            cleanFields(task.outputs()),
            cleanList(task.assumptions()),
            cleanSteps(task.steps()),
            cleanList(task.validationCriteria()),
            task.createdAt(),
            task.updatedAt()
        ));
    }

    public void deleteTask(String id) {
        taskRepository.delete(id);
    }

    public TaskDraft beginDraft(String conversationId, String prePlanningModel, String executionModel) {
        if (!StringUtils.hasText(conversationId)) {
            throw new IllegalArgumentException("conversationId is required");
        }
        Instant now = Instant.now();
        TaskDraft existing = taskRepository.findDraft(conversationId).orElse(null);
        return taskRepository.saveDraft(new TaskDraft(
            conversationId,
            TaskDraftStatus.DRAFT,
            "define_runtime_inputs",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of("What runtime inputs should this reusable task accept?"),
            0,
            normalize(prePlanningModel),
            normalize(executionModel),
            null,
            existing == null ? now : existing.createdAt(),
            now
        ));
    }

    public Optional<TaskDraft> activeDraft(String conversationId) {
        return taskRepository.findDraft(conversationId);
    }

    public List<String> listDraftConversationIds() {
        return taskRepository.findDraftConversationIds();
    }

    public PlanMode mode(String conversationId) {
        if (StringUtils.hasText(activeRunId(conversationId))) {
            return PlanMode.EXECUTE_TASK;
        }
        return taskRepository.findDraft(conversationId)
            .filter(draft -> draft.status() != TaskDraftStatus.APPROVED)
            .map(draft -> PlanMode.TASK)
            .orElse(PlanMode.NORMAL);
    }

    public TaskDraft setGoal(String conversationId, String goal) {
        TaskDraft draft = requireDraft(conversationId, "task_set_goal");
        String normalizedGoal = normalize(goal);
        if (normalizedGoal == null) {
            throw new IllegalArgumentException("task_set_goal requires a goal");
        }
        return saveDraft(draft.withPlanningTask("define_outputs").withGoal(normalizedGoal));
    }

    public TaskDraft setTask(String conversationId, String planningTask) {
        TaskDraft draft = requireDraft(conversationId, "task_set_task");
        String normalizedTask = normalizePlanningTask(planningTask);
        if (normalizedTask == null) {
            throw new IllegalArgumentException("task_set_task requires a current task");
        }
        return saveDraft(draft.withPlanningTask(normalizedTask));
    }

    public TaskDraft putTextItem(String conversationId, String section, Integer key, String text) {
        TaskDraft draft = requireDraft(conversationId, "task_put_item");
        int itemKey = requirePositiveKey(key);
        String normalizedText = normalize(text);
        if (normalizedText == null) {
            throw new IllegalArgumentException("task_put_item requires text");
        }
        return saveDraft(withSection(draft, section, itemKey, normalizedText, null, false));
    }

    public TaskDraft putFieldItem(String conversationId, String section, Integer key, TaskFieldDefinition field) {
        TaskDraft draft = requireDraft(conversationId, "task_put_item");
        int itemKey = requirePositiveKey(key);
        TaskFieldDefinition cleanField = cleanField(field);
        if (cleanField == null) {
            throw new IllegalArgumentException("task_put_item requires a named input or output");
        }
        return saveDraft(withSection(draft, section, itemKey, null, cleanField, false));
    }

    public TaskDraft deleteItem(String conversationId, String section, Integer key) {
        TaskDraft draft = requireDraft(conversationId, "task_delete_item");
        return saveDraft(withSection(draft, section, requirePositiveKey(key), null, null, true));
    }

    public TaskDraft askQuestions(String conversationId, List<String> questions) {
        TaskDraft draft = requireDraft(conversationId, "ask_user_questions");
        List<String> cleanQuestions = cleanList(questions);
        if (cleanQuestions.isEmpty()) {
            throw new IllegalArgumentException("ask_user_questions requires at least one question");
        }
        if (cleanQuestions.size() > MAX_QUEUED_QUESTIONS) {
            throw new IllegalArgumentException("ask_user_questions accepts at most five questions");
        }
        return saveDraft(draft.withPlanningTask("clarification_questions").withPendingQuestions(cleanQuestions, 0));
    }

    public TaskDraft recordPromptAnswer(String conversationId, String answer, String notes, Integer expectedQuestionIndex) {
        TaskDraft draft = requireDraft(conversationId, "task answer");
        if (!draft.hasPendingQuestion()) {
            throw new IllegalStateException("No active task question exists for this conversation");
        }
        int currentQuestionIndex = draft.pendingQuestionIndex() + 1;
        if (expectedQuestionIndex != null && expectedQuestionIndex != currentQuestionIndex) {
            throw new IllegalStateException(
                "Stale task answer. Expected question " + currentQuestionIndex + " but received " + expectedQuestionIndex
            );
        }
        if (!StringUtils.hasText(answer) && !StringUtils.hasText(notes)) {
            throw new IllegalArgumentException("Task answer requires an answer");
        }
        int nextIndex = draft.pendingQuestionIndex() + 1;
        List<String> pendingQuestions = nextIndex >= draft.pendingQuestions().size()
            ? List.of()
            : draft.pendingQuestions();
        return saveDraft(draft
            .withNotes(appendAnswerNote(draft.notes(), draft.currentQuestion(), answer, notes))
            .withPendingQuestions(pendingQuestions, pendingQuestions.isEmpty() ? 0 : nextIndex));
    }

    public TaskDraft markReadyForApproval(String conversationId) {
        TaskDraft draft = requireDraft(conversationId, "task_ready_for_approval");
        if (draft.hasPendingQuestion()) {
            throw new IllegalStateException("task_ready_for_approval requires all queued questions to be answered");
        }
        validateComplete(draft, "task_ready_for_approval");
        return saveDraft(draft
            .withStatus(TaskDraftStatus.READY_FOR_APPROVAL)
            .withPlanningTask("approval")
            .withPendingQuestions(List.of(), 0));
    }

    public TaskDefinition approveDraft(String conversationId) {
        TaskDraft draft = requireDraft(conversationId, "approve task");
        validateComplete(draft, "approve task");
        TaskDefinition task = saveTask(new TaskDefinition(
            StringUtils.hasText(draft.createdTaskId()) ? draft.createdTaskId() : UUID.randomUUID().toString(),
            draft.title(),
            draft.summary(),
            draft.goal(),
            draft.notes(),
            draft.inputDescription(),
            draft.inputs(),
            draft.outputDescription(),
            draft.outputs(),
            draft.assumptions(),
            draft.steps(),
            draft.validationCriteria(),
            null,
            null
        ));
        saveDraft(draft
            .withStatus(TaskDraftStatus.APPROVED)
            .withPlanningTask("approved")
            .withPendingQuestions(List.of(), 0)
            .withCreatedTaskId(task.id()));
        return task;
    }

    public String runtimeInstructions(String conversationId) {
        String runId = activeRunId(conversationId);
        if (StringUtils.hasText(runId)) {
            return executionInstructions(requireRun(runId));
        }
        return taskRepository.findDraft(conversationId)
            .filter(draft -> draft.status() != TaskDraftStatus.APPROVED)
            .map(this::draftInstructions)
            .orElse("");
    }

    public TaskRun startRun(String taskId, Map<String, Object> inputValues) {
        TaskDefinition task = getTask(taskId);
        Map<String, Object> cleanInputs = cleanMap(inputValues);
        List<String> missing = missingRequiredInputs(task, cleanInputs);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required task input(s): " + String.join(", ", missing));
        }
        Instant now = Instant.now();
        return taskRepository.saveRun(new TaskRun(
            UUID.randomUUID().toString(),
            task.id(),
            TaskRunStatus.RUNNING,
            cleanInputs,
            Map.of(),
            task,
            List.of("Task run started."),
            List.of(),
            null,
            null,
            now,
            now,
            now,
            null
        ));
    }

    public TaskRun startChatExecution(String conversationId, String taskId, Map<String, Object> inputValues) {
        TaskRun run = startRun(taskId, inputValues);
        registerExecutionContext(conversationId, run.id());
        return run;
    }

    public void registerExecutionContext(String conversationId, String runId) {
        if (!StringUtils.hasText(conversationId) || !StringUtils.hasText(runId)) {
            throw new IllegalArgumentException("conversationId and runId are required");
        }
        TaskRun run = requireRun(runId);
        if (run.status() != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("Task run is not active: " + runId);
        }
        executionRunsByConversationId.put(conversationId, runId);
        if (chatSessionMetadataRepository != null) {
            chatSessionMetadataRepository.saveActiveTaskRunId(conversationId, runId);
        }
    }

    public void clearExecutionContext(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            executionRunsByConversationId.remove(conversationId);
            if (chatSessionMetadataRepository != null) {
                chatSessionMetadataRepository.clearActiveTaskRunId(conversationId);
            }
        }
    }

    public String runIdForConversation(String conversationId) {
        return activeRunId(conversationId);
    }

    public String finalMessage(String runId) {
        return requireRun(runId).finalMessage();
    }

    public TaskRun recordReport(String runId, String summary, List<String> evidence) {
        TaskRun run = requireRun(runId);
        if (run.status() != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("task_report is available only while a task run is active");
        }
        List<String> entries = new ArrayList<>(run.executionEvidence());
        String normalizedSummary = normalize(summary);
        if (normalizedSummary != null) {
            entries.add("Summary: " + normalizedSummary);
        }
        for (String value : cleanList(evidence)) {
            entries.add("Evidence: " + value);
        }
        return taskRepository.saveRun(run.withExecutionEvidence(entries));
    }

    public TaskRun completeRun(String runId, Map<String, Object> outputValues, String finalMessage, List<String> evidence) {
        TaskRun run = requireRun(runId);
        if (run.status() != TaskRunStatus.RUNNING) {
            throw new IllegalStateException("task_complete is available only while a task run is active");
        }
        Map<String, Object> cleanOutputs = cleanMap(outputValues);
        List<String> missing = missingRequiredOutputs(run.taskSnapshot(), cleanOutputs);
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Missing required task output(s): " + String.join(", ", missing));
        }
        List<String> entries = new ArrayList<>(run.executionEvidence());
        entries.addAll(cleanList(evidence).stream().map(value -> "Evidence: " + value).toList());
        if (entries.isEmpty()) {
            entries.add("Summary: task completed.");
        }
        return taskRepository.saveRun(run
            .withStatus(TaskRunStatus.COMPLETED)
            .withOutputValues(cleanOutputs)
            .withExecutionEvidence(entries)
            .withFinalMessage(normalize(finalMessage))
            .withCompletedAt(Instant.now()));
    }

    public TaskRun failRun(String runId, String errorText) {
        TaskRun run = requireRun(runId);
        return taskRepository.saveRun(run
            .withStatus(TaskRunStatus.FAILED)
            .withErrorText(normalize(errorText))
            .withCompletedAt(Instant.now()));
    }

    public TaskRun markNeedsReview(String runId, String reason) {
        TaskRun run = requireRun(runId);
        List<String> feedback = new ArrayList<>(run.validationFeedback());
        String normalizedReason = normalize(reason);
        if (normalizedReason != null) {
            feedback.add(normalizedReason);
        }
        return taskRepository.saveRun(run
            .withStatus(TaskRunStatus.NEEDS_REVIEW)
            .withValidationFeedback(feedback)
            .withCompletedAt(Instant.now()));
    }

    public TaskRun markActiveRunNeedsReview(String conversationId, String reason) {
        String runId = activeRunId(conversationId);
        if (!StringUtils.hasText(runId)) {
            throw new IllegalStateException("No active task run exists for this conversation");
        }
        try {
            return markNeedsReview(runId, reason);
        } finally {
            clearExecutionContext(conversationId);
        }
    }

    public TaskRun failActiveRun(String conversationId, String errorText) {
        String runId = activeRunId(conversationId);
        if (!StringUtils.hasText(runId)) {
            throw new IllegalStateException("No active task run exists for this conversation");
        }
        try {
            return failRun(runId, errorText);
        } finally {
            clearExecutionContext(conversationId);
        }
    }

    public TaskRun getRun(String runId) {
        return requireRun(runId);
    }

    public List<TaskRun> listRuns(String taskId) {
        return taskRepository.findRunsForTask(taskId);
    }

    public List<String> compatibilityWarnings(TaskDefinition upstream, TaskDefinition downstream, Map<String, String> outputToInput) {
        if (upstream == null || downstream == null || outputToInput == null) {
            return List.of();
        }
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, String> entry : outputToInput.entrySet()) {
            TaskFieldDefinition output = fieldByName(upstream.outputs(), entry.getKey());
            TaskFieldDefinition input = fieldByName(downstream.inputs(), entry.getValue());
            if (output != null && input != null && output.type() != input.type()) {
                warnings.add("Type mismatch: " + output.name() + " is " + output.type().wireName()
                    + " but " + input.name() + " expects " + input.type().wireName());
            }
        }
        return warnings;
    }

    private TaskRun requireRun(String runId) {
        return taskRepository.findRun(runId).orElseThrow(() -> new IllegalStateException("Task run not found: " + runId));
    }

    private TaskDraft requireDraft(String conversationId, String action) {
        TaskDraft draft = taskRepository.findDraft(conversationId)
            .orElseThrow(() -> new IllegalStateException("No task draft exists for this conversation"));
        if (draft.status() == TaskDraftStatus.APPROVED) {
            throw new IllegalStateException(action + " is not available after task approval");
        }
        return draft;
    }

    private TaskDraft saveDraft(TaskDraft draft) {
        return taskRepository.saveDraft(draft);
    }

    private TaskDraft withSection(
        TaskDraft draft,
        String section,
        int key,
        String text,
        TaskFieldDefinition field,
        boolean delete
    ) {
        return switch (normalize(section) == null ? "" : normalize(section)) {
            case "input" -> draft
                .withPlanningTask("define_runtime_inputs")
                .withInputs(keyedFields(draft.inputs(), key, field, delete));
            case "output" -> draft
                .withPlanningTask("define_outputs")
                .withOutputs(keyedFields(draft.outputs(), key, field, delete));
            case "assumption" -> draft
                .withPlanningTask("clarify_and_elaborate")
                .withAssumptions(keyedList(draft.assumptions(), key, text, delete));
            case "note" -> draft
                .withPlanningTask("clarify_and_elaborate")
                .withNotes(keyedNoteText(draft.notes(), key, text, delete));
            case "step" -> draft
                .withPlanningTask("build_task_steps")
                .withSteps(keyedSteps(draft.steps(), key, text, delete));
            case "validation_criterion" -> draft
                .withPlanningTask("approval_readiness")
                .withValidationCriteria(keyedList(draft.validationCriteria(), key, text, delete));
            default -> throw new IllegalArgumentException("Unknown task section: " + section);
        };
    }

    private void validateComplete(TaskDraft draft, String action) {
        if (!StringUtils.hasText(draft.title())) {
            throw new IllegalArgumentException(action + " requires a title");
        }
        if (!StringUtils.hasText(draft.goal())) {
            throw new IllegalArgumentException(action + " requires a goal");
        }
        if (draft.outputs().isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one named output");
        }
        if (draft.steps().isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one step");
        }
        if (draft.validationCriteria().isEmpty()) {
            throw new IllegalArgumentException(action + " requires at least one validation criterion");
        }
    }

    private void validateFieldNames(List<TaskFieldDefinition> fields, String label) {
        java.util.Set<String> names = new java.util.HashSet<>();
        for (TaskFieldDefinition field : cleanFields(fields)) {
            if (!names.add(field.name())) {
                throw new IllegalArgumentException("Duplicate task " + label + " name: " + field.name());
            }
        }
    }

    private List<String> missingRequiredInputs(TaskDefinition task, Map<String, Object> values) {
        return task.inputs().stream()
            .filter(TaskFieldDefinition::required)
            .filter(field -> !values.containsKey(field.name()) || values.get(field.name()) == null
                || (values.get(field.name()) instanceof String text && !StringUtils.hasText(text)))
            .map(TaskFieldDefinition::name)
            .toList();
    }

    private List<String> missingRequiredOutputs(TaskDefinition task, Map<String, Object> values) {
        return task.outputs().stream()
            .filter(TaskFieldDefinition::required)
            .filter(field -> !values.containsKey(field.name()) || values.get(field.name()) == null
                || (values.get(field.name()) instanceof String text && !StringUtils.hasText(text)))
            .map(TaskFieldDefinition::name)
            .toList();
    }

    private TaskFieldDefinition fieldByName(List<TaskFieldDefinition> fields, String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return fields.stream().filter(field -> name.equals(field.name())).findFirst().orElse(null);
    }

    private String activeRunId(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        String cached = executionRunsByConversationId.get(conversationId);
        if (StringUtils.hasText(cached) && isActiveRun(cached)) {
            return cached;
        }
        if (StringUtils.hasText(cached)) {
            executionRunsByConversationId.remove(conversationId);
        }
        if (chatSessionMetadataRepository == null) {
            return null;
        }
        String stored = chatSessionMetadataRepository.findActiveTaskRunId(conversationId).orElse(null);
        if (!StringUtils.hasText(stored)) {
            return null;
        }
        if (isActiveRun(stored)) {
            executionRunsByConversationId.put(conversationId, stored);
            return stored;
        }
        chatSessionMetadataRepository.clearActiveTaskRunId(conversationId);
        return null;
    }

    private boolean isActiveRun(String runId) {
        return taskRepository.findRun(runId)
            .map(run -> run.status() == TaskRunStatus.RUNNING)
            .orElse(false);
    }

    private List<TaskFieldDefinition> keyedFields(List<TaskFieldDefinition> values, int key, TaskFieldDefinition field, boolean delete) {
        List<TaskFieldDefinition> updated = new ArrayList<>(values == null ? List.of() : values);
        int index = key - 1;
        if (delete) {
            if (index < updated.size()) {
                updated.remove(index);
            }
            return List.copyOf(updated);
        }
        while (updated.size() < index) {
            updated.add(new TaskFieldDefinition("field_" + (updated.size() + 1), TaskValueType.STRING, null, false, null, null));
        }
        if (index < updated.size()) {
            updated.set(index, field);
        } else {
            updated.add(field);
        }
        return cleanFields(updated);
    }

    private List<String> keyedList(List<String> values, int key, String text, boolean delete) {
        List<String> updated = new ArrayList<>(values == null ? List.of() : values);
        int index = key - 1;
        if (delete) {
            if (index < updated.size()) {
                updated.remove(index);
            }
            return List.copyOf(updated);
        }
        while (updated.size() < index) {
            updated.add("");
        }
        if (index < updated.size()) {
            updated.set(index, text);
        } else {
            updated.add(text);
        }
        return cleanList(updated);
    }

    private List<TaskStep> keyedSteps(List<TaskStep> steps, int key, String text, boolean delete) {
        List<TaskStep> updated = new ArrayList<>(steps == null ? List.of() : steps);
        updated.removeIf(step -> step.order() == key);
        if (!delete) {
            updated.add(new TaskStep(key, text));
        }
        return cleanSteps(updated);
    }

    private String keyedNoteText(String notes, int key, String text, boolean delete) {
        List<String> updated = keyedList(noteLines(notes), key, text, delete);
        return updated.isEmpty() ? null : String.join("\n", updated);
    }

    private String appendAnswerNote(String notes, String question, String answer, String answerNotes) {
        List<String> lines = new ArrayList<>(noteLines(notes));
        StringBuilder line = new StringBuilder("Answered: ").append(question);
        if (StringUtils.hasText(answer)) {
            line.append(" | ").append(answer.trim());
        }
        if (StringUtils.hasText(answerNotes)) {
            line.append(" | Notes: ").append(answerNotes.trim());
        }
        lines.add(line.toString());
        return String.join("\n", lines);
    }

    private List<String> noteLines(String notes) {
        return StringUtils.hasText(notes)
            ? notes.lines().map(this::normalize).filter(value -> value != null).toList()
            : List.of();
    }

    private int requirePositiveKey(Integer key) {
        if (key == null || key < 1) {
            throw new IllegalArgumentException("A positive integer key is required");
        }
        return key;
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(this::normalize).filter(value -> value != null).toList();
    }

    private List<TaskFieldDefinition> cleanFields(List<TaskFieldDefinition> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream().map(this::cleanField).filter(field -> field != null).toList();
    }

    private TaskFieldDefinition cleanField(TaskFieldDefinition field) {
        if (field == null || !StringUtils.hasText(field.name())) {
            return null;
        }
        return new TaskFieldDefinition(
            field.name().trim(),
            field.type(),
            normalize(field.description()),
            field.required(),
            normalize(field.schema()),
            normalize(field.example())
        );
    }

    private List<TaskStep> cleanSteps(List<TaskStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream()
            .filter(step -> step != null && StringUtils.hasText(step.text()))
            .map(step -> new TaskStep(step.order() <= 0 ? 1 : step.order(), step.text().trim()))
            .sorted(Comparator.comparingInt(TaskStep::order))
            .toList();
    }

    private Map<String, Object> cleanMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> clean = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (StringUtils.hasText(entry.getKey())) {
                clean.put(entry.getKey().trim(), entry.getValue());
            }
        }
        return clean;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizePlanningTask(String value) {
        String normalized = normalize(value);
        return "define_deliverables".equals(normalized) ? "define_outputs" : normalized;
    }

    private String draftInstructions(TaskDraft draft) {
        StringBuilder builder = new StringBuilder("""
            You are Magenta in TASK mode.

            Your job is to create a reusable task definition, not to execute one-off work.
            Tasks use typed runtime inputs, such as <Topic> or <SourceFile>, as placeholders for values supplied when the task runs.
            First confirm proposed input definitions with ask_user_questions before persisting them.
            Define concrete named outputs for workflow chaining. Named outputs are the reusable task's deliverables for downstream workflow binding.

            Required terminal states for every TASK-mode turn:
            - Queue one to five focused questions with ask_user_questions, or
            - Mark a complete draft with task_ready_for_approval.

            Tool rules:
            - Use task_set_goal, task_set_task, task_put_item, task_delete_item, and task_ready_for_approval.
            - task_put_item sections: input, output, assumption, note, step, validation_criterion.
            - Inputs and outputs require name, type, description, required, schema, and example fields.
            - Do not ask for concrete runtime values during task creation; ask only what the reusable task should accept.

            Runtime task draft:
            """.stripIndent());
        appendValue(builder, "Status", draft.status().name());
        appendValue(builder, "Current task", draft.planningTask());
        appendValue(builder, "Title", draft.title());
        appendValue(builder, "Goal", draft.goal());
        appendValue(builder, "Summary", draft.summary());
        appendList(builder, "Inputs", draft.inputs().stream().map(this::fieldSummary).toList());
        appendList(builder, "Outputs", draft.outputs().stream().map(this::fieldSummary).toList());
        appendList(builder, "Assumptions", draft.assumptions());
        appendList(builder, "Steps", draft.steps().stream().map(step -> step.order() + ". " + step.text()).toList());
        appendList(builder, "Validation criteria", draft.validationCriteria());
        if (draft.hasPendingQuestion()) {
            appendValue(builder, "Pending question", draft.currentQuestion());
            appendValue(builder, "Pending question progress", (draft.pendingQuestionIndex() + 1) + "/" + draft.pendingQuestions().size());
        }
        return builder.toString().trim();
    }

    private String executionInstructions(TaskRun run) {
        StringBuilder builder = new StringBuilder("""
            You are Magenta executing a reusable task in an isolated task-run context.

            Use the concrete runtime input values below. Record useful evidence with task_report while working.
            Complete only by calling task_complete with outputValues keyed exactly by declared output name.
            Missing required declared outputs are rejected.

            Task snapshot:
            """.stripIndent());
        TaskDefinition task = run.taskSnapshot();
        appendValue(builder, "Run id", run.id());
        appendValue(builder, "Title", task.title());
        appendValue(builder, "Goal", task.goal());
        appendList(builder, "Inputs", run.inputValues().entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .toList());
        appendList(builder, "Declared outputs", task.outputs().stream().map(this::fieldSummary).toList());
        appendList(builder, "Steps", task.steps().stream().map(step -> step.order() + ". " + step.text()).toList());
        appendList(builder, "Validation criteria", task.validationCriteria());
        return builder.toString().trim();
    }

    private String fieldSummary(TaskFieldDefinition field) {
        return field.name() + " (" + field.type().wireName() + ", required=" + field.required() + "): " + field.description();
    }

    private void appendValue(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append(label).append(": ").append(value).append("\n");
        }
    }

    private void appendList(StringBuilder builder, String label, List<String> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        builder.append(label).append(":\n");
        for (String value : values) {
            builder.append("- ").append(value).append("\n");
        }
    }
}
