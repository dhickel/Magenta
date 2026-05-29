---
schema_version: 1
document_type: worker-directive
status: planning
phase: 03
role: notes-project-context
worker_model: gpt-5.5
worker_reasoning: high
validator_model: gpt-5.5
validator_reasoning: xhigh
---

# Phase 03 Notes And Project Context Directive

## Objective

Implement Notes, Projects, and Contacts/Materials widgets with personal DB notes, file-backed agent/project/Work Area notes, and typed household project artifacts.

## Editable Targets

- Widget renderers/routes/services under `avatar/dashboard` and `api/web`.
- `AvatarService`/repository for personal note settings if needed.
- Existing project, Work Area, file explorer, output services only through service APIs.
- New project artifact adapter/service classes if needed.
- `WorkAreaExplorerFragments` only for reusable fragment extraction, not broad redesign.
- Tests/docs/specs.

## Forbidden Scope

- Do not store runtime project/Work Area state in `avatar.sqlite`.
- Do not bypass Work Area confinement services.
- Do not duplicate full `/projects` or full file explorer.

## Implementation Steps

1. Add Notes widget source settings: personal, agent, project, Work Area, mixed.
2. Implement personal note quick capture/search/tag/last-opened behavior.
3. Implement file-backed note browsing/search/read/edit through Work Area/project file services and labels/tags.
4. Add Project widget summary/detail for goals, materials, contacts, blockers, next actions, outputs, notes, progress.
5. Add typed project artifact schemas/files and service adapters with validation/default creation.
6. Add Contacts/Materials widget or project sub-widget according to registry scope, with source binding and project links.
7. Add tool descriptors and static tools or service methods for controlled read/update.

## Acceptance Criteria

- Personal notes remain in `avatar_notes`; file notes remain file-backed.
- Project household artifacts are confined under project/Work Area roots and validated by service adapters.
- Notes widget shows source clearly and handles missing bindings.
- Projects widget supports household and code projects without pretending all projects are repos.

## Validation Commands

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest,ProjectServiceTest,ProjectRepositoryTest,WorkAreaServiceTest,WorkAreaExplorerServiceTest,WorkAreaControllerTest test`
- Add tool tests if tools changed.

## Browser Checklist

Notes personal mode, Work Area file-note mode, Markdown viewer/editor, Projects widget seeded with household project goals/materials/contacts/blockers/next actions, mobile detail scrolling, style comparison with Work Area browser.

## Stop Conditions

Stop if typed project artifact location conflicts with workspace path contracts. Stop if file-note edit requires unsafe direct filesystem access.

## Do Not Close Unless

- Confinement tests pass.
- File-backed and DB-backed notes are visibly distinct.
- Docs explain source modes and project artifact storage.
