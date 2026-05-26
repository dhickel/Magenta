# Senior Engineer Guidance

## Core Bias

Keep this as a contract correction, not a platform expansion. The goal is one coherent workspace/run/output model that services, tools, prompts, docs, tests, and UI all agree on.

## Architectural Rules

- Centralize static layout names first. Replacing old strings with new scattered strings is not acceptable.
- Preserve data-root confinement and root-relative DB storage. `RootRelativePathService` remains the storage resolver for Magenta-owned paths.
- Use additive SQLite migrations unless a worker owns a deliberate development reset step.
- Treat `workspaceId` as compatibility metadata unless a specific existing API requires it. Project and Work Area routing are the user-facing selectors.
- Keep controllers thin. Filesystem and routing policy belongs in workspace/runtime services.
- Keep Work Area identity stable. Do not infer user-facing identity from folder names once the target model is in place.

## Implementation Priorities

- First make it hard to create the wrong path. Then migrate services to that helper.
- Make execution staging and final output promotion separate concepts in code and tests.
- Remove job-owned workspace behavior from new flows before cleaning up UI language.
- Validate retention by asserting staging remains after terminal completion and is eligible only after the retention threshold.
- For browser changes, prioritize a coherent operational file browser over broad internals exposure.

## Risk Hotspots

- `JobService` currently allocates and displays persistent job workspace paths.
- `PlanService` currently deletes temp staging immediately and tells models that `outputs/` is permanently preserved.
- `WorkflowRunner` has separate path fallback behavior that can drift from task runs.
- File/shell tool aliases are duplicated; a small helper for alias constants may prevent prompt/tool mismatch.
- Tests may assert old `runtime/task-runs`, `runtime/workflow-runs`, `outputs/tasks`, `outputs/workflows`, and `outputs/jobs` paths.
- Docs and package `AGENTS.md` are currently stale and can mislead future agents if not updated in the same delivery.

## Replan Triggers

- A worker cannot make Work Areas ID-backed without rewriting broad UI semantics.
- A required legacy job workspace behavior is still active in a user-facing flow.
- Output promotion requires writing directly to final output directories during model execution.
- Retention cannot be enforced without a scheduler/cleanup behavior that was not planned.

