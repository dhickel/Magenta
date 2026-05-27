# Agent Skills Specification — Implementation Research Report

> **Source:** [Agent Skills Specification](https://agentskills.io/specification)  
> **Client Implementation Guide:** [Adding Skills Support](https://agentskills.io/client-implementation/adding-skills-support.md)  
> **Date:** 2025-07-17  
> **Status:** Research — for implementation team review

---

## 1. Specification Summary

### 1.1 What Are Agent Skills?

Agent Skills are a lightweight, open format for extending AI agent capabilities with specialized knowledge and workflows. A skill is a **directory** containing, at minimum, a `SKILL.md` file with YAML frontmatter + Markdown body, plus optional `scripts/`, `references/`, and `assets/` subdirectories.

```
skill-name/
├── SKILL.md          # Required: YAML frontmatter + Markdown instructions
├── scripts/          # Optional: executable code (Python, Bash, JS, etc.)
├── references/       # Optional: additional documentation loaded on demand
├── assets/           # Optional: templates, images, data files
└── ...               # Any additional files
```

### 1.2 SKILL.md Frontmatter Specification

| Field | Required | Constraints |
|-------|----------|-------------|
| `name` | **Yes** | 1-64 chars. Lowercase `a-z`, `0-9`, hyphens only. No leading/trailing hyphens. No consecutive hyphens (`--`). **Must match parent directory name.** |
| `description` | **Yes** | 1-1024 chars. Describes what the skill does AND when to use it. Include keywords for agent matching. |
| `license` | No | License name or reference to bundled license file. |
| `compatibility` | No | 1-500 chars. Environment requirements (intended product, system packages, network access). |
| `metadata` | No | Arbitrary `key: value` map for additional properties (e.g., `author`, `version`). |
| `allowed-tools` | No | Space-separated string of pre-approved tools. **Experimental.** |

**Minimal valid SKILL.md:**

```markdown
---
name: pdf-processing
description: Extract PDF text, fill forms, merge files. Use when handling PDFs.
---
# PDF Processing Skill
...instructions...
```

### 1.3 Progressive Disclosure — The Core Principle

Skills are loaded in **three tiers**, each progressively larger:

| Tier | What's Loaded | When | Token Cost |
|------|--------------|------|------------|
| **1. Catalog** | `name` + `description` only | Session startup | ~50-100 tokens per skill |
| **2. Instructions** | Full `SKILL.md` body (frontmatter stripped or not) | When skill is activated | < 5,000 tokens (recommended) |
| **3. Resources** | Scripts, references, assets | When instructions reference them | Varies |

**Key insight:** An agent with 20 installed skills pays only the catalog cost upfront (~1,000-2,000 tokens), not 20× full instruction sets. The full instructions load only for skills actually used in a conversation.

### 1.4 Skill Discovery — Directory Scanning

Skills live in scoped directories. The spec defines two canonical scopes, plus client-specific directories:

| Scope | Path | Purpose |
|-------|------|---------|
| Project (client-native) | `<project>/.<client>/skills/` | Your client's location |
| Project (cross-client) | `<project>/.agents/skills/` | Cross-client interoperability |
| User (client-native) | `~/.<client>/skills/` | Your client's location |
| User (cross-client) | `~/.agents/skills/` | Cross-client interoperability |

**Scanning rules:**
- Look for subdirectories containing a file named exactly `SKILL.md`
- Skip `.git/`, `node_modules/`, etc.
- Optionally respect `.gitignore`
- Set reasonable bounds (max depth 4-6, max 2,000 directories)

**Name collision precedence:** Project-level skills **override** user-level skills. Within the same scope, pick first-found or last-found consistently; log a warning when a collision occurs.

**Trust considerations:** Project-level skills come from the repository (potentially untrusted). Consider gating on a trust check — only load project-level skills if the user has marked the project folder as trusted.

### 1.5 Parsing SKILL.md

1. Find opening `---` at start of file, closing `---` after it
2. Parse YAML block between them → extract `name`, `description`, optional fields
3. Everything after closing `---`, trimmed → skill body content

**Lenient validation guidance:**
- `name` doesn't match directory → warn, load anyway
- `name` exceeds 64 chars → warn, load anyway
- `description` missing/empty → **skip the skill** (essential for disclosure), log error
- YAML completely unparseable → **skip the skill**, log error
- Unquoted colons in values → attempt fallback quoting before failing

**Stored record per skill (minimum):**

| Field | Description |
|-------|-------------|
| `name` | From frontmatter |
| `description` | From frontmatter |
| `location` | Absolute path to `SKILL.md` |
| `baseDirectory` | Parent of `location` (derived, used for relative path resolution) |

Body can be stored at discovery time or read at activation time. Storing makes activation faster; reading at activation uses less memory and picks up live changes.

### 1.6 Disclosing Skills to the Model (Catalog)

Two placement approaches:

**A) System prompt section** — Add catalog as a labeled section in the system prompt, with brief behavioral instructions. Simplest approach, works with any model that has file-reading capability.

**B) Tool description** — Embed catalog in description of a dedicated skill-activation tool. Keeps system prompt clean, couples discovery with activation.

