package io.mindspice.magenta2.ai.skills;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AgentSkillActivationService {
    private static final List<String> RESOURCE_DIRECTORIES = List.of("scripts", "references", "assets");

    private final AgentSkillCatalogService catalogService;
    private final AgentSkillRepositoryService repositoryService;
    private final AgentSkillParser parser;
    private final AgentSkillRuntimeCatalogService runtimeCatalogService;
    private final AgentSkillAgentContextResolver agentContextResolver;
    private final Map<String, Set<String>> activeSkillsByConversation = new ConcurrentHashMap<>();

    public AgentSkillActivationService(
        AgentSkillCatalogService catalogService,
        AgentSkillRepositoryService repositoryService,
        AgentSkillParser parser,
        AgentSkillRuntimeCatalogService runtimeCatalogService,
        AgentSkillAgentContextResolver agentContextResolver
    ) {
        this.catalogService = catalogService;
        this.repositoryService = repositoryService;
        this.parser = parser;
        this.runtimeCatalogService = runtimeCatalogService;
        this.agentContextResolver = agentContextResolver;
    }

    public AgentSkillActivationResult activateSkill(String conversationId, String skillName) {
        if (!StringUtils.hasText(skillName)) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.INVALID_REQUEST,
                null,
                "skillName is required"
            );
        }
        String normalizedSkillName = skillName.trim();
        String resolvedConversationId = agentContextResolver.resolveConversationId(conversationId).orElse(null);

        List<AgentSkillCatalogEntry> availableCatalog = runtimeCatalogService.catalogForConversation(resolvedConversationId);
        if (availableCatalog.isEmpty()) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.NO_SKILLS_AVAILABLE,
                normalizedSkillName,
                "No assigned skills are available for this conversation."
            );
        }
        boolean assigned = availableCatalog.stream().anyMatch(entry -> normalizedSkillName.equals(entry.name()));
        if (!assigned) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.SKILL_UNASSIGNED,
                normalizedSkillName,
                "Skill is not assigned and enabled for this conversation."
            );
        }
        if (StringUtils.hasText(resolvedConversationId) && isAlreadyActive(resolvedConversationId, normalizedSkillName)) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.ALREADY_ACTIVE,
                normalizedSkillName,
                "Skill is already active in this conversation."
            );
        }

        AgentSkill skill = loadableSkillsByName().get(normalizedSkillName);
        if (skill == null || !StringUtils.hasText(skill.directorySlug()) || !StringUtils.hasText(skill.skillMdRootRelativePath())) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.SKILL_UNAVAILABLE,
                normalizedSkillName,
                "Skill metadata is unavailable or invalid."
            );
        }

        String skillMarkdown;
        Path skillDirectory;
        try {
            Path skillMarkdownPath = repositoryService.resolveSkillMarkdown(skill.directorySlug());
            skillDirectory = repositoryService.resolveSkillDirectory(skill.directorySlug());
            skillMarkdown = Files.readString(skillMarkdownPath, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException exception) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.SKILL_UNAVAILABLE,
                normalizedSkillName,
                "Failed to read SKILL.md for activation."
            );
        }

        AgentSkillParseResult parseResult = parser.parse(
            skillMarkdown,
            skill.directorySlug(),
            skill.skillMdRootRelativePath()
        );
        if (!parseResult.loadable()) {
            return AgentSkillActivationResult.failure(
                AgentSkillActivationOutcome.SKILL_UNAVAILABLE,
                normalizedSkillName,
                "SKILL.md is malformed and cannot be activated."
            );
        }

        List<String> resources = listResources(skillDirectory);
        String content = wrapSkillContent(
            normalizedSkillName,
            StringUtils.hasText(parseResult.body()) ? parseResult.body() : "",
            "skills/" + skill.directorySlug(),
            resources
        );

        if (StringUtils.hasText(resolvedConversationId)) {
            markActive(resolvedConversationId, normalizedSkillName);
        }
        return new AgentSkillActivationResult(
            AgentSkillActivationOutcome.ACTIVATED,
            normalizedSkillName,
            "Skill activated.",
            content,
            "skills/" + skill.directorySlug(),
            resources
        );
    }

    public void clearConversationActivations(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            activeSkillsByConversation.remove(conversationId.trim());
        }
    }

    private Map<String, AgentSkill> loadableSkillsByName() {
        Map<String, AgentSkill> skills = new LinkedHashMap<>();
        for (AgentSkill skill : catalogService.listAll()) {
            if (skill.status().loadable() && StringUtils.hasText(skill.name())) {
                skills.putIfAbsent(skill.name(), skill);
            }
        }
        return skills;
    }

    private boolean isAlreadyActive(String conversationId, String skillName) {
        Set<String> active = activeSkillsByConversation.get(conversationId);
        return active != null && active.contains(skillName);
    }

    private void markActive(String conversationId, String skillName) {
        activeSkillsByConversation.computeIfAbsent(conversationId, ignored -> ConcurrentHashMap.newKeySet()).add(skillName);
    }

    private List<String> listResources(Path skillDirectory) {
        Set<String> resources = new LinkedHashSet<>();
        for (String directoryName : RESOURCE_DIRECTORIES) {
            Path resourceRoot = skillDirectory.resolve(directoryName).normalize();
            if (!resourceRoot.startsWith(skillDirectory)) {
                continue;
            }
            if (!Files.isDirectory(resourceRoot, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resourceRoot)) {
                continue;
            }
            try (var walk = Files.walk(resourceRoot)) {
                walk.filter(path -> !path.equals(resourceRoot))
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> resources.add(skillDirectory.relativize(path).toString().replace('\\', '/')));
            } catch (IOException ignored) {
                // Resource listing is best-effort and intentionally does not fail activation.
            }
        }
        return new ArrayList<>(resources);
    }

    private String wrapSkillContent(String skillName, String body, String skillDirectory, List<String> resources) {
        StringBuilder builder = new StringBuilder();
        builder.append("<skill_content name=\"").append(skillName).append("\">\n");
        builder.append(body.strip()).append("\n\n");
        builder.append("Skill directory: ").append(skillDirectory).append("\n");
        builder.append("Relative paths in this skill are relative to the skill directory.\n");
        builder.append("<skill_resources>\n");
        for (String resource : resources) {
            builder.append("  <file>").append(resource).append("</file>\n");
        }
        builder.append("</skill_resources>\n");
        builder.append("</skill_content>");
        return builder.toString();
    }
}
