package io.mindspice.magenta2.ai.chat.plan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mindspice.magenta2.ai.chat.service.ChatModelRouter;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PlanCompletionService {
    private static final String VALIDATOR_SYSTEM_PROMPT = """
        You are Magenta's plan validator.
        Review only whether the execution evidence satisfies the approved plan.
        Return strict JSON only, with keys: complete, summary, findings, remediationSteps.
        complete must be true only when every deliverable and validation criterion is satisfied by evidence.
        If incomplete, remediationSteps must be detailed and specific enough for the implementing agent to continue.
        """;

    private final PlanService planService;
    private final ChatModelRouter chatModelRouter;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    @Autowired
    public PlanCompletionService(
        PlanService planService,
        @Autowired(required = false) ChatModelRouter chatModelRouter,
        @Autowired(required = false) AiConfig aiConfig,
        ObjectMapper objectMapper
    ) {
        this.planService = planService;
        this.chatModelRouter = chatModelRouter;
        this.aiConfig = aiConfig;
        this.objectMapper = objectMapper;
    }

    public String complete(
        String conversationId,
        String summary,
        List<String> evidence,
        List<String> deviations,
        List<String> unmetCriteria,
        List<String> artifactPaths
    ) {
        ExecutionPlan reported = planService.recordExecutionReport(
            conversationId,
            summary,
            evidence,
            deviations,
            unmetCriteria,
            artifactPaths
        );
        ValidationResult result = validate(reported);
        List<String> feedback = feedback(result);
        planService.recordValidationFeedback(conversationId, feedback);
        if (result.complete()) {
            planService.markCompleted(conversationId);
            return "Plan validation passed. The plan is marked COMPLETED.\n\n" + renderFeedback(feedback);
        }
        return "Plan validation failed. Continue execution and address these remediation steps before calling plan_complete again.\n\n"
            + renderFeedback(feedback);
    }

    private ValidationResult validate(ExecutionPlan plan) {
        if (chatModelRouter == null || aiConfig == null || aiConfig.models() == null) {
            return new ValidationResult(
                false,
                "Validator model is not configured.",
                List.of("No validator model/router was available."),
                List.of("Review execution evidence manually and call plan_complete again after validator configuration is available.")
            );
        }
        String model = validatorModel(plan);
        if (!StringUtils.hasText(model)) {
            return new ValidationResult(
                false,
                "Validator model could not be resolved.",
                List.of("No planning or fallback model was available."),
                List.of("Configure planningModel or preserve the pre-planning model before validating completion.")
            );
        }
        String response = chatModelRouter.chatClient(model)
            .prompt(new Prompt(
                List.of(
                    new SystemMessage(VALIDATOR_SYSTEM_PROMPT),
                    new UserMessage(validationInput(plan))
                ),
                chatModelRouter.ollamaOptions(model)
            ))
            .call()
            .content();
        return parseValidation(response);
    }

    private String validatorModel(ExecutionPlan plan) {
        String planningModelKey = aiConfig.resolvedPlanningModelKey();
        ModelConfig planningModel = aiConfig.models().get(planningModelKey);
        if (planningModel != null && StringUtils.hasText(planningModel.remoteModelName())) {
            return planningModel.remoteModelName();
        }
        if (StringUtils.hasText(plan.executionModel())) {
            return plan.executionModel();
        }
        if (StringUtils.hasText(plan.prePlanningModel())) {
            return plan.prePlanningModel();
        }
        return null;
    }

    private String validationInput(ExecutionPlan plan) {
        StringBuilder builder = new StringBuilder();
        builder.append("Approved plan:\n\n")
            .append(planService.approvalMarkdown(plan))
            .append("\n\nExecution evidence:\n");
        appendList(builder, plan.executionEvidence());
        if (!plan.validationFeedback().isEmpty()) {
            builder.append("\nPrior validation feedback:\n");
            appendList(builder, plan.validationFeedback());
        }
        return builder.toString().trim();
    }

    private ValidationResult parseValidation(String response) {
        if (!StringUtils.hasText(response)) {
            return new ValidationResult(
                false,
                "Validator returned an empty response.",
                List.of("No validation JSON was produced."),
                List.of("Call plan_complete again with clearer evidence.")
            );
        }
        try {
            Map<String, Object> value = objectMapper.readValue(jsonPayload(response), new TypeReference<>() { });
            return new ValidationResult(
                Boolean.TRUE.equals(value.get("complete")),
                string(value.get("summary"), "Validator did not provide a summary."),
                stringList(value.get("findings")),
                stringList(value.get("remediationSteps"))
            );
        } catch (JsonProcessingException exception) {
            return new ValidationResult(
                false,
                "Validator response was not valid JSON.",
                List.of(response.trim()),
                List.of("Call plan_complete again after adding clearer, structured execution evidence.")
            );
        }
    }

    private String jsonPayload(String response) {
        String text = response.trim();
        if (text.startsWith("```")) {
            text = text.replaceFirst("(?s)^```(?:json)?\\s*", "");
            text = text.replaceFirst("(?s)\\s*```$", "");
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end > start ? text.substring(start, end + 1) : text;
    }

    private List<String> feedback(ValidationResult result) {
        List<String> feedback = new ArrayList<>();
        feedback.add("Validator summary: " + result.summary());
        addPrefixed(feedback, "Finding", result.findings());
        addPrefixed(feedback, "Remediation", result.remediationSteps());
        return List.copyOf(feedback);
    }

    private void addPrefixed(List<String> target, String label, List<String> values) {
        for (String value : values == null ? List.<String>of() : values) {
            if (StringUtils.hasText(value)) {
                target.add(label + ": " + value.trim());
            }
        }
    }

    private String renderFeedback(List<String> feedback) {
        StringBuilder builder = new StringBuilder();
        appendList(builder, feedback);
        return builder.toString().trim();
    }

    private void appendList(StringBuilder builder, List<String> values) {
        for (String value : values == null ? List.<String>of() : values) {
            builder.append("- ").append(value).append("\n");
        }
    }

    private String string(Object value, String fallback) {
        return value instanceof String text && StringUtils.hasText(text) ? text.trim() : fallback;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .filter(item -> item instanceof String)
                .map(item -> ((String) item).trim())
                .filter(StringUtils::hasText)
                .toList();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            return List.of(text.trim());
        }
        return List.of();
    }

    record ValidationResult(
        boolean complete,
        String summary,
        List<String> findings,
        List<String> remediationSteps
    ) {
        ValidationResult {
            findings = findings == null ? List.of() : List.copyOf(findings);
            remediationSteps = remediationSteps == null ? List.of() : List.copyOf(remediationSteps);
        }
    }
}