**Example catalog format (XML):**

```xml
<available_skills>
  <skill>
    <name>pdf-processing</name>
    <description>Extract PDF text, fill forms, merge files. Use when handling PDFs.</description>
    <location>/home/user/.agents/skills/pdf-processing/SKILL.md</location>
  </skill>
</available_skills>
```

**Behavioral instructions (file-read activation):**

```
The following skills provide specialized instructions for specific tasks.
When a task matches a skill's description, use your file-read tool to load
the SKILL.md at the listed location before proceeding.
When a skill references relative paths, resolve them against the skill's
directory (the parent of SKILL.md) and use absolute paths in tool calls.
```

**Filtering:** Hide filtered skills entirely from the catalog (don't list them with a block). When no skills are available, omit the catalog and instructions entirely.

### 1.7 Activating Skills

**Two activation patterns:**

| Pattern | Mechanism | Best For |
|---------|-----------|----------|
| **File-read activation** | Model calls its standard file-read tool with the `SKILL.md` path from catalog | Simplest; model already has file access |
| **Dedicated tool activation** | Register an `activate_skill` tool that takes a skill name and returns content | Models without file access; also useful for wrapping, permissions, analytics |

**Dedicated tool advantages:**
- Control what content is returned (strip frontmatter or preserve it)
- Wrap content in structured tags for context management identification
- List bundled resources (scripts, references) alongside instructions
- Enforce permissions or prompt for user consent
- Track activation for analytics
- Constrain `name` parameter to valid skill names (enum in tool schema) to prevent hallucination

**User-explicit activation:** Slash commands (`/skill-name`) or mention syntax intercepted by the harness. Autocomplete widget for discoverability.

**What the model receives:**
- **Full file:** Entire `SKILL.md` including YAML frontmatter (natural with file-read activation)
- **Body only:** Frontmatter stripped, only Markdown instructions (common with dedicated tools)

**Structured wrapping (recommended for dedicated tool):**

```xml
<skill_content name="pdf-processing">
# PDF Processing
...[SKILL.md body]...

Skill directory: /home/user/.agents/skills/pdf-processing
Relative paths in this skill are relative to the skill directory.

<skill_resources>
  <file>scripts/extract.py</file>
  <file>scripts/merge.py</file>
  <file>references/pdf-spec-summary.md</file>
</skill_resources>
</skill_content>
```

**Permission allowlisting:** Skill directories should be allowlisted so the model can read bundled resources without triggering permission confirmations.

### 1.8 Managing Skill Context Over Time

- **Protect skill content from context compaction:** Skill instructions should be exempt from pruning/truncation when the context window fills up. Flag skill tool outputs as protected so the pruning algorithm skips them. Use structured tags to identify skill content.
- **Deduplicate activations:** Track which skills have been activated in the current session. Skip re-injection of already-loaded skills.
- **Subagent delegation (optional, advanced):** Run skill in a separate subagent session instead of injecting into main conversation. Useful for complex workflows.

---

## 2. Magenta Codebase Analysis — Current Architecture

### 2.1 Request Flow Overview

```
ChatController
  → ChatService.chat(request)
    → RequestResolver.resolve()          # Resolves conversation ID, model, mode
    → ConversationTurnCoordinator.submit() # Serializes turns per conversation
    → chatNow()
      → approvedTools(request)           # Resolves which tools are available
        → ToolAccessPolicy.filterToolsByMode()  # Filters by PLAN/TASK/NORMAL mode
      → toolChatWithRetry() OR plainChat()
        → prompt()                       # Builds ChatClient prompt
          → effectiveSystemPrompt()      # Calls PromptContextAssembler.mergeModePrompt()
          → chatModelRouter.chatOptions()
        → ChatClient.call()
```

### 2.2 Key Injection Points for Skills

#### A) System Prompt Assembly: `PromptContextAssembler`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`

This is where the system prompt is composed. Currently:

```java
public void assemble(TurnContext ctx) {
    ResolvedChatRequest request = ctx.resolvedRequest();
    PlanMode mode = ctx.interactionMode();
    String systemPrompt = mergeModePrompt(mode, request.conversationId());
    ctx.systemPrompt(systemPrompt);
    ctx.turnInstructions(assembleTurnInstructions(request, systemPrompt));
}
```

`mergeModePrompt()` merges the default agent prompt with mode-specific runtime instructions from PlanService/TaskService. **This is where the skill catalog would be appended** (Tier 1 — Discovery).

#### B) Agent Configuration: `AgentConfig` and `AiConfig`

**Files:**
- `src/main/java/io/mindspice/magenta2/ai/config/user/AgentConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`

`AgentConfig` is a record:
```java
public record AgentConfig(
    String model,
    String systemPrompt,
    List<String> approvedTools,
    List<String> allowedShellCommands
) { }
```

**New fields needed:**
- `skillsEnabled: boolean` — per-agent toggle
- `skillsDirectories: List<String>` — additional skill scan paths beyond defaults
- `skillsTrustProjectSkills: boolean` — trust gate for project-level skills

`AiConfig` would need a `skills` section for global skill configuration.

#### C) Tool Registration: `ChatToolRegistry`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`

Tools are registered as Spring beans via `ToolCallback` and `ToolCallbackProvider`. Each tool package (file, shell, web, orchestration, plan, task, avatar) has its own `@Configuration` class.

**For skills with `allowed-tools`:** The `allowed-tools` field in SKILL.md frontmatter is experimental, but if supported, the tool access policy would need to intersect the skill's allowed tools with the agent's approved tools.

#### D) Tool Access Policy: `ToolAccessPolicy`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`

Currently filters tools by interaction mode (PLAN, TASK, EXECUTE_PLAN, EXECUTE_TASK, NORMAL). For skills, this would also need to consider the active skill's tool constraints.

#### E) Context Management: `ContextManagementAdvisor`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`

Manages context compaction (summarization + trimming). This is where **skill content protection** must be implemented — skill instructions must be exempt from pruning. The advisor already has patterns for identifying protected content:

- `isHiddenSummary()` — identifies compacted summaries by prefix
- `isCompactionNotice()` — identifies compaction notices

A similar pattern (prefix-based or tag-based) could identify skill content for protection.

#### F) Chat Service: `ChatService`

**File:** `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` (2,443 lines)

The `prompt()` method (line ~2100) builds the `ChatClient.ChatClientRequestSpec`. This is where the system prompt is set:

```java
String systemPrompt = effectiveSystemPrompt(request);
if (StringUtils.hasText(systemPrompt)) {
    prompt = prompt.system(systemPrompt);
}
```

The `chatNow()` method (line ~321) resolves approved tools and decides tool vs. plain chat path. This is where skill tool filtering would apply.

#### G) Startup Configuration: `AiUserConfigConfiguration` and `ExternalAiConfigLoader`

**Files:**
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiUserConfigConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`

Config loading happens at Spring context startup. **Skill discovery would also happen at startup** (scan directories, parse SKILL.md files, build catalog).

#### H) Existing `agents/` Directory

The repo has an `agents/` directory at the project root containing `agent-1/workspace/outputs/`. This is for agent workspace persistence, not skills. **Skills would need their own directory structure**, e.g.:
- `~/.magenta/skills/` — user-level
- `<project>/.magenta/skills/` — project-level

---

## 3. Implementation Plan — High-Level

### 3.1 New Components

| Component | Package | Responsibility |
|-----------|---------|----------------|
| `SkillDiscoveryService` | `ai.skill` | Scan directories, discover SKILL.md files, parse frontmatter, build catalog |
| `SkillCatalog` | `ai.skill` | In-memory registry of discovered skills (name → SkillRecord map) |
| `SkillRecord` | `ai.skill` | Data class: name, description, location, baseDirectory, body (optional), metadata |
| `SkillActivationService` | `ai.skill` | Load skill body, wrap in structured tags, track activations, deduplicate |
| `SkillContextProtector` | `ai.skill` | Integration with ContextManagementAdvisor to protect skill content from compaction |
| `SkillCatalogInjector` | `ai.skill` | Appends skill catalog to system prompt (or provides it to tool description) |
| `SkillToolActivator` | `ai.chat.tool.skill` | Optional: dedicated `activate_skill` tool for model-driven activation |

### 3.2 Modified Components

| Component | Change |
|-----------|--------|
| `AgentConfig` | Add `skillsEnabled`, `skillsDirectories`, `skillsTrustProjectSkills` fields |
| `AiConfig` | Add `skills` configuration section (scan paths, trust settings) |
| `PromptContextAssembler` | Append skill catalog to system prompt (when skills are enabled) |
| `ToolAccessPolicy` | Consider active skill's `allowed-tools` when filtering |
| `ContextManagementAdvisor` | Protect skill content (identified by XML tags or prefix) from compaction |
| `ChatService.prompt()` | Pass skill catalog to prompt assembly |
| `ChatService.chatNow()` | Consider skill-based tool restrictions |
| `ExternalAiConfigLoader` | Add skill config validation |
| `ChatBeanConfig` | Wire new skill beans |
| `application.yml` | Add skill configuration properties |

### 3.3 Startup Lifecycle

```
Spring Context Start
  → AiUserConfigConfiguration loads AiConfig
  → SkillDiscoveryService.postConstruct()
      1. Resolve scan directories:
         - ~/.magenta/skills/          (user-level, client-native)
         - ~/.agents/skills/           (user-level, cross-client)
         - <project>/.magenta/skills/  (project-level, client-native)
         - <project>/.agents/skills/   (project-level, cross-client)
         - Any custom paths from AiConfig
      2. Walk each directory (max depth 4-6)
      3. For each SKILL.md found:
         a. Parse YAML frontmatter
         b. Validate (lenient)
         c. Create SkillRecord
      4. Resolve name collisions (project overrides user)
      5. Build SkillCatalog (in-memory map)
      6. Log discovery summary
  → SkillCatalogInjector builds catalog text for system prompt
```

### 3.4 Per-Turn Lifecycle

```
Chat Turn
  → PromptContextAssembler.assemble()
      1. Build base system prompt (existing logic)
      2. If skills enabled and catalog non-empty:
         Append catalog + behavioral instructions
      3. If a skill has been activated this session:
         Skill content is already in conversation history (protected)
         → Skip re-injection (deduplication)
  → ChatService.chatNow()
      1. approvedTools() resolves agent's approved tools
      2. If active skill has allowed-tools:
         Intersect with agent's approved tools
  → ContextManagementAdvisor.prepare() / compact()
      1. Skill content identified by <skill_content> tags
      2. Exempt from pruning during compaction
```

### 3.5 Activation Flow (Two Paths)

**Path A — File-read activation (simpler, first implementation):**
1. Model sees skill catalog in system prompt with `location` paths
2. Model decides a skill is relevant
3. Model calls its existing `file_read` tool with the `SKILL.md` path
4. Model receives file content as tool result
5. Model follows the skill's instructions

**Path B — Dedicated tool activation (future enhancement):**
1. Model sees skill catalog embedded in `activate_skill` tool description
2. Model calls `activate_skill(name="pdf-processing")`
3. `SkillActivationService` loads the body, wraps in `<skill_content>` tags, lists bundled resources
4. Result is injected as a tool result into conversation
5. Skill content is marked as protected for context management

---

## 4. Detailed Implementation Targets

### 4.1 `SkillRecord` (New)

```java
package io.mindspice.magenta2.ai.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record SkillRecord(
    String name,            // From frontmatter, validated
    String description,     // From frontmatter, required
    Path location,          // Absolute path to SKILL.md
    Path baseDirectory,     // Parent directory of location
    String body,            // Markdown body after frontmatter (nullable, lazy-loaded)
    String license,         // Optional
    String compatibility,   // Optional
    Map<String, String> metadata,  // Optional
    List<String> allowedTools,     // Optional, space-separated from frontmatter
    String scope            // "user" or "project"
) {
    /**
     * Resolves a relative path (e.g. "scripts/extract.py") against the skill's
     * base directory, returning an absolute path.
     */
    public Path resolveRelative(String relativePath) {
        return baseDirectory.resolve(relativePath).normalize();
    }
}
```

### 4.2 `SkillCatalog` (New)

```java
package io.mindspice.magenta2.ai.skill;

import java.util.*;

public class SkillCatalog {
    private final Map<String, SkillRecord> skillsByName;
    private final List<SkillRecord> allSkills;

    public SkillCatalog(List<SkillRecord> skills) {
        // Resolve name collisions: project overrides user
        Map<String, SkillRecord> resolved = new LinkedHashMap<>();
        List<SkillRecord> projectFirst = new ArrayList<>(skills);
        projectFirst.sort(Comparator.comparing(s -> "project".equals(s.scope()) ? 0 : 1));
        for (SkillRecord skill : projectFirst) {
            SkillRecord existing = resolved.get(skill.name());
            if (existing != null) {
                // Project overrides user; log warning
                if (!"project".equals(existing.scope()) && "project".equals(skill.scope())) {
                    resolved.put(skill.name(), skill);
                }
            } else {
                resolved.put(skill.name(), skill);
            }
        }
        this.skillsByName = Collections.unmodifiableMap(resolved);
        this.allSkills = List.copyOf(resolved.values());
    }

    public Optional<SkillRecord> get(String name) { ... }
    public List<SkillRecord> all() { return allSkills; }
    public boolean isEmpty() { return allSkills.isEmpty(); }
    public int size() { return allSkills.size(); }
}
```

### 4.3 `SkillDiscoveryService` (New)

```java
package io.mindspice.magenta2.ai.skill;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SkillDiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(SkillDiscoveryService.class);
    private static final int MAX_SCAN_DEPTH = 5;
    private static final int MAX_SCAN_DIRS = 2000;

    private final AiConfig aiConfig;
    private SkillCatalog catalog = new SkillCatalog(List.of());

    // Configuration from AiConfig
    private List<Path> scanDirectories;
    private boolean trustProjectSkills;

    @PostConstruct
    public void discover() {
        resolveScanDirectories();
        List<SkillRecord> discovered = new ArrayList<>();
        for (Path dir : scanDirectories) {
            if (Files.isDirectory(dir)) {
                discovered.addAll(scanDirectory(dir));
            }
        }
        this.catalog = new SkillCatalog(discovered);
        log.info("Skill discovery complete: {} skills loaded from {} directories",
            catalog.size(), scanDirectories.size());
    }

    private List<SkillRecord> scanDirectory(Path root) { /* ... */ }
    private Optional<SkillRecord> parseSkillMd(Path skillMdPath) { /* ... */ }

    public SkillCatalog catalog() { return catalog; }
}
```

### 4.4 `PromptContextAssembler` Changes

The `mergeModePrompt()` method needs a new injection point after the mode-specific prompt is built but before returning:

```java
public String mergeModePrompt(PlanMode mode, String conversationId) {
    // ... existing logic builds `result` ...
    
    // Append skill catalog if enabled
    String skillCatalog = skillCatalogInjector.catalogText();
    if (StringUtils.hasText(skillCatalog) && StringUtils.hasText(result)) {
        result = result.stripTrailing() + "\n\n" + skillCatalog;
    }
    
    // Append worktype profile (existing)
    // ...
    return result;
}
```

### 4.5 `ContextManagementAdvisor` Changes

The compaction logic needs to identify and skip skill content. Using the structured wrapping approach:

```java
// In compact() method, filter out skill content from compactable messages:
List<Message> compactable = storedMessages.stream()
    .filter(message -> !isHiddenSummary(message))
    .filter(message -> !isCompactionNotice(message))
    .filter(message -> !isSkillContent(message))  // NEW
    .toList();

