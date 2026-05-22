# Final Avatar Sprint Orchestration Plan

## Context

This is the execution plan for later implementation of the Avatar sprint. It assumes the current planning suite is committed and that implementation starts from a clean working tree on a dedicated branch. It deliberately avoids worktrees.

## Goal

Coordinate later implementation so safe non-overlapping lanes can run in parallel, shared files are serialized, validation is mandatory, and every phase closes with docs, `.internal-dev` updates, and an explicit-path commit.

## In Scope

- Branch and commit order.
- Parallel and serial lane mapping.
- File ownership declarations.
- Subagent roster and prompt summaries.
- Validation gates and red-team checks.
- Stop rules for overlap, dirty worktree risk, and validation blockers.

## Out of Scope

- Implementing any Avatar feature during this planning pass.
- Worktrees.
- Plugin runtime implementation.
- Bypassing repo-required Playwright validation for UI phases.

## Execution Rules

- Use one shared implementation branch, for example `feature/avatar-dashboard-sprint`.
- Do not use worktrees unless the user explicitly changes that constraint.
- Before editing, every implementation agent must:
  - read root/package `AGENTS.md` files relevant to its lane;
  - inspect `git status --short --branch`;
  - declare expected owned paths;
  - stop if owned files already have unexpected changes.
- Agents must not edit outside declared ownership without requesting remap.
- Commits must stage explicit path lists only, never `git add .`.
- Parallel implementation is allowed only for disjoint ownership lanes.
- Integration is serial after parallel lane commits land.
- UI validation must be run by a validation subagent using `gpt-5.3-codex` medium and must include screenshots.
- All testing, including non-Playwright validation, uses `gpt-5.3-codex` medium when delegated.

## Shared Notes

Use `.codex-orchestration/avatar-dashboard-sprint/notes.md` as coordinator-owned shared state during implementation. Worker agents may read it, but they must not edit the shared notes file while parallel implementation is active. Each lane writes its own handoff note under `.codex-orchestration/avatar-dashboard-sprint/lanes/<phase-id>-<agent-id>.md`; the coordinator serially merges relevant results into the shared notes file after each lane commit.

Required notes sections:

- Global Assumptions
- Active Agents
- Completed Work
- Validation Results
- Remediation Notes
- Blockers
- Closeout Work
- Final Validation Status
- Handoff Notes

## Phase Graph

1. Phase 01 serial: Avatar core and persistence.
   - Owns Avatar domain package, `avatar-schema.sql`, datasource wiring, Avatar profile reservation, and Avatar focus activation.
   - This establishes contracts for UI and assistant behavior.

2. Phase 02 can run after phase 01 starts, but must not touch Avatar files.
   - Owns workspace/output package, task completion output options, output publication tests, and docs.
   - Serializes on shared `PlanService`, `TaskTools`, workflow/job output code.

3. Phase 03 can run in parallel with phase 02 after phase 01 profile contract is known.
   - Owns operational tool package and agent chat context wrapper.
   - Serializes on `ChatService`, `ToolAccessPolicy`, `AgentOrchestrationController`, and config examples.

4. Phase 04 starts after phase 01 and phase 03 core contracts land.
   - Owns Avatar tools, profile-scoped chat behavior, organizer behavior, and redacted email event/reaction flow.
   - Serializes on `ChatService`, Avatar services, event/reaction enums, and task/assignment submission code.

5. Phase 05 starts after phase 01 services exist and can overlap only with read-only review of phases 02-04.
   - Owns `/avatar` controller/components/assets and UI docs.
   - Serializes on navigation files and shared web shell tests.

6. Final integration serial.
   - Runs broad tests, startup, browser validation, red-team checks, docs review, artifact archival, changelog review, and closeout email when all implementation work is done.

## Ownership Lanes

Parallel-safe lanes after phase 01 contracts:

- Avatar DB/domain:
  - `src/main/java/io/mindspice/magenta2/avatar/**`
  - `src/test/java/io/mindspice/magenta2/avatar/**`
  - `src/main/resources/avatar-schema.sql`
