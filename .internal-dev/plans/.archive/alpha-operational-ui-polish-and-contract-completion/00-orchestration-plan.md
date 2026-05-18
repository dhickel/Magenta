# Alpha Operational UI Polish And Contract Completion: Orchestration Plan

## Objective

Bring the current operational UI from "mostly wired but uneven" to alpha-usable by directly fixing the dashboard, plan editor, workflow builder, project setup, agent surface, Docker status, system chat, and model override gaps listed on 2026-05-14. The implementation must complete all listed work unless the item is already in `.internal-dev/notes/alpha-deferred-targets.md` or becomes a documented blocker during real validation.

This is a planning artifact for follow-on implementation agents. It intentionally avoids code changes beyond recording the one user-approved deferral.

## Inputs And Assumptions

Confirmed inputs:

- The user wants `.internal-dev/notes/alpha-deferred-targets.md` to be the only place for explicitly deferred alpha targets.
- Existing alpha deferrals include drag-canvas editing, cyclic workflow/retry loops, condition expression evaluation, rich validator feedback loops, and parallel ready-node execution.
- The user explicitly deferred testing and ironing out mid-chat planning and task-planning loops to a future sprint.
- `PlanDefinition` already stores a full finalized-plan shape, including structured inputs/outputs and model/profile fields.
- `WorkflowDefinition` already uses explicit `WorkflowNode` and `WorkflowRoute` records.
- `RuntimeSettingsService` already resolves default/planning/summary/compaction models and default-agent prompt/tools, but does not define a dedicated system-chat config record.
- Current dashboard code renders a freshness placeholder without a server-provided `data-freshness` value.
- Current agent chat has an SSE side-panel implementation, while one visible agent dashboard action links to `/chat?agent=...`.

Assumptions the implementer must verify before coding:

- The large dirty worktree is expected and belongs to ongoing alpha work. Preserve it.
- Docker/Podman should work out of the box for alpha validation, but the exact local daemon/socket state must be validated live.
- "Robust graph editor" for alpha means structured modal/side-panel graph editing over the current node/route schema, not a drag canvas, because drag-canvas editing is already explicitly deferred.
- "Manager type" is the desired user-facing replacement for "Worktype"; internal names may remain temporarily if persistence renaming adds risk.

## Scope

In scope:

- Dashboard visual and wording fixes.
- System chat accordion and config contract.
- Full structured plan editor parity.
- New Plan Chat entrypoint using existing plan chat mechanics.
- Workflow graph editor improvements, including adapters and multiple receivers.
- Project manager-type terminology and agent dropdowns.
- Agent page re-layout, profile tab placement, Docker status accuracy, active tab/nav styling, and agent-specific chat.
- Complete model override coverage and canonical model-key persistence.
- Full test, startup, browser, Docker, and `.internal-dev` closeout gates.

Out of scope:

- Drag-canvas workflow editing.
- Cyclic workflow/retry semantics.
- Conditional expression runtime.
- Parallel ready-node execution.
- Deep mid-chat planning/task-planning loop hardening.
- Replacing `/chat`.
- Broad production security/deployment hardening beyond alpha validation.

## Current-State Analysis

Relevant implementation areas:

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` owns most operational pages and fragments.
- `src/main/resources/static/css/orchestration.css` owns dashboard, agent, and operational surface layout.
- `src/main/resources/static/js/orchestration/dashboard.js` owns the dashboard freshness ticker and settings JS.
- `src/main/resources/static/js/orchestration/agent-chat.js` owns the agent SSE chat panel.
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanDefinition.java` is the persisted plan shape to match in the UI.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java`, `WorkflowNode.java`, `WorkflowRoute.java`, and `WorkflowBinding.java` define the current workflow graph.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettings.java` and `RuntimeSettingsService.java` define current global model/default-agent settings.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java` validates agent default model and tool allowlists.

Known issues from code inspection:

- Dashboard freshness is a UI placeholder/ticker mismatch.
- System chat is a disabled placeholder, not an accordion or configured chat path.
- Side nav and tab active state need route/tab-aware rendering.
- Plan editor is close to schema parity but must be completed against every `PlanDefinition` field and use row-shaped editors everywhere.
- Workflow route rows need labels and a real selected-node/adapter editor.
- Agent profile editing sits in a side column and contributes to horizontal pressure.
- Docker status uses `UNAVAILABLE` when the runtime service bean is absent, but the UI needs to distinguish disabled config from daemon/socket/image/container states.
- Model override fields exist in several layers, but UI and validation must be made consistent and key-based.

## Target Design

The target alpha UI is a server-rendered, HTMX-first operational surface:

- Dashboard sections are visually separated operational panels with truthful counts and no placeholder text.
- System Chat is a bounded operational chat profile with explicit model, prompt, tools, context limit, and enabled state.
- Plans are edited through schema-shaped controls that mirror `PlanDefinition`.
- Workflows use the current node/route graph as the source of truth and expose adapters as persisted nodes or structured node config.
- Projects use manager-type language and structured agent/model selectors.
- Agents use top-level tabs/panels, agent-specific chat, and clear Docker lifecycle diagnostics.
- Model overrides are persisted as configured model keys and resolved to provider remote names only in routing/execution services.

HTMX vs JavaScript:

- HTMX owns CRUD, filters, tabs, row mutation, modal loading, validation fragments, and settings save.
- JavaScript is allowed for SSE chat stream lifecycle and optional local selected-node highlighting or graph-state conveniences.

## Implementation Plan

Execute the phase files in order:

1. `01-dashboard-and-system-chat.md`
2. `02-plan-editor-and-new-plan-chat.md`
3. `03-workflow-graph-editor.md`
4. `04-projects-and-agent-surfaces.md`
5. `05-model-overrides-and-config-contract.md`
6. `06-final-validation-gate.md`

Parallelization guidance:

- Phases 1 and 2 can proceed in parallel only if one owner serializes `OrchestrationController.java` edits.
- Phase 3 should not run alongside another writer touching workflow records, validator, runner, or workflow editor sections.
- Phase 4 should coordinate with Phase 5 for shared model dropdown helpers.
- Phase 6 must be a separate validator after all implementation work is merged.

## Validation Plan

The final validator must run:

- focused phase tests;
- `mvn test`;
- bounded Spring Boot startup;
- Playwright MCP browser validation for `/dashboard`, `/plans`, `/workflows`, `/projects`, `/agents`, `/settings`, and any affected chat/SSE route;
- Docker/Podman validation for Docker-related fixes when local daemon access is available.

If Docker/Podman or model-backed execution is unavailable, stop and ask the user. Do not substitute unit-only validation for alpha signoff.

## Handoff Checklist

- Read this file and the phase file for the assigned work.
- Read the closest package `AGENTS.md` before editing code.
- Read the relevant SimplyPages docs before UI edits.
- Preserve unrelated dirty work.
- Implement the phase directly, not as a review-only artifact.
- Add focused tests and run them.
- Report changed files, tests run, skipped tests, blockers, and any newly discovered out-of-scope items.
- Do not append deferred work anywhere except `.internal-dev/notes/alpha-deferred-targets.md`, and only when the user explicitly agrees it is deferred.