private boolean isSkillContent(Message message) {
    String text = message.getText();
    return text != null && text.contains("<skill_content");
}
```

### 4.6 `AgentConfig` Changes

```java
public record AgentConfig(
    String model,
    String systemPrompt,
    List<String> approvedTools,
    List<String> allowedShellCommands,
    // NEW fields:
    @JsonProperty("skillsEnabled") Boolean skillsEnabled,
    @JsonProperty("skillsDirectories") List<String> skillsDirectories,
    @JsonProperty("skillsTrustProjectSkills") Boolean skillsTrustProjectSkills
) {
    // Backward-compatible constructor
    public AgentConfig(String model, String systemPrompt, List<String> approvedTools,
                       List<String> allowedShellCommands) {
        this(model, systemPrompt, approvedTools, allowedShellCommands, true, List.of(), false);
    }
    
    public boolean isSkillsEnabled() { return skillsEnabled == null || skillsEnabled; }
    public boolean isTrustProjectSkills() { return Boolean.TRUE.equals(skillsTrustProjectSkills); }
}
```

### 4.7 `activate_skill` Tool (Optional Future Enhancement)

For a dedicated activation tool:

```java
@Component
public class SkillActivationTools {
    private final SkillDiscoveryService skillDiscovery;
    private final SkillActivationService activationService;