- Workspace outputs:
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
  - selected `PlanService`, `TaskTools`, `WorkflowRunner`, `JobService` edits behind a serialization gate
  - workspace/output tests
- Operational tools:
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/**`
  - selected `ChatService`, `ToolAccessPolicy`, `AgentOrchestrationController` edits behind a serialization gate
- UI:
  - `AvatarDashboardController`, `AvatarDashboardComponents`
  - `avatar-dashboard.css`, `avatar-chat.js`
  - web/controller tests
- Docs/internal-dev closeout:
  - `docs/**`
  - `.internal-dev/**`
  - Coordinator-owned during parallel implementation, except for explicitly assigned per-phase files named in the agent prompt.

Closeout ownership rules:

- Phase agents may update only their explicitly assigned docs and `.internal-dev` files.
- If docs or `.internal-dev` ownership is unclear, the phase agent records a requested closeout edit in its lane handoff note and stops.
- The integration coordinator performs final `.internal-dev/focus/**`, suite archive/handoff, changelog consolidation, and broad docs consistency edits serially.
- Shared `.codex-orchestration/avatar-dashboard-sprint/notes.md` is coordinator-owned; lane handoff notes are worker-owned.

Tightly gated files:

- `src/main/resources/schema.sql`
- `src/main/resources/application.yml`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/main/resources/static/js/chat-client.js`

## Subagent Roster

Use the model requested by the plan when available:

- Domain implementation agents: `gpt-5.5` high.
- Research/review agents: `gpt-5.5` xhigh for architecture synthesis; `gpt-5.3` medium for delegated external reading.
- Validation agents: `gpt-5.3-codex` medium.

Prompt summary for each implementation agent:

- Read the relevant phase file and package `AGENTS.md`.
- Declare owned paths.
- Inspect `git status`.
- Implement only the assigned lane.
- Add focused tests.
- Run lane validation.
- Update only explicitly assigned docs and `.internal-dev` closeout files for that phase; otherwise record requested closeout edits in the lane handoff note.
- Commit with explicit path list.
- Stop on unexpected changes, ownership overlap, missing secrets/services, or validation blockers.

## Validation Gates

Per phase:

- Focused unit/repository/controller tests named in the phase file.
- A bounded Spring startup for backend wiring phases.
- Documentation and `.internal-dev` closeout.
- Explicit-path commit.

UI gates:

- Controller tests.
- Bounded startup.
- Playwright validation by subagent with screenshots for `/avatar`.
- Desktop and mobile visual review for layout, overlap, and interaction breakage.

Integration gates:

- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Browser validation across `/avatar`, `/dashboard`, `/agents`, `/projects`, `/jobs`, `/outputs`, and `/chat`.
- Red-team checks:
  - auth/CSRF on unsafe routes;
  - path traversal and symlink output escape;
  - cross-agent mutation attempts;
  - unsafe widget/plugin content;
  - HTMX error visibility;
  - email secret/body leakage;
  - broad Avatar tool approval or shell allowlist mistakes.

## Remediation Policy

- Failed focused tests block that phase.
- Failed browser validation blocks UI/integration completion.
- Validation failures are remediated before the next serial phase starts.
- A blocker can be deferred only with explicit user approval and must be recorded in `.internal-dev/focus/unfinished-work.md`.

## Branch And Commit Order

1. Create `feature/avatar-dashboard-sprint`.
2. Commit phase 01.
3. Commit phase 02 and phase 03 as separate commits after their lane validations.
4. Commit phase 04 after phase 01/03 integration is stable.
5. Commit phase 05 after UI tests and Playwright validation.
6. Commit final docs/internal-dev/archive/handoff updates.
7. Final integration validation.
8. Email Dwight a closeout summary and wait for reply using `email-followup-wait`.

## Exit Criteria

- All phase plans are implemented and committed.
- Final validation gates pass or any blockers are explicitly approved and recorded.
- Plugin runtime remains research-only.
- Closeout email has been sent and reply wait handled.
