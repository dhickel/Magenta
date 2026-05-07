package io.mindspice.magenta2.ai.orchestration.agents;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

import io.mindspice.magenta2.ai.config.user.AgentConfig;
import io.mindspice.magenta2.ai.config.user.AiConfig;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettings;
import io.mindspice.magenta2.ai.orchestration.settings.RuntimeSettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AgentProfileSeeder implements ApplicationRunner {
    private final AgentProfileRepository repository;
    private final AgentProfileService service;
    private final RuntimeSettingsRepository settingsRepository;
    private final AiConfig aiConfig;

    public AgentProfileSeeder(
        AgentProfileRepository repository,
        AgentProfileService service,
        RuntimeSettingsRepository settingsRepository,
        AiConfig aiConfig
    ) {
        this.repository = repository;
        this.service = service;
        this.settingsRepository = settingsRepository;
        this.aiConfig = aiConfig;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!repository.isEmpty()) {
            return;
        }
        String id = UUID.randomUUID().toString();
        String legacyName = StringUtils.hasText(aiConfig.defaultAgent()) ? aiConfig.defaultAgent() : "magenta";
        AgentConfig legacy = aiConfig.agents() == null ? null : aiConfig.agents().get(legacyName);
        String model = legacy != null && StringUtils.hasText(legacy.model())
            ? legacy.model()
            : (StringUtils.hasText(aiConfig.resolvedDefaultModelKey())
                ? aiConfig.resolvedDefaultModelKey()
                : aiConfig.resolvedSummeryModelKey());
        String prompt = legacy == null ? fallbackPrompt() : legacy.systemPrompt();
        AgentProfile seeded = service.create(new AgentProfile(
            id,
            "magenta",
            AgentProfileStatus.ACTIVE,
            model,
            prompt,
            legacy == null ? List.of() : legacy.approvedTools(),
            legacy == null ? List.of() : legacy.allowedShellCommands(),
            true,
            null,
            null
        ));
        settingsRepository.save(new RuntimeSettings(
            seeded.id(),
            seeded.name(),
            model,
            aiConfig.resolvedPlanningModelKey(),
            aiConfig.resolvedSummeryModelKey(),
            aiConfig.resolvedCompactionModelKey(),
            aiConfig.resolvedContextBufferPercent()
        ));
    }

    private String fallbackPrompt() {
        java.nio.file.Path prompt = java.nio.file.Path.of("config/prompts/system.md");
        if (Files.isRegularFile(prompt)) {
            try {
                return Files.readString(prompt);
            } catch (java.io.IOException ignored) {
            }
        }
        return "You are Magenta, a helpful operational assistant.";
    }
}
