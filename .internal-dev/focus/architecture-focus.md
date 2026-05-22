---
schema_version: 1
document_type: architecture-focus
last_reviewed: 2026-05-22
owner: unassigned
status: active
---

# Architecture Focus

## Active Architecture Focus

| id | area | direction | status | owner | source | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
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
| 2026-05-22 | codex | updated | Updated Avatar runtime boundary notes after final dashboard integration and email-ingress removal. |
| 2026-05-22 | codex | updated | Marked the Avatar runtime boundary active after Phase 03 wired operational tools and side-panel agent chat through existing services. |
| 2026-05-22 | codex | updated | Marked Avatar data ownership active after the `avatar.sqlite` datasource and schema landed in Phase 01. |
| 2026-05-22 | codex | updated | Added Avatar data ownership and runtime-boundary architecture focus from the planning suite. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
