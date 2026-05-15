# Phase 05 - Model Overrides And Config Contract

## Context

The user wants complete model overrides everywhere. Current code already has several model fields: runtime default/planning/summary/compaction models, agent default model, plan planning/execution models, workflow run model override, job item model override, assignment model override, schedule/reaction template model override, and chat request model override paths. The risk is not absence of all fields; it is inconsistent UI coverage and alias/raw-name mismatch.

## Goal

Create one canonical override contract and apply it consistently across chat, system chat, plans, tasks, workflows, jobs, projects, agents, schedules, reactions, and submit-to-agent flows.

## In Scope

- Define model override precedence.
- Use model keys everywhere in UI and persisted settings.
- Add missing dropdowns to UI surfaces.
- Validate unknown model keys at service boundaries.
- Expose override summaries so users know what model will run.

## Out of Scope

- Provider credential management.
- New model-router architecture.
- Per-token/per-tool dynamic model routing.

## Target Contract

Use this precedence unless code evidence proves a stricter existing contract:

1. Explicit run/submit override from the immediate action.
2. Job item or workflow node override.
3. Plan/task execution model.
4. Agent default model.
5. Project manager type default model if project scope is active.
6. Runtime default model.
7. File config default model.

Planning chat uses:

1. Explicit planning chat override.
2. Plan planning model.
3. Runtime planning model.
4. File config planning model.

System chat uses:

1. System chat configured model.
2. Runtime default model.
3. File config default model.

All persisted values must be model keys from `AiConfig.models()`. Remote model names are provider details and should only be used inside `RuntimeSettingsService`/router resolution.

## Implementation Steps

1. Create a small shared model catalog helper if one does not already exist:
   - list configured model keys and labels;
   - validate keys;
   - resolve remote name for execution.
2. Update UI model controls to use dropdowns, not free text:
   - runtime settings default/planning/summary/compaction;
   - system chat model;
   - agent default model;
   - plan planning and execution model;
   - workflow submit model override;
   - job definition and job item model override;
   - project manager default model if project manager type has model defaults;
   - schedule/reaction assignment template model override.
3. Add validation at service save boundaries:
   - `AgentProfileService`
   - `PlanService`
   - `WorkflowService` or workflow run submit service
   - `JobService`
   - `ProjectService`
   - schedule/reaction services
   - runtime settings.
4. Update submit forms so they display effective model:
   - if no explicit override, show the fallback source;
   - if explicit override is selected, show the selected model key.
5. Normalize old raw remote names on read only if there is existing compatibility code like `keyForModelOrRemoteName`. Do not create new raw-name persistence.
6. Add tests that use at least two model aliases pointing to different remote names so alias/raw-name bugs cannot hide.

## Validation

- Unit tests for model precedence across default chat, planning chat, system chat, agent submit, plan execution, workflow run, and job item execution.
- Controller tests for dropdown rendering and unknown model rejection.
- Repository tests prove persisted model fields remain model keys.
- Playwright MCP changes model override on each operational surface and verifies reload persistence.
- A negative browser/API check submits an unknown model and gets a visible validation error, not a silent fallback.

## Exit Criteria

- Every alpha-visible work launch surface has a model override or a visible effective-model explanation.
- All persisted model selections are canonical keys.
- Users can tell why a specific model will run.

