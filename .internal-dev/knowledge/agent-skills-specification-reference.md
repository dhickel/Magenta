# Agent Skills Specification — Implementation Research Report

> **Source:** [Agent Skills Specification](https://agentskills.io/specification)
> **Client Implementation Guide:** [Adding Skills Support](https://agentskills.io/client-implementation/adding-skills-support.md)
> **Date:** 2026-05-26
> **Status:** Research + Phase-01 contract-validation notes + Phase-06 closeout revalidation notes

---

## 0. Phase-01 Contract Validation Notes (2026-05-26)

These notes are the reusable contract baseline for the `agent-skills-system` Phase 01 docs update. Official `agentskills.io` pages are authoritative for format/lifecycle behavior; local research is implementation context only.

### 0.1 Official facts re-verified

- A skill is a directory with required `SKILL.md` and optional `scripts/`, `references/`, and `assets/`.
- `SKILL.md` requires YAML frontmatter with `name` and `description`; `allowed-tools` is explicitly marked experimental.
- Progressive disclosure is the core lifecycle: catalog at startup, full instructions on activation, resources on demand.
- Client implementation guidance says directory locations are client-defined, while `.agents/skills/` is a widely adopted interoperability convention.
- Client guidance documents project-over-user precedence for name collisions and recommends trust-gating project-level skills.
- Activation patterns include file-read activation and dedicated activation tool; structured wrapping and resource listing are recommended for dedicated-tool paths.
- Context management guidance explicitly recommends protecting skill content from compaction and deduplicating activations.
- Script guidance requires relative paths from the skill root and recommends declaring prerequisites/compatibility explicitly.
- Best-practice guidance recommends concise `SKILL.md` content and progressive disclosure for larger skills (keep core instructions compact and move details to references).

### 0.2 MVP vs deferred boundary used in Magenta specs/docs

- MVP active contract in this phase: Magenta-managed root repository `MagentaRootProperties.path()/skills` + agent-profile assignment + catalog/activation contract docs.
- Deferred in this phase: project-local `.agents/skills`, user-home/client-native scopes, layered assignment (project/job/task/workflow/chat/session), script trust/execution policy, and registry/package/marketplace ingestion.
- `allowed-tools` is documented as experimental and not treated as enforced permission policy in MVP.

### 0.3 Phase-06 closeout revalidation notes (2026-05-26)

- Official pages re-opened during closeout:
  - `https://agentskills.io/specification`
  - `https://agentskills.io/client-implementation/adding-skills-support`
  - `https://agentskills.io/skill-creation/best-practices`
  - `https://agentskills.io/skill-creation/using-scripts`
- Confirmed still-valid requirements used in Magenta closeout:
  - required `SKILL.md` shape and required `name`/`description`;
  - optional `license`, `compatibility`, `metadata`, and experimental `allowed-tools`;
  - progressive disclosure (catalog metadata first, full instructions on activation, resources on demand);
  - dedicated activation tool is valid and may return body-only content;
  - no-skill behavior should omit empty catalog/tool exposure;
  - scripts/references path usage is relative to skill root.
- Closeout documentation keeps Magenta-specific divergences explicit:
  - root repository policy is `MagentaRootProperties.path()/skills` for MVP;
  - project/user scopes and layered assignment remain deferred;
  - `allowed-tools` remains non-authoritative metadata in MVP.

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

### 1.4 Skill Discovery — Client Directory Scanning Examples

The Agent Skills specification defines what goes inside a skill directory; it does not mandate where clients store or discover those directories. The client implementation guide describes common client-chosen project and user scopes, and treats `.agents/skills/` as a widely adopted cross-client interoperability convention rather than a specification-defined mandatory location:

| Client-guide example scope | Path | Purpose |
|----------------------------|------|---------|
| Project (client-native) | `<project>/.<client>/skills/` | Client-specific repository location chosen by the implementor |
| Project (cross-client convention) | `<project>/.agents/skills/` | Cross-client interoperability convention/example |
| User (client-native) | `~/.<client>/skills/` | Client-specific user location chosen by the implementor |
| User (cross-client convention) | `~/.agents/skills/` | Cross-client interoperability convention/example |

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

## 2. Magenta Phase-01 Contract Snapshot (Active)

### 2.1 Active MVP Scope

- Root-only repository: `MagentaRootProperties.path()/skills`.
- Agent-profile assignment scope only.
- Catalog/disclosure/activation lifecycle follows progressive disclosure.
- `allowed-tools` remains experimental metadata and is not an enforced MVP permission layer.

### 2.2 Explicit Deferred Scope

- Project-local/client-local skill loading (including `.agents/skills` repository discovery in Magenta runtime behavior).
- User-home scope loading.
- Layered assignment beyond agent profile (project/job/task/workflow/chat/session).
- Script trust/execution policy and UI-level script execution promises.
- Remote registry/package/marketplace ingestion and provenance flows.

### 2.3 Historical Content Status

The pre-Phase-01 implementation sketch previously stored in this file (multi-scope scanning, optional `allowed-tools` enforcement paths, and marketplace-forward roadmap notes) is historical research context only and is superseded by the active Phase-01 contract above plus `.internal-dev/specifications/{architecture,services,api,web,simplypages,decisions,deferred-features}.md`.

## 3. References

- [Agent Skills Specification](https://agentskills.io/specification)
- [Client Implementation Guide](https://agentskills.io/client-implementation/adding-skills-support.md)
- [Best Practices for Skill Creators](https://agentskills.io/skill-creation/best-practices.md)
- [Using Scripts in Skills](https://agentskills.io/skill-creation/using-scripts.md)
- [Skills Validation Library](https://github.com/agentskills/skills-ref) — `skills-ref validate ./my-skill`
