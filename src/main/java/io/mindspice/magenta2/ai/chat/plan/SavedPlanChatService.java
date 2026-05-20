package io.mindspice.magenta2.ai.chat.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SavedPlanChatService {
    private static final String OPENING_TASK = "saved_plan_opening_questions";
    private static final String DRAFT_TASK = "draft_saved_plan";
    private static final String RESUME_TASK = "saved_plan_resume_chat";
    private static final String OPENING_COMPLETE_MESSAGE =
        "Saved plan draft updated. Continue editing manually or describe the next change.";
    private static final String DRAFT_RESUME_QUESTION = "Any details you want to provide before continuing?";
    private static final String CHANGE_REQUEST_QUESTION = "What do you need to change in this plan?";
    private static final String MESSAGE_QUESTION = "What field should be updated?";

    private static final List<String> OPENING_QUESTIONS = List.of(
        "What is the goal?",
        "What specific runtime inputs should this saved plan accept? Include field names, types, required flags, array flags, schema/examples, or say \"no inputs\".",
        "What are the high-level deliverables? Outputs are asked next.",
        "What specific structured outputs should this saved plan produce for workflow chaining or downstream use? Include field names, types, required flags, array flags, schema/examples, or say \"no outputs\"."
    );

    private final PlanService planService;
    private final PlanChatRepository repository;

    public SavedPlanChatService(PlanService planService, PlanChatRepository repository) {
        this.planService = planService;
        this.repository = repository;
    }

    public SavedPlanChatState create() {
        return create("New Plan Chat");
    }

    public SavedPlanChatState create(String title) {
        String resolvedTitle = StringUtils.hasText(title) ? title.trim() : "Untitled Plan";
        PlanDefinition plan = planService.saveTask(new PlanDefinition(
            UUID.randomUUID().toString(), PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            resolvedTitle, null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), WorkTypeProfile.CODING_CENTRIC.name(),
            null, null, null, OPENING_TASK,
            OPENING_QUESTIONS, 0, 0, null, null, null, null
        ));
        repository.append(plan.id(), "assistant", plan.currentQuestion());
        return state(plan.id());
    }

    public SavedPlanChatState start(String planId, String instruction) {
        PlanDefinition plan = planService.getTask(planId);
        List<PlanChatMessage> messages = repository.findByPlanId(plan.id());
        if (messages.isEmpty()) {
            String question = resumeQuestion(plan);
            PlanDefinition seeded = planService.saveTask(plan
                .withPlanningTask(RESUME_TASK)
                .withPendingQuestions(List.of(question), 0));
            repository.append(seeded.id(), "assistant", seeded.currentQuestion());
            return new SavedPlanChatState(planService.getTask(seeded.id()), repository.findByPlanId(seeded.id()), question);
        }
        if (StringUtils.hasText(instruction)) {
            repository.append(plan.id(), "user", instruction.trim());
        }
        String question = resumeQuestion(plan);
        if (!StringUtils.hasText(instruction) && lastMessageIs(messages, "assistant", question)) {
            return new SavedPlanChatState(plan, messages, question);
        }
        planService.saveTask(plan
            .withPlanningTask(RESUME_TASK)
            .withPendingQuestions(List.of(question), 0));
        repository.append(plan.id(), "assistant", question);
        return new SavedPlanChatState(planService.getTask(plan.id()), repository.findByPlanId(plan.id()), question);
    }

    public SavedPlanChatState answer(String planId, String answer) {
        PlanDefinition plan = planService.getTask(planId);
        if (!plan.hasPendingQuestion()) {
            repository.append(plan.id(), "user", answer);
            repository.append(plan.id(), "assistant", "What else should be changed in this saved plan?");
            return state(plan.id());
        }
        int index = plan.pendingQuestionIndex();
        repository.append(plan.id(), "user", answer);
        PlanDefinition updated = applyOpeningAnswer(plan, index, answer);
        int nextIndex = index + 1;
        if (nextIndex < OPENING_QUESTIONS.size()) {
            updated = planService.saveTask(updated.withPendingQuestions(OPENING_QUESTIONS, nextIndex));
            repository.append(updated.id(), "assistant", updated.currentQuestion());
        } else {
            updated = planService.saveTask(updated.withPendingQuestions(List.of(), 0).withPlanningTask(DRAFT_TASK));
            repository.append(updated.id(), "assistant", OPENING_COMPLETE_MESSAGE);
        }
        return state(updated.id());
    }

    public SavedPlanChatState message(String planId, String message) {
        PlanDefinition plan = planService.getTask(planId);
        repository.append(plan.id(), "user", message);
        repository.append(plan.id(), "assistant", MESSAGE_QUESTION);
        return state(plan.id());
    }

    public void appendEditorSaveContext(PlanDefinition before, PlanDefinition after) {
        if (before == null || after == null || !StringUtils.hasText(after.id())) {
            return;
        }
        if (repository.findByPlanId(after.id()).isEmpty()) {
            return;
        }
        String diff = editorDiff(before, after);
        if (StringUtils.hasText(diff)) {
            repository.append(after.id(), "user", "Saved editor updates: " + diff);
        }
    }

    public SavedPlanChatState state(String planId) {
        PlanDefinition plan = planService.getTask(planId);
        return new SavedPlanChatState(plan, repository.findByPlanId(planId), plan.currentQuestion());
    }

    public void deleteMessages(String planId) {
        repository.deleteByPlanId(planId);
    }

    private String resumeQuestion(PlanDefinition plan) {
        return isFinalized(plan.status()) ? CHANGE_REQUEST_QUESTION : DRAFT_RESUME_QUESTION;
    }

    private boolean isFinalized(PlanStatus status) {
        if (status == null) {
            return false;
        }
        String name = status.name();
        return "APPROVED".equals(name) || "SAVED_TASK".equals(name) || "COMPLETED".equals(name);
    }

    private boolean lastMessageIs(List<PlanChatMessage> messages, String role, String text) {
        if (messages.isEmpty()) {
            return false;
        }
        PlanChatMessage last = messages.get(messages.size() - 1);
        return role.equalsIgnoreCase(last.role()) && text.equals(last.text());
    }

    private String editorDiff(PlanDefinition before, PlanDefinition after) {
        List<String> changes = new ArrayList<>();
        addChange(changes, "title", before.title(), after.title());
        addChange(changes, "summary", before.summary(), after.summary());
        addChange(changes, "goal", before.goal(), after.goal());
        addChange(changes, "notes", before.notes(), after.notes());
        addChange(changes, "manager type", before.promptProfile(), after.promptProfile());
        addChange(changes, "planning model", before.planningModel(), after.planningModel());
        addChange(changes, "execution model", before.executionModel(), after.executionModel());
        addChange(changes, "planning task", before.planningTask(), after.planningTask());
        addChange(changes, "final message", before.finalMessage(), after.finalMessage());
        addChange(changes, "settings override JSON", before.settingsOverrideJson(), after.settingsOverrideJson());
        return String.join("; ", changes);
    }

    private void addChange(List<String> changes, String field, String before, String after) {
        String oldValue = normalize(before);
        String newValue = normalize(after);
        if (!oldValue.equals(newValue)) {
            changes.add(field + " changed from " + quote(oldValue) + " to " + quote(newValue));
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    private String quote(String value) {
        if (!StringUtils.hasText(value)) {
            return "(blank)";
        }
        String trimmed = value.length() > 80 ? value.substring(0, 77) + "..." : value;
        return "\"" + trimmed + "\"";
    }

    private PlanDefinition applyOpeningAnswer(PlanDefinition plan, int index, String answer) {
        String value = StringUtils.hasText(answer) ? answer.trim() : "";
        return switch (index) {
            case 0 -> planService.saveTask(plan.withGoal(value));
            case 1 -> planService.saveTask(plan.withInputs(parseFields(value, true)));
            case 2 -> planService.saveTask(plan.withDeliverables(parseLines(value)));
            case 3 -> planService.saveTask(plan.withOutputs(parseFields(value, false)));
            default -> plan;
        };
    }

    private List<String> parseLines(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        return value.lines()
            .map(line -> line.replaceFirst("^\\s*[-*0-9.)]+\\s*", "").trim())
            .filter(line -> !line.isBlank())
            .toList();
    }

    private List<PlanFieldDefinition> parseFields(String value, boolean inputs) {
        if (!StringUtils.hasText(value) || value.trim().equalsIgnoreCase(inputs ? "no inputs" : "no outputs")) {
            return List.of();
        }
        List<PlanFieldDefinition> fields = new ArrayList<>();
        for (String line : parseLines(value)) {
            String[] parts = line.split(":", 2);
            String name = parts[0].trim().replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
            if (name.isBlank()) {
                continue;
            }
            String detail = parts.length > 1 ? parts[1].trim() : line;
            String lower = line.toLowerCase(Locale.ROOT);
            PlanFieldType type = lower.contains("json") || lower.contains("schema") ? PlanFieldType.JSON
                : lower.contains("file") ? PlanFieldType.FILE_PATH
                : lower.contains("number") ? PlanFieldType.NUMBER
                : PlanFieldType.STRING;
            fields.add(new PlanFieldDefinition(
                name,
                type,
                lower.contains("array") || lower.contains("list"),
                detail,
                !lower.contains("optional"),
                null
            ));
        }
        return List.copyOf(fields);
    }

    public record SavedPlanChatState(
        PlanDefinition plan,
        List<PlanChatMessage> messages,
        String promptQuestion
    ) {
    }
}
