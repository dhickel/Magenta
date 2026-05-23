---
schema_version: 1
document_type: architecture-focus
last_reviewed: 2026-05-23
owner: unassigned
status: active
---

# Architecture Focus

## Active Architecture Focus

| id | area | direction | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| ARCH-20260523-01 | Avatar Work Areas and dashboard refactor. | Treat Work Areas as runtime-owned metadata around confined agent/project directories while keeping Avatar layout and planner data in `avatar.sqlite`. | active | unassigned | .internal-dev/changelogs/2026-05-23-avatar-ui-polish.md | 2026-06-22 | Implemented direction: selected Work Area becomes assignment `workspace/`, broader owned root becomes `root/`, output routing is explicit assignment metadata, planner organizer data remains non-executable Avatar data, and dashboard layout editing remains in-place using SimplyPages row/module patterns. UI polish now collapses empty rows in rendering, uses a focused add-widget picker, and strengthens Avatar chat without importing the full `/chat` client. |
| ARCH-20260522-04 | Avatar data ownership. | Store Avatar user-centric profile, preferences, organizer data, dashboard layout, facts, and events in separate `avatar.sqlite` under the Magenta root. | active | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/phase-01-avatar-core-persistence.md | 2026-06-21 | Phase 01 implemented the separate datasource/schema; existing orchestration/runtime state stays in `magenta.sqlite`; no cross-database foreign keys. |
| ARCH-20260522-03 | Avatar runtime boundary. | Build Avatar on existing chat, tool, agent profile, assignment, workspace, schedule, reaction, and output services rather than creating a second runtime. | active | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/phase-04-avatar-assistant-behaviors.md | 2026-06-21 | Sprint implementation kept Avatar on existing services; Phase 05 added the `/avatar` surface and removed the first-pass email HTTP ingress after user review. |

## Constraints

| id | constraint | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |
| ARCH-20260522-02 | Do not migrate existing architecture notes into this file during initialization. | active | unassigned | task request | 2026-06-21 | This file is additive living focus, not a replacement for any existing note. |

## Open Questions

| id | question | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-23 | codex | updated | Refreshed Avatar dashboard refactor notes after the approved visual polish pass tightened empty rows, picker behavior, chat hierarchy, and list constraints without changing runtime boundaries. |
| 2026-05-23 | codex | updated | Refreshed Avatar layout notes after the SimplyPages demo parity pass replaced heavy edit panels with live row/module decorators and insertion controls. |
| 2026-05-23 | codex | reviewed | Avatar visual layout refactor preserves existing Avatar data/runtime boundaries while making rendered dashboard layout the editing source of truth. |
| 2026-05-23 | codex | updated | Added Avatar Work Area/layout refactor direction from the new implementation plan suite. |
| 2026-05-23 | codex | updated | Refreshed Avatar Work Area/layout refactor notes after implementation landed on the feature branch. |
| 2026-05-22 | codex | updated | Updated Avatar runtime boundary notes after final dashboard integration and email-ingress removal. |
| 2026-05-22 | codex | updated | Marked the Avatar runtime boundary active after Phase 03 wired operational tools and side-panel agent chat through existing services. |
| 2026-05-22 | codex | updated | Marked Avatar data ownership active after the `avatar.sqlite` datasource and schema landed in Phase 01. |
| 2026-05-22 | codex | updated | Added Avatar data ownership and runtime-boundary architecture focus from the planning suite. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