    @Tool(description = """
        Activate a skill to load its full instructions. 
        Available skills: {catalog}
        Call this when a task matches a skill's description.
        """)
    public String activateSkill(
        @ToolParam(description = "Name of the skill to activate") String name
    ) {
        SkillRecord skill = skillDiscovery.catalog().get(name)
            .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + name));
        return activationService.activate(skill);
    }
}
```

The `{catalog}` placeholder would be dynamically replaced with the skill list at tool registration time.

---

## 5. Senior Analysis

### 5.1 Specification Strengths

1. **Progressive disclosure is the right architecture.** Loading only names+descriptions at startup and full instructions on demand is the correct trade-off for context window economics. Magenta already has a similar pattern with its compaction system.

2. **Cross-client paths (`.agents/skills/`) are a strong ecosystem play.** This means skills created for Claude Code, Cursor, or other tools would be automatically visible in Magenta if we scan those paths.

3. **File-read activation is the pragmatic first step.** Magenta already has `file_read` as a tool. The model can activate skills with zero new infrastructure — just add the catalog to the system prompt with locations.

4. **The spec is designed for lenient parsing.** The guidance to warn rather than fail on cosmetic issues (name mismatch, length violations) is pragmatic. Skills authored for other clients may have minor deviations.

### 5.2 Specification Gaps and Ambiguities

1. **No versioning model.** There's no `version` field in the spec frontmatter (only in optional `metadata`). Skill evolution over time (breaking changes to instructions, script API changes) has no standard handling. Recommendation: use `metadata.version` and document a semver convention.

2. **No dependency model.** Skills can't declare dependencies on other skills. If skill A says "use skill B for the validation step," the model must discover B through its own judgment. This works with current LLM capabilities but could be made explicit.

3. **`allowed-tools` is experimental.** The field exists but support varies. We should implement it as optional — if present, intersect with agent-approved tools; if absent, use agent's full tool set.

4. **No skill state/persistence model.** Skills are stateless instructions. If a skill needs to track state across turns (e.g., "step 3 of 5"), that state lives in conversation history. This is actually fine for Magenta since we persist conversation history.

5. **Subagent delegation is mentioned but not specified.** The spec mentions "subagent delegation (optional)" as a pattern but provides no standard interface. If we implement this, we'd need to define our own contract.

6. **Context protection mechanism is implementation-defined.** The spec says "exempt skill content from pruning" and suggests "flag skill tool outputs as protected" but doesn't define how. We'd use the structured wrapping approach (`<skill_content>` tags).

### 5.3 Alignment with Magenta Architecture

**Strong alignments:**
- Magenta's `PromptContextAssembler` is the natural injection point for the skill catalog
- Magenta's `ChatToolRegistry` + `ToolAccessPolicy` maps well to skill tool filtering
- Magenta's `ContextManagementAdvisor` already has the compaction-exemption pattern needed for skill protection
- Magenta's external config model (`AiConfig` + `AgentConfig`) can cleanly absorb skill configuration
- Magenta's file tools (`AgentFileTools`) already support `file_read`, making file-read activation trivial

**Challenges:**
- The `approvedTools` resolution chain (`ChatService` → `RuntimeSettingsService` → `AgentProfileService` → `AgentConfig`) is deep; adding skill-level tool filtering requires threading through multiple layers
- The `prompt()` method builds the system prompt inline; the skill catalog append needs to respect the same mode-specific logic (`PLAN` mode replaces the system prompt entirely)
- Skill scanning at startup adds startup time — needs to be bounded and fast. For large skill directories with many bundled files, consider shallow scanning (only SKILL.md at discovery, not full directory trees)

### 5.4 Recommended Implementation Order

1. **Phase 1 — File-read activation (MVP):**
   - `SkillRecord`, `SkillCatalog`, `SkillDiscoveryService`
   - Catalog injection into system prompt via `PromptContextAssembler`
   - Agent config toggle (`skillsEnabled`)
   - Trust gate for project skills

2. **Phase 2 — Context protection:**
   - `SkillContextProtector` integration with `ContextManagementAdvisor`
   - Deduplication tracking in `SkillActivationService`

3. **Phase 3 — Dedicated tool activation:**
   - `activate_skill` tool
   - Structured wrapping (`<skill_content>` tags)
   - Bundled resource listing
   - User-explicit activation (`/skill-name` command in chat)

4. **Phase 4 — Advanced:**
   - `allowed-tools` support
   - Subagent delegation for complex skills
   - Skill marketplace / remote registry integration
   - Slash-command autocomplete in UI

### 5.5 Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Untrusted project skills inject malicious instructions | **High** | Gate project skills on trust check; display activation notice in UI |
| Skill catalog bloats system prompt for every turn | **Medium** | Progressive disclosure — only catalog, not bodies. Filter out irrelevant skills. |
| Skill body conflicts with agent's system prompt | **Medium** | Skill content is injected as conversation messages, not system prompt replacement. Model can reconcile. |
| Startup time regression from scanning large file trees | **Low** | Bounded scan depth, skip common directories, shallow scan only |
| Name collision between user and project skills | **Low** | Deterministic precedence (project overrides user), logged warning |

---

## 6. Contract Requirements for Specification Adherence

### 6.1 MUST Implement (Required for compliance)

- [ ] Scan configured directories for `SKILL.md` files
- [ ] Parse YAML frontmatter (name, description required; optional fields)
- [ ] Validate `name`: 1-64 chars, lowercase alphanumeric + hyphens, no leading/trailing/consecutive hyphens
- [ ] Reject skills with empty/missing `description`
- [ ] Load only name + description at startup (Tier 1)
- [ ] Provide mechanism to load full body when skill is activated (Tier 2)
- [ ] Support relative path resolution from skill base directory
- [ ] Project-level skills override user-level skills on name collision
- [ ] Lenient validation — warn on non-critical issues, don't block loading

### 6.2 SHOULD Implement (Strongly recommended)

- [ ] Scan both client-native (`.magenta/skills/`) and cross-client (`.agents/skills/`) paths
- [ ] Structured wrapping of skill content (`<skill_content>` tags)
- [ ] Protect skill content from context compaction
- [ ] Deduplicate activations within a session
- [ ] Skip `.git/`, `node_modules/` during scan
- [ ] Log diagnostics for malformed skills
- [ ] Trust gate for project-level skills
- [ ] Omit catalog entirely when no skills are available

### 6.3 MAY Implement (Optional enhancements)

- [ ] Dedicated `activate_skill` tool (vs. file-read activation)
- [ ] `allowed-tools` field support
- [ ] `compatibility` field filtering
- [ ] Subagent delegation for skill execution
- [ ] User-explicit activation via slash commands
- [ ] Autocomplete for skill names in UI
- [ ] Remote skill registry integration

---

## 7. References

- [Agent Skills Specification](https://agentskills.io/specification)
- [Client Implementation Guide](https://agentskills.io/client-implementation/adding-skills-support.md)
- [Best Practices for Skill Creators](https://agentskills.io/skill-creation/best-practices.md)
- [Using Scripts in Skills](https://agentskills.io/skill-creation/using-scripts.md)
- [Skills Validation Library](https://github.com/agentskills/skills-ref) — `skills-ref validate ./my-skill`
