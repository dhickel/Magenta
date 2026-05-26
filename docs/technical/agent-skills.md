# Agent Skills (Planned MVP Contract)

This document captures the intended technical contract for Agent Skills before full runtime implementation lands.

## Scope Boundary

- **In scope for MVP contract:** root `skills/` repository, `SKILL.md` parsing contract, skill metadata/catalog surface, agent-level assignment, and activation lifecycle guidance.
- **Out of scope for MVP:** project-local `.agents/skills`, user-home/client-native scopes, layered assignment (project/job/task/workflow/chat/session), script trust/execution policy, and registry/package ingestion.

## Repository Root

MVP uses a Magenta-owned repository rooted at:

```text
MagentaRootProperties.path()/skills
```

This is an intentional client policy choice. Agent Skills format is still aligned with official `SKILL.md` structure and optional directories.

## Skill Record Contract

Minimum persisted/disclosed metadata per discovered skill:

- `name`
- `description`
- `location` (absolute `SKILL.md` path or equivalent canonical identifier)

Optional metadata is retained when available (`license`, `compatibility`, `metadata`, experimental `allowed-tools`).

## Service Ownership (Planned)

Expected ownership boundary:

- Skill discovery and parser services scan root `skills/`, parse frontmatter/body, and emit diagnostics.
- Catalog services provide compact discovery payloads for prompt/tool disclosure.
- Assignment services bind skills to agent profiles.
- Activation services load selected skill instructions and track activation state.

Chat/web/API layers consume this domain surface; they do not own direct parser/discovery logic.

## Disclosure And Activation Contract

Intended behavior follows progressive disclosure:

1. Catalog only (`name`, `description`, optional `location`) at startup.
2. Full `SKILL.md` instructions when activated.
3. Supporting files loaded on demand when referenced.

MVP keeps both activation patterns available in contract discussions:

- Dedicated activation tool (preferred baseline for control and structured wrapping).
- File-read activation compatibility for environments/models where direct file reads are preferred.

## API/Web Contract (Planned)

Planned API/web surfaces cover:

- Skill list/read/create/update/delete in root repository.
- Guided skill creation/editing for `SKILL.md`.
- Agent-skill assignment and removal.

Planned MVP constraints:

- No script execution control surface in browser.
- No claims that `allowed-tools` is an enforced permission policy.
- Deferred scope must be explicitly labeled as deferred in API/web docs.

## Validation Expectations

When implementation starts, validate with:

- Parser and discovery service tests.
- Catalog/activation service tests (including compaction protection hooks and dedupe behavior).
- Controller/API tests for skill and assignment endpoints.
- Focused Playwright validation for skill browser/editor/assignment flows.
- Bounded Spring startup smoke when wiring/config changes.
