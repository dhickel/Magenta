package io.mindspice.magenta2.ai.orchestration.settings;

import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.config.user.ModelConfig;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeSettingsService {
    private final RuntimeSettingsRepository repository;
    private final AiConfig aiConfig;
    private final AgentProfileService agentProfileService;

    public RuntimeSettingsService(
        RuntimeSettingsRepository repository,
        AiConfig aiConfig,
        AgentProfileService agentProfileService
    ) {
        this.repository = repository;
        this.aiConfig = aiConfig;
        this.agentProfileService = agentProfileService;
    }

    public RuntimeSettings get() {
        return repository.find().orElseGet(() -> new RuntimeSettings(
            null,
            legacyDefaultAgentName(),
            aiConfig.resolvedDefaultModelKey(),
            aiConfig.resolvedPlanningModelKey(),
            aiConfig.resolvedSummaryModelKey(),
            aiConfig.resolvedCompactionModelKey(),
            aiConfig.resolvedContextBufferPercent(),
            aiConfig.resolvedDefaultModelKey(),
            null,
            null,
            aiConfig.resolvedContextBufferPercent(),
            true,
            -1,
            false
        ));
    }

    public RuntimeSettings save(RuntimeSettings settings) {
        RuntimeSettings normalized = normalizeModelReferences(settings);
        validateModel(normalized.defaultModel(), "defaultModel");
        validateModel(normalized.planningModel(), "planningModel");
        validateModel(normalized.summaryModel(), "summaryModel");
        validateModel(normalized.compactionModel(), "compactionModel");
        validateModel(normalized.systemChatModel(), "systemChatModel");
        Integer buffer = normalized.contextBufferPercent();
        if (buffer != null && (buffer < 1 || buffer > 50)) {
            throw new IllegalArgumentException("contextBufferPercent must be between 1 and 50");
        }
        Integer systemChatLimit = normalized.systemChatContextLimit();
        if (systemChatLimit != null && (systemChatLimit < 1 || systemChatLimit > 100)) {
            throw new IllegalArgumentException("systemChatContextLimit must be between 1 and 100");
        }
        Integer purgeDays = normalized.assignmentHistoryAutoPurgeDays();
        if (purgeDays != null && purgeDays != -1 && purgeDays < 1) {
            throw new IllegalArgumentException("assignmentHistoryAutoPurgeDays must be -1 or at least 1");
        }
        if (StringUtils.hasText(normalized.defaultAgentId())) {
            agentProfileService.get(normalized.defaultAgentId());
        }
        return repository.save(normalized);
    }

    public String resolveModel(String explicitRequestModel) {
        return remoteModelName(resolveModelKey(explicitRequestModel, null));
    }

    public String resolveModel(String explicitRequestModel, String agentDefaultModel) {
        return remoteModelName(resolveModelKey(explicitRequestModel, agentDefaultModel));
    }

    public String defaultModel() {
        return remoteModelName(defaultModelKey());
    }

    public String defaultModelKey() {
        return resolveModelKey(null, null);
    }

    public String planningModel() {
        return remoteModelName(planningModelKey());
    }

    public String planningModelKey() {
        RuntimeSettings settings = get();
        String key = StringUtils.hasText(settings.planningModel())
            ? settings.planningModel()
            : aiConfig.resolvedPlanningModelKey();
        return keyForModelOrRemoteName(key, aiConfig.resolvedPlanningModelKey());
    }

    public String compactionModel() {
        return remoteModelName(compactionModelKey());
    }

    public String compactionModelKey() {
        RuntimeSettings settings = get();
        String key = StringUtils.hasText(settings.compactionModel())
            ? settings.compactionModel()
            : (StringUtils.hasText(settings.summaryModel())
                ? settings.summaryModel()
                : aiConfig.resolvedCompactionModelKey());
        return keyForModelOrRemoteName(key, aiConfig.resolvedCompactionModelKey());
    }

    public String summaryModel() {
        return remoteModelName(summaryModelKey());
    }

    public String summaryModelKey() {
        RuntimeSettings settings = get();
        String key = StringUtils.hasText(settings.summaryModel())
            ? settings.summaryModel()
            : aiConfig.resolvedSummaryModelKey();
        return keyForModelOrRemoteName(key, aiConfig.resolvedSummaryModelKey());
    }

    public int contextBufferPercent() {
        Integer configured = get().contextBufferPercent();
        return configured == null ? aiConfig.resolvedContextBufferPercent() : configured;
    }

    public String defaultSystemPrompt() {
        RuntimeSettings settings = get();
        try {
            return agentProfileService.systemPrompt(settings.defaultAgentId(), settings.defaultAgentName());
        } catch (IllegalStateException exception) {
            String agentName = legacyDefaultAgentName();
            return aiConfig.agents() == null || aiConfig.agents().get(agentName) == null
                ? null
                : aiConfig.agents().get(agentName).systemPrompt();
        }
    }

    public String systemChatModel() {
        return remoteModelName(systemChatModelKey());
    }

    public String systemChatModelKey() {
        RuntimeSettings settings = get();
        String key = StringUtils.hasText(settings.systemChatModel())
            ? settings.systemChatModel()
            : settings.defaultModel();
        return keyForModelOrRemoteName(
            StringUtils.hasText(key) ? key : aiConfig.resolvedDefaultModelKey(),
            aiConfig.resolvedDefaultModelKey()
        );
    }

    public String systemChatPrompt() {
        RuntimeSettings settings = get();
        if (StringUtils.hasText(settings.systemChatPrompt())) {
            return settings.systemChatPrompt();
        }
        return defaultSystemPrompt();
    }

    public java.util.List<String> systemChatApprovedTools() {
        RuntimeSettings settings = get();
        if (StringUtils.hasText(settings.systemChatApprovedTools())) {
            return java.util.Arrays.stream(settings.systemChatApprovedTools().split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        }
        return approvedTools();
    }

    public int systemChatContextLimit() {
        RuntimeSettings settings = get();
        Integer limit = settings.systemChatContextLimit();
        return limit == null ? contextBufferPercent() : limit;
    }

    public boolean systemChatEnabled() {
        Boolean enabled = get().systemChatEnabled();
        return enabled == null || enabled;
    }

    public boolean retainTempWork() {
        return Boolean.TRUE.equals(get().retainTempWork());
    }

    public java.util.List<String> approvedTools() {
        RuntimeSettings settings = get();
        try {
            return agentProfileService.approvedTools(settings.defaultAgentId(), settings.defaultAgentName());
        } catch (IllegalStateException exception) {
            String agentName = legacyDefaultAgentName();
            return aiConfig.agents() == null || aiConfig.agents().get(agentName) == null
                ? java.util.List.of()
                : aiConfig.agents().get(agentName).approvedTools();
        }
    }

    public java.util.List<String> allowedShellCommands() {
        RuntimeSettings settings = get();
        try {
            return agentProfileService.allowedShellCommands(settings.defaultAgentId(), settings.defaultAgentName());
        } catch (IllegalStateException exception) {
            String agentName = legacyDefaultAgentName();
            return aiConfig.agents() == null || aiConfig.agents().get(agentName) == null
                ? java.util.List.of()
                : aiConfig.agents().get(agentName).allowedShellCommands();
        }
    }

    public AgentProfile defaultAgentProfile() {
        RuntimeSettings settings = get();
        return agentProfileService.defaultAgent(settings.defaultAgentId(), settings.defaultAgentName());
    }

    private String resolveModelKey(String explicitRequestModel, String agentDefaultModel) {
        if (StringUtils.hasText(explicitRequestModel)) {
            return keyForModelOrRemoteName(explicitRequestModel, null);
        }
        if (StringUtils.hasText(agentDefaultModel)) {
            return keyForModelOrRemoteName(agentDefaultModel, aiConfig.resolvedDefaultModelKey());
        }
        RuntimeSettings settings = get();
        if (StringUtils.hasText(settings.defaultModel())) {
            return keyForModelOrRemoteName(settings.defaultModel(), aiConfig.resolvedDefaultModelKey());
        }
        if (StringUtils.hasText(aiConfig.resolvedDefaultModelKey())) {
            return aiConfig.resolvedDefaultModelKey();
        }
        return legacyDefaultModelKey();
    }

    private String remoteModelName(String modelKey) {
        ModelConfig model = aiConfig.models().get(modelKey);
        if (model == null) {
            throw new IllegalArgumentException("Unknown configured model: " + modelKey);
        }
        return model.remoteModelName();
    }

    private String keyForModelOrRemoteName(String value, String preferredKey) {
        if (aiConfig.models().containsKey(value)) {
            return value;
        }
        if (remoteModelNameMatches(preferredKey, value)) {
            return preferredKey;
        }
        return aiConfig.models().entrySet().stream()
            .filter(entry -> value.equals(entry.getValue().remoteModelName()))
            .map(java.util.Map.Entry::getKey)
            .findFirst()
            .orElse(value);
    }

    private boolean remoteModelNameMatches(String modelKey, String remoteModelName) {
        if (!StringUtils.hasText(modelKey) || !StringUtils.hasText(remoteModelName)) {
            return false;
        }
        ModelConfig model = aiConfig.models().get(modelKey);
        return model != null && remoteModelName.equals(model.remoteModelName());
    }

    private RuntimeSettings normalizeModelReferences(RuntimeSettings settings) {
        return new RuntimeSettings(
            settings.defaultAgentId(),
            settings.defaultAgentName(),
            normalizeModelReference(settings.defaultModel(), aiConfig.resolvedDefaultModelKey()),
            normalizeModelReference(settings.planningModel(), aiConfig.resolvedPlanningModelKey()),
            normalizeModelReference(settings.summaryModel(), aiConfig.resolvedSummaryModelKey()),
            normalizeModelReference(settings.compactionModel(), aiConfig.resolvedCompactionModelKey()),
            settings.contextBufferPercent(),
            normalizeModelReference(settings.systemChatModel(), aiConfig.resolvedDefaultModelKey()),
            settings.systemChatPrompt(),
            settings.systemChatApprovedTools(),
            settings.systemChatContextLimit(),
            settings.systemChatEnabled(),
            settings.assignmentHistoryAutoPurgeDays(),
            settings.retainTempWork()
        );
    }

    private String normalizeModelReference(String value, String preferredKey) {
        return StringUtils.hasText(value) ? keyForModelOrRemoteName(value, preferredKey) : value;
    }

    private String legacyDefaultModelKey() {
        String agentName = legacyDefaultAgentName();
        if (StringUtils.hasText(agentName) && aiConfig.agents() != null && aiConfig.agents().get(agentName) != null) {
            return aiConfig.agents().get(agentName).model();
        }
        return aiConfig.resolvedSummaryModelKey();
    }

    private String legacyDefaultAgentName() {
        return StringUtils.hasText(aiConfig.defaultAgent()) ? aiConfig.defaultAgent() : "magenta";
    }

    private void validateModel(String modelKey, String field) {
        if (StringUtils.hasText(modelKey) && !aiConfig.models().containsKey(modelKey)) {
            throw new IllegalArgumentException(field + " references missing model: " + modelKey);
        }
    }
}
