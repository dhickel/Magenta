package io.mindspice.magenta2.ai.chat.task;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskDraft(
    String conversationId,
    TaskDraftStatus status,
    String planningTask,
    String title,
    String summary,
    String goal,
    String notes,
    String inputDescription,
    List<TaskFieldDefinition> inputs,
    String outputDescription,
    List<TaskFieldDefinition> outputs,
    List<String> assumptions,
    List<TaskStep> steps,
    List<String> validationCriteria,
    List<String> pendingQuestions,
    int pendingQuestionIndex,
    String prePlanningModel,
    String executionModel,
    String createdTaskId,
    Instant createdAt,
    Instant updatedAt
) {
    public TaskDraft {
        status = status == null ? TaskDraftStatus.DRAFT : status;
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        steps = steps == null ? List.of() : List.copyOf(steps);
        validationCriteria = validationCriteria == null ? List.of() : List.copyOf(validationCriteria);
        pendingQuestions = pendingQuestions == null ? List.of() : List.copyOf(pendingQuestions);
        pendingQuestionIndex = Math.max(0, pendingQuestionIndex);
    }

    public boolean hasPendingQuestion() {
        return !pendingQuestions.isEmpty() && pendingQuestionIndex < pendingQuestions.size();
    }

    public String currentQuestion() {
        return hasPendingQuestion() ? pendingQuestions.get(pendingQuestionIndex) : null;
    }
}
