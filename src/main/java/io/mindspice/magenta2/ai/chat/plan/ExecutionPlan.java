package io.mindspice.magenta2.ai.chat.plan;

import io.mindspice.magenta2.ai.chat.model.PlanMode;
import java.time.Instant;
import java.util.List;

public record ExecutionPlan(
    String conversationId,
    PlanMode mode,
    PlanStatus status,
    String planningTask,
    String goal,
    String title,
    String summary,
    String notes,
    List<String> deliverables,
    List<String> inputs,
    List<String> outputs,
    List<String> assumptions,
    List<PlanStep> steps,
    List<String> acceptanceCriteria,
    List<String> executionEvidence,
    List<String> validationFeedback,
    String prePlanningModel,
    String executionModel,
    List<String> pendingQuestions,
    int pendingQuestionIndex,
    int planStartMessageOrder,
    String finalMessage,
    Instant createdAt,
    Instant updatedAt
) {
    public ExecutionPlan {
        deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        outputs = outputs == null ? List.of() : List.copyOf(outputs);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        steps = steps == null ? List.of() : List.copyOf(steps);
        acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
        executionEvidence = executionEvidence == null ? List.of() : List.copyOf(executionEvidence);
        validationFeedback = validationFeedback == null ? List.of() : List.copyOf(validationFeedback);
        pendingQuestions = pendingQuestions == null ? List.of() : List.copyOf(pendingQuestions);
        pendingQuestionIndex = Math.max(0, pendingQuestionIndex);
    }

    public ExecutionPlan(
        String conversationId,
        PlanMode mode,
        PlanStatus status,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> assumptions,
        List<PlanStep> steps,
        List<String> acceptanceCriteria,
        List<String> executionEvidence,
        int planStartMessageOrder,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            conversationId,
            mode,
            status,
            null,
            goal,
            title,
            summary,
            notes,
            List.of(),
            List.of(),
            List.of(),
            assumptions,
            steps,
            acceptanceCriteria,
            executionEvidence,
            List.of(),
            null,
            null,
            List.of(),
            0,
            planStartMessageOrder,
            null,
            createdAt,
            updatedAt
        );
    }

    public ExecutionPlan(
        String conversationId,
        PlanMode mode,
        PlanStatus status,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> deliverables,
        List<String> assumptions,
        List<PlanStep> steps,
        List<String> acceptanceCriteria,
        List<String> executionEvidence,
        String prePlanningModel,
        PlanPrompt pendingPrompt,
        List<PlanAnswer> answerHistory,
        int planStartMessageOrder,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(
            conversationId,
            mode,
            status,
            null,
            goal,
            title,
            summary,
            notes,
            deliverables,
            List.of(),
            List.of(),
            assumptions,
            steps,
            acceptanceCriteria,
            executionEvidence,
            List.of(),
            prePlanningModel,
            null,
            pendingPrompt != null && pendingPrompt.active() ? List.of(pendingPrompt.question()) : List.of(),
            0,
            planStartMessageOrder,
            null,
            createdAt,
            updatedAt
        );
    }

    public boolean hasSavedPlan() {
        return goal != null && !goal.isBlank()
            && steps != null && !steps.isEmpty()
            && ((deliverables != null && !deliverables.isEmpty()) || (outputs != null && !outputs.isEmpty()))
            && acceptanceCriteria != null && !acceptanceCriteria.isEmpty();
    }

    public boolean hasPendingQuestion() {
        return pendingQuestions != null
            && !pendingQuestions.isEmpty()
            && pendingQuestionIndex >= 0
            && pendingQuestionIndex < pendingQuestions.size();
    }

    public String currentQuestion() {
        return hasPendingQuestion() ? pendingQuestions.get(pendingQuestionIndex) : null;
    }

    // --- Wither helpers ---

    private ExecutionPlan derive(
        PlanMode mode,
        PlanStatus status,
        String planningTask,
        String goal,
        String title,
        String summary,
        String notes,
        List<String> deliverables,
        List<String> inputs,
        List<String> outputs,
        List<String> assumptions,
        List<PlanStep> steps,
        List<String> acceptanceCriteria,
        List<String> executionEvidence,
        List<String> validationFeedback,
        String prePlanningModel,
        String executionModel,
        List<String> pendingQuestions,
        int pendingQuestionIndex,
        String finalMessage
    ) {
        return new ExecutionPlan(
            this.conversationId, mode, status, planningTask,
            goal, title, summary, notes,
            deliverables, inputs, outputs, assumptions,
            steps, acceptanceCriteria,
            executionEvidence, validationFeedback,
            prePlanningModel, executionModel,
            pendingQuestions, pendingQuestionIndex,
            this.planStartMessageOrder, finalMessage,
            this.createdAt, Instant.now()
        );
    }

    public ExecutionPlan withModeStatus(PlanMode mode, PlanStatus status) {
        return derive(mode, status, this.planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withPlanningTask(String planningTask) {
        return derive(this.mode, this.status, planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withGoal(String goal) {
        return derive(this.mode, this.status, this.planningTask, goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withTitle(String title) {
        return derive(this.mode, this.status, this.planningTask, this.goal, title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withPendingQuestions(List<String> pendingQuestions, int pendingQuestionIndex) {
        return derive(this.mode, this.status, this.planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            pendingQuestions, pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withExecutionEvidence(List<String> executionEvidence) {
        return derive(this.mode, this.status, this.planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withValidationFeedback(List<String> validationFeedback) {
        return derive(this.mode, this.status, this.planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, this.finalMessage);
    }

    public ExecutionPlan withFinalMessage(String finalMessage) {
        return derive(this.mode, this.status, this.planningTask, this.goal, this.title, this.summary, this.notes,
            this.deliverables, this.inputs, this.outputs, this.assumptions, this.steps, this.acceptanceCriteria,
            this.executionEvidence, this.validationFeedback, this.prePlanningModel, this.executionModel,
            this.pendingQuestions, this.pendingQuestionIndex, finalMessage);
    }
}
