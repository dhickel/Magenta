# Worker Directive: Phase 02 Workspace Starter Generation

## Objective

Generate hard-coded starter `AGENTS.md` guidance only when Magenta first creates an agent workspace, and prove existing files are never overwritten.

## Required Source Verification

Before editing, verify <https://agents.md/> for file name, plain-Markdown nature, and update/living-doc expectations. Local research is context only.

## Editable Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathLayout.java`
- New small helper/service/record under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/` if justified.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java` only for narrow call-site integration.
- Focused tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/` and/or agent service tests.

## Forbidden Scope

- Do not overwrite existing `AGENTS.md`.
- Do not add configurable template storage.
- Do not write starter files for project workspaces or user Work Areas in this phase unless planning is revised.
- Do not add UI, API, or schema changes.

## Supporting Docs To Read

- `.internal-dev/plans/agents-md-runtime-support/00-specification-lock.md`
- `.internal-dev/plans/agents-md-runtime-support/02-target-design.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`

## Implementation Steps

1. Identify the first-creation point for agent workspace roots.
2. Add a narrow helper that writes `AGENTS.md` only if the file does not exist.
3. Keep starter content hard-coded and aligned with current workspace semantics.
4. Ensure repeated `agentWorkspace(...)`, profile update, enable, or storage-ensure calls leave existing content unchanged.
5. Add service tests for first creation and no-overwrite.

## Acceptance Criteria

- A new agent workspace root contains `AGENTS.md`.
- Repeated workspace/profile operations do not modify an existing `AGENTS.md`.
- User-edited content remains byte-for-byte unchanged.
- Starter content covers all required topics from `00-specification-lock.md`.

## Negative Checks

- No starter file is written outside `workspace/<agentWorkspaceId>/`.
- No project or Work Area starter generation unless plan is revised.
- No legacy scratch/job-owned workspace language is advertised as current behavior.

## Validation Commands

```bash
mvn -Dtest='*Workspace*Test,*AgentProfile*Test' test
git diff --check -- src/main/java src/test/java
```

## Stop Conditions

- First-creation cannot be distinguished from later access without risking overwrite.
- A schema or metadata marker appears necessary.
- Starter wording conflicts with current workspace architecture docs.

## Do Not Close Unless

- Tests prove first creation and no-overwrite.
- Implementation report includes exact starter-file path and call path.
- No unrelated workspace behavior changes are included.
