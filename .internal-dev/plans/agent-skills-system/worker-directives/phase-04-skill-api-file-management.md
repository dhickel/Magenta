# Phase 04 Worker Directive: Skill APIs And File Management

## Objective

Expose thin API and fragment endpoints for listing skills, refreshing discovery, viewing diagnostics/details, safely listing/viewing/editing/creating skill files, and managing agent assignments through backend metadata.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, high reasoning.
- Validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.

## Required Reading

- Phase 02 and Phase 03 implementation reports and validator results.
- Package guide: `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- Package guide: `src/main/java/io/mindspice/magenta2/ai/skills/AGENTS.md`
- `entity-selector-htmx-pattern.md`
- `plans-list-status-chip-and-delete-pattern.md`
- Existing file endpoints:
  - `WorkAreaController`
  - `WorkAreaExplorerFragments`
  - project file editor sections in `OrchestrationController`

## Editable Targets

- New or existing web/API controllers:
  - `src/main/java/io/mindspice/magenta2/api/web/SkillController.java` or equivalent
  - `src/main/java/io/mindspice/magenta2/api/web/SkillFragments.java` or equivalent
  - `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` only for navigation/fragment integration if chosen
- `src/main/java/io/mindspice/magenta2/ai/skills/` for service methods required by API.
- `src/test/java/io/mindspice/magenta2/api/web/*Skill*Test.java`
- API/technical docs and specs if route names/payloads settle here.

## Forbidden Scope

- Do not build final browser styling or guided UI flow in this phase unless a tiny fragment is necessary for endpoint tests.
- Do not expose unrestricted filesystem paths.
- Do not allow binary/script execution.
- Do not add broad auth/CSRF infrastructure.
- Do not implement project-local scan controls.

## Implementation Steps

1. Verify official spec pages for file/directory assumptions that affect route behavior.
2. Define route contract and keep controllers thin.
3. Add API endpoints for:
   - skill list/detail;
   - refresh/rescan;
   - diagnostics;
   - file tree/list;
   - file view;
   - text save;
   - safe text file create;
   - agent assignment add/remove/list.
4. Implement service-backed file operations:
   - route `skillName` validation;
   - relative path validation;
   - only skill-root-confined reads/writes;
   - text/binary size limits consistent with existing file editor posture;
   - clear status codes for invalid/missing/conflict/unsupported.
5. Add controller tests for success and negative cases:
   - malformed skill still listed with diagnostics;
   - save `SKILL.md` triggers or requires refresh behavior;
   - traversal/symlink blocked;
   - assignment to unknown agent/skill fails safely;
   - duplicate assignment handled idempotently or with documented status.
6. Add HTMX fragments only to the extent needed by Phase 05 UI and tests.
7. Update docs/specs with final route names and payload/status behavior.

## Acceptance Criteria

- API/controller tests cover skill management and file operations.
- Route behavior is service-backed and controllers remain thin.
- Skill file read/write/create cannot escape root repository.
- Assignment endpoints persist through the backend assignment service.
- Malformed skills remain visible and safe through API.

## Negative Checks

- No endpoint accepts absolute filesystem paths.
- No endpoint reads arbitrary Magenta root files outside `skills/`.
- No script/resource execution route exists.
- No UI-only validation replaces server-side validation.

## Validation Commands

```bash
mvn -Dtest='*Skill*Controller*,*AgentSkill*Repository*,*AgentSkill*Assignment*' test
rg -n "/api/skills|/skills|SkillController|SkillFragments|AgentSkill" src/main/java src/test/java docs .internal-dev/specifications
```

## Stop Conditions

- Stop if controller route shape conflicts with existing operational route conventions.
- Stop if file editing needs new reusable file-browser abstractions beyond this phase.
- Stop if API status decisions are unclear or conflict with existing tests.

## Senior Guidance

This phase is about contracts and safe boundaries. Do not drift into visual design; Phase 05 owns the experience.

## Do Not Close Unless

- API route/payload/status behavior is documented.
- Path confinement tests include traversal and symlink negatives.
- Assignment API behavior is backed by DB/service tests.
