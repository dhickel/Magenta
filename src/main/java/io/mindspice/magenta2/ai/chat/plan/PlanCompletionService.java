package io.mindspice.magenta2.ai.chat.plan;

import java.nio.file.Files;
import java.nio.file.Path;
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
        Review whether the execution evidence, artifact contents, AND proposed final message satisfy every deliverable and validation criterion in the approved plan.
        The proposed final message will be delivered verbatim to the user after validation passes. Verify it accurately represents the completed work, is consistent with the evidence, and contains no unverified claims.
        Cross-reference each criterion against the provided evidence and artifact file contents.
        Return strict JSON only, with keys: complete, summary, findings, remediationSteps.
        complete must be true only when every deliverable and validation criterion is satisfied by specific, verifiable evidence (not just a claim of completion), and the final message accurately reflects the verified results.
        If incomplete, remediationSteps must name the exact unmet criterion and what specific evidence is missing or insufficient.
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
        List<String> artifactPaths,
        String finalMessage
    ) {
        List<String> reportedArtifactPaths = cleanList(artifactPaths);
        ExecutionPlan reported = planService.recordExecutionReport(
            conversationId,
            summary,
            evidence,
            deviations,
            unmetCriteria,
            reportedArtifactPaths
        );
        ValidationResult result = coverageValidation(reported, evidence)
            .orElseGet(() -> validate(reported, reportedArtifactPaths, finalMessage));
        List<String> feedback = feedback(result);
        planService.recordValidationFeedback(conversationId, feedback);
        if (result.complete()) {
            planService.markCompleted(conversationId, finalMessage);
            return "Plan validation passed. The plan is marked COMPLETED.\n\n" + renderFeedback(feedback);
        }
        return "Plan validation failed. Continue execution and address these remediation steps before calling plan_complete again.\n\n"
            + renderFeedback(feedback);
    }

    private java.util.Optional<ValidationResult> coverageValidation(ExecutionPlan plan, List<String> evidence) {
        List<String> criteria = cleanList(plan.acceptanceCriteria());
        if (criteria.isEmpty()) {
            return java.util.Optional.empty();
        }
        List<String> evidenceList = cleanList(evidence);
        List<String> missing = criteria.stream()
            .filter(criterion -> evidenceList.stream().noneMatch(entry -> criterion.equals(extractCriterionLabel(entry))))
            .toList();
        if (missing.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new ValidationResult(
            false,
            "Completion evidence did not address every validation criterion.",
            missing.stream()
                .map(criterion -> "Missing evidence for: " + criterion)
                .toList(),
            missing.stream()
                .map(criterion -> "Call plan_complete again with an evidence entry formatted as 'Criterion: "
                    + criterion + " | Evidence: <specific proof>'.")
                .toList()
        ));
    }

    private String extractCriterionLabel(String evidenceEntry) {
        if (!StringUtils.hasText(evidenceEntry)) {
            return null;
        }
        String text = evidenceEntry.trim();
        int prefix = text.indexOf("Criterion:");
        if (prefix < 0) {
            return null;
        }
        int start = prefix + "Criterion:".length();
        int end = text.indexOf("|", start);
        String label = end < 0 ? text.substring(start) : text.substring(start, end);
        return normalize(label);
    }

    private ValidationResult validate(ExecutionPlan plan, List<String> artifactPaths, String finalMessage) {
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
                    new UserMessage(validationInput(plan, artifactPaths, finalMessage))
                ),
                chatModelRouter.chatOptions(model)
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

    private String validationInput(ExecutionPlan plan, List<String> artifactPaths, String finalMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Approved plan:\n\n")
            .append(planService.approvalMarkdown(plan))
            .append("\n\nExecution evidence:\n");
        appendList(builder, plan.executionEvidence());
        if (!artifactPaths.isEmpty()) {
            builder.append("\nArtifact file contents:\n");
            for (String path : artifactPaths) {
                builder.append("--- ").append(path).append(" ---\n");
                builder.append(readArtifact(path)).append("\n");
            }
        }
        if (StringUtils.hasText(finalMessage)) {
            builder.append("\nProposed final message (will be delivered verbatim to the user):\n\n")
                .append(finalMessage).append("\n");
        }
        if (!plan.validationFeedback().isEmpty()) {
            builder.append("\nPrior validation feedback:\n");
            appendList(builder, plan.validationFeedback());
        }
        return builder.toString().trim();
    }

    private String readArtifact(String path) {
        if (!StringUtils.hasText(path)) {
            return "[empty artifact path]";
        }
        try {
            if (aiConfig == null || aiConfig.dataRoot() == null) {
                return "[data root not configured, cannot read " + path + "]";
            }
            Path root = aiConfig.dataRoot().toRealPath();
            Path filePath = Path.of(path);
            Path resolved = filePath.isAbsolute() ? filePath.normalize() : root.resolve(filePath).normalize();
            if (!resolved.startsWith(root)) {
                return "[artifact path escapes data root: " + path + "]";
            }
            if (!Files.isRegularFile(resolved)) {
                return "[artifact not found or not a regular file: " + path + "]";
            }
            String content = Files.readString(resolved);
            if (content.length() > 8000) {
                content = content.substring(0, 8000) + "\n... [truncated at 8000 chars]";
            }
            return content;
        } catch (Exception e) {
            return "[error reading artifact " + path + ": " + e.getMessage() + "]";
        }
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

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .toList();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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
