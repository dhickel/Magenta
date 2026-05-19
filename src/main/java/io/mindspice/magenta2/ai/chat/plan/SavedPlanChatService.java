package io.mindspice.magenta2.ai.chat.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SavedPlanChatService {
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
        PlanDefinition plan = planService.saveTask(new PlanDefinition(
            UUID.randomUUID().toString(), PlanKind.TASK_TEMPLATE, PlanStatus.DRAFT,
            "Untitled Plan", null, null, null,
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), WorkTypeProfile.CODING_CENTRIC.name(),
            null, null, null, "saved_plan_opening_questions",
            OPENING_QUESTIONS, 0, 0, null, null, null, null
        ));
        repository.append(plan.id(), "assistant", plan.currentQuestion());
        return state(plan.id());
    }

    public SavedPlanChatState start(String planId, String instruction) {
        PlanDefinition plan = planService.getTask(planId);
        List<PlanChatMessage> messages = repository.findByPlanId(plan.id());
        if (messages.isEmpty()) {
            PlanDefinition seeded = planService.saveTask(plan
                .withPlanningTask("saved_plan_opening_questions")
                .withPendingQuestions(OPENING_QUESTIONS, 0));
            repository.append(seeded.id(), "assistant", seeded.currentQuestion());
        }
        if (StringUtils.hasText(instruction)) {
            repository.append(plan.id(), "user", instruction.trim());
            repository.append(plan.id(), "assistant", "What should change in this saved plan?");
        }
        return state(plan.id());
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
            updated = planService.saveTask(updated.withPendingQuestions(List.of(), 0).withPlanningTask("draft_saved_plan"));
            repository.append(updated.id(), "assistant", "Saved plan draft updated. Continue editing manually or describe the next change.");
        }
        return state(updated.id());
    }

    public SavedPlanChatState message(String planId, String message) {
        PlanDefinition plan = planService.getTask(planId);
        repository.append(plan.id(), "user", message);
        repository.append(plan.id(), "assistant", "Saved plan chat recorded. Use the editor fields for precise manual changes in this pass.");
        return state(plan.id());
    }

    public SavedPlanChatState state(String planId) {
        PlanDefinition plan = planService.getTask(planId);
        return new SavedPlanChatState(plan, repository.findByPlanId(planId), plan.currentQuestion());
    }

    public void deleteMessages(String planId) {
        repository.deleteByPlanId(planId);
    }

    private PlanDefinition applyOpeningAnswer(PlanDefinition plan, int index, String answer) {
        String value = StringUtils.hasText(answer) ? answer.trim() : "";
        return switch (index) {
            case 0 -> planService.saveTask(plan
                .withGoal(value)
                .withTitle(titleFromGoal(value)));
            case 1 -> planService.saveTask(plan.withInputs(parseFields(value, true)));
            case 2 -> planService.saveTask(plan.withDeliverables(parseLines(value)));
            case 3 -> planService.saveTask(plan.withOutputs(parseFields(value, false)));
            default -> plan;
        };
    }

    private String titleFromGoal(String value) {
        if (!StringUtils.hasText(value)) {
            return "Untitled Plan";
        }
        String singleLine = value.replace('\n', ' ').trim();
        return singleLine.length() > 80 ? singleLine.substring(0, 80).trim() : singleLine;
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
