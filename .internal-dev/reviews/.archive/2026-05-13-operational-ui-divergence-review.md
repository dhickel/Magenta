## Scope

Audit of operational UI parity against the alpha feature contract for unified plan/task/workflow orchestration, including:
- Plan/task editor behavior
- Agent detail navigation and utility panels
- Model/config surfaces
- Task/workflow chat/user-interaction affordances

Code and artifacts reviewed:
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `.internal-dev/notes/operational-ui-contract-missing-features.md`

## Findings

1. **Fixed now (alpha-blocking UX): list item edits in plan editor now persist inline**
   - Prior behavior: deliverables/steps/validation/assumptions text inputs were editable but not persisted.
   - Fix landed: HTMX `PUT` endpoints plus inline row update wiring.
   - Evidence:
     - `OrchestrationController`: new `@PutMapping` routes for `/plans/_editor/{planId}/{deliverables|steps|validation|assumptions}`
     - `OrchestrationController`: `listSection(...)` now sets named fields + `hx-put` on change

2. **Fixed now (alpha-blocking UX): agent detail tabs are now wired**
   - Prior behavior: tab buttons were inert (`data-tab` only).
   - Fix landed: tab buttons now issue HTMX requests into `#agent-tab-panel`.
   - Evidence:
     - `OrchestrationController`: `tabNav(String agentId, String... names)` adds `hx-get/hx-target/hx-swap`
     - `agentDetailLayout(...)` now passes `agent.id()` into tab rendering

3. **Fixed now (alpha should-fix): model dropdowns in plan/job/project editors now include configured models**
   - Prior behavior: editors rendered `Default` only.
   - Fix landed: `modelSelect(...)` now includes `chatService.availableModels()`.
   - Evidence:
     - `OrchestrationController`: `modelSelect(String name)` updated

4. **Still open (alpha blocker): full plan save path still does not serialize all complex sections from form submit**
   - `PUT /plans/_editor/{planId}` still reconstructs using current persisted collections and ignores submitted list payloads for bulk save semantics.
   - Inline row edits are now persisted, but "single Save as source-of-truth" semantics are still incomplete for full-form parity.

5. **Still open (alpha blocker contract): no task/workflow chat session surface in orchestration UI**
   - Feature contract expects task/workflow user consultation and inbox-mediated responses.
   - Current UI has chat link on agent dashboard and plan "Continue in Chat", but no direct threaded chat surface for task/workflow run instances.

6. **Still open (alpha should-fix): settings remains JS-driven save path**
   - `/settings` Save uses `data-action="save-settings"` and JS transport.
   - No HTMX fallback form submit path for save.

7. **Still open (alpha should-fix): inbox/outputs remain JS-transport pages**
   - Contract direction is HTMX-first for standard CRUD/interactions.
   - Current pages still depend on client JS orchestration for primary content/actions.

8. **Still open (alpha feature gap): workflow gates and user approval messaging are backend-capable but UI-thin**
   - Backend workflow gate nodes and inbox state exist.
   - Operational UI does not yet expose robust gate lifecycle operations as a first-class management flow.

## Risk Assessment

- **Alpha risk remains high** for end-user completeness despite recent UX fixes.
- Core orchestration backend appears materially ahead of UI surface area.
- Most remaining risk clusters are workflow/task conversational UX and operator control flows.

## Recommendations

1. Phase next implementation on **task/workflow run UX completeness**:
   - run detail panel
   - gate wait states
   - user reply path
   - output handoff visibility
2. Add HTMX save/interaction parity for settings/inbox/outputs unless explicit JS exception is accepted and documented.
3. Add controller tests for:
   - list-section `PUT` update routes
   - agent tab HTMX wiring
   - model select population path

## Follow-ups

- Track remaining open parity bugs in `.internal-dev/bugs/2026-05-13-operational-ui-divergence-audit/report.md`.
- Continue remediation in small vertical slices (one surface end-to-end) rather than broad shallow UI spread.
