---
schema_version: 1
document_type: ideas-inbox
last_reviewed: 2026-05-25
owner: unassigned
status: active
---

# Ideas Inbox

## Intake

| id | idea | status | owner | source | captured | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| IDEA-20260525-03 | Add a scheduler-style wait primitive for long-running agent coordination, with clear main-thread status updates. | needs-triage | unassigned | .internal-dev/notes/.archive/idea_drop.md | 2026-05-25 | 2026-06-24 | Consolidated from archived raw notes during internal-dev hygiene; evaluate against current AgentMail and orchestration wait workflows. |
| IDEA-20260525-02 | Keep using git commits as explicit handoff inputs/outputs for implementation and validation phases. | needs-triage | unassigned | .internal-dev/notes/.archive/idea_drop.md | 2026-05-25 | 2026-06-24 | Consolidated from archived raw notes; likely process/decision candidate once standardized. |
| IDEA-20260525-01 | Preserve optional prior-chat context in planning mode while keeping clean-context execution as default. | needs-triage | unassigned | .internal-dev/notes/.archive/scratch.md | 2026-05-25 | 2026-06-24 | Consolidated from archived scratch notes; requires explicit product decision on default behavior and user controls. |
| IDEA-20260523-01 | Improve Avatar layout widget catalog empty-state flow when every first-party widget already exists. | needs-triage | unassigned | Playwright validation for .internal-dev/changelogs/2026-05-23-avatar-layout-editor-ui.md | 2026-05-23 | 2026-06-22 | V1 correctly enforces single widget instances, but the add-widget catalog can show only disabled `Added` entries until a user removes a widget elsewhere. Consider clearer guidance, relocation affordances, or future multi-instance support. |

## Promoted

| id | idea | promoted_to | promoted_on | notes |
| --- | --- | --- | --- | --- |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-25 | codex | updated | Consolidated raw idea fragments from archived `notes/idea_drop.md` and `notes/scratch.md` into intake rows; archived source notes with a hygiene consolidation map. |
| 2026-05-23 | codex | updated | Captured Avatar widget catalog UX friction found during Playwright validation. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
