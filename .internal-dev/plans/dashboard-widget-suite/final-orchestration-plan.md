---
schema_version: 1
document_type: final-orchestration-plan
status: planning
owner: advanced-planner
created: 2026-05-29
work_classification: large
---

# Final Orchestration Plan

## Pre-Dispatch Gate

The main thread must run the requested planning red-team before implementation dispatch:

- Model: `gpt-5.3-codex`
- Reasoning: xhigh
- Mode: non-mutating plan review
- Reject if the plan fails to resolve widget registry, instance/settings, binding, tool descriptors, multi-instance persistence, migration, security/trust boundary, or Playwright criteria.

If any requested model/reasoning cannot be selected, stop and ask the user. Do not substitute without approval; record `TOOLING_CONSTRAINT` if a fallback is later approved.

## Branch And Commit Gate

This is a future implementation plan. Before Phase 01 implementation starts, the main thread must create a dedicated branch for `dashboard-widget-suite`. Commit at the end of each validated phase per repo workflow. This planning task itself must not commit.

## Dispatch Order

1. Phase 01 Platform Foundation: registry, widget instances, settings, binding, migration, route shell.
2. Phase 02 Personal Planning Core: Today Planner, Tasks/Routines, Calendar/Schedule.
3. Phase 03 Notes And Project Context: Notes, Projects, Contacts/Materials.
4. Phase 04 Agent Operational Widgets: Agent Status/Queue, Agent Outputs, Agent Files/Notes.
5. Phase 05 Tracking, Alerts, And Context: Habits/Trackers, Reminders/Alerts, Dashboard Context Panel.
6. Phase 06 Integration, Docs, And Final Validation.

Phase 03 and Phase 04 may be prepared after Phase 01 validation, but do not merge/commit dependent behavior until Phase 02 planner/reminder contracts are stable where they overlap.

## Worker And Validator Policy

- Implementation workers: `gpt-5.5` high.
- Code validators/red-team agents: `gpt-5.5` xhigh.
- Browser/Playwright agents: `gpt-5.5` high unless xhigh is selectable. If unavailable, stop before fallback.
- Every mutating phase gets a fresh code validator after implementation.
- Browser proof is separate from code validation and returns to the validator for reconciliation.
- For code defects, use a fresh scoped repair worker with `gpt-5.5` high unless the issue is an allowed trivial validator edit.
- If the same targeted issue fails validation twice, use a fresh `gpt-5.5` high escalation repair worker unless the user supplies another model.

## Integration Gates

Each phase must pass:

- focused tests for touched services/repositories/controllers/tools;
- bounded startup when schema/wiring/tool registration changes;
- docs/spec impact check;
- validator review;
- Playwright proof for UI-affecting phases.

The final integration validator checks:

- registry/instance/persistence consistency across all widgets;
- route and HTMX target consistency;
- tool descriptor to tool registry consistency;
- service ownership and database boundary adherence;
- docs/spec/changelog/archive completeness;
- evidence JSON consistency;
- stale-reference sweep results.

## Canonical Evidence

Future implementation evidence path:

- `artifacts/dashboard-widget-suite/validation-summary.json`

Planning template included at:

- `.internal-dev/plans/dashboard-widget-suite/artifacts/dashboard-widget-suite/validation-summary.json`
- `.internal-dev/plans/dashboard-widget-suite/shared/evidence-index-contract.md`

The top-level status cannot be `fully_validated` until all validators, startup smoke, browser proof, docs/spec closeout, stale-reference sweep, and residual-risk reconciliation pass.

## Main-Thread Red-Team Checklist

Reject or return to planning if any answer is no:

- Does Phase 01 remove or replace `unique(dashboard_id, widget_key)` for multi-instance widgets?
- Does the registry include settings, binding, refresh, empty-state, renderer, and tool metadata?
- Do worker directives name exact files/modules/routes/tests?
- Are dashboards agent-agnostic while widgets bind to agents/projects/Work Areas?
- Are runtime/project/Work Area/output boundaries preserved?
- Is arbitrary scripted widget execution explicitly out of scope?
- Is calendar required to render as calendar/agenda?
- Are project/household artifacts designed beyond plain project names?
- Are file-backed notes included?
- Are exact Playwright scenarios and visual criteria included?
- Are model/reasoning constraints encoded with `TOOLING_CONSTRAINT` stop behavior?

## Closeout Gates

Before final user reporting in the future implementation:

- Update affected `.internal-dev/specifications/`.
- Record durable decisions for widget registry/instance model, in-dashboard reminder boundary, and project artifact storage.
- Update end-user docs, technical docs, and API docs.
- Add `.internal-dev/changelogs/` entry.
- Move finalized plan/bug artifacts to archive only after implementation is complete and validated.
- Mirror any newly created `.internal-dev/bugs/` reports to GitHub issues if the repository is connected.
- Run stale-reference sweep.
- Ensure evidence index is current and conservative.

## Open Decisions

- External notification delivery is out of scope unless the user explicitly accepts it.
- Third-party calendar rendering library is not accepted by default; workers must return with analysis before adoption.
- Dynamic Spring AI tool registration is not accepted by default; static annotated tools plus registry descriptors are the plan.
- Broad route rename away from current `/dashboards` and `/_dashboards` compatibility is not accepted by default.
