---
schema_version: 1
document_type: unfinished-work
last_reviewed: 2026-05-23
owner: unassigned
status: active
---

# Unfinished Work

## Open Items

| id | title | status | next_action | owner | source | created | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| UNFINISHED-20260523-01 | Decide whether to migrate historical untagged chat sessions. | deferred | If legacy `/chat` history visibility matters, design a one-time migration or operator repair path that can distinguish browser sessions from Avatar/internal sessions without reintroducing cross-surface leakage. | unassigned | .internal-dev/changelogs/2026-05-22-chat-session-scope-filter.md | 2026-05-23 | 2026-06-22 | The session-scope fix hides untagged legacy conversations from `/chat` rather than risking Avatar/agent/internal leakage. |
| UNFINISHED-20260523-02 | Decide whether planner recurrence should trigger automation. | deferred | If planner tasks should drive reminders, user contact, wait-for-input flows, or assignment creation, design that scheduler/automation separately from the v1 organizer records. | unassigned | .internal-dev/changelogs/2026-05-23-avatar-planner-organizer.md | 2026-05-23 | 2026-06-22 | V1 stores planner tasks, recurrence, subtodos, projections, and work links only; it intentionally does not automate execution. |
| UNFINISHED-20260522-04 | Design future email processing through scripting or internal messaging. | deferred | Plan a non-public ingestion path using the scripting API, internal messaging, or approved agent tools after endpoint lockdown/redaction rules are designed. | unassigned | .internal-dev/changelogs/2026-05-22-avatar-email-ingress-correction.md | 2026-05-22 | 2026-06-21 | Dwight rejected the first-pass public-ish Avatar email endpoint; the sprint removed it and left email processing as later scope. |
| UNFINISHED-20260522-03 | Decide whether to implement an Avatar plugin/scripting runtime. | deferred | Use the plugin research review before choosing Kawa, a safer DSL, Java SPI, or no runtime. | unassigned | .internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md | 2026-05-22 | 2026-06-21 | Sprint scope was research-only; no plugin runtime was implemented. |

## Recently Closed

| id | title | status | owner | source | closed_on | notes |
| --- | --- | --- | --- | --- | --- | --- |
| UNFINISHED-20260522-01 | Confirm the first durable current focus. | closed | user | .internal-dev/plans/.archive/avatar-dashboard-sprint/README.md | 2026-05-22 | Avatar was selected as the first durable implementation focus; current pass remains planning-only. |
| UNFINISHED-20260522-02 | Create the Avatar dashboard sprint plan suite. | closed | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/final-orchestration-plan.md | 2026-05-22 | Planning suite was created as a handoff for later implementation. |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-23 | codex | updated | Added deferred decision for historical untagged chat-session migration after introducing explicit chat surface filtering. |
| 2026-05-23 | codex | updated | Added deferred planner automation decision after the Avatar planner organizer implementation. |
| 2026-05-22 | codex | updated | Added deferred closeout items for future email processing and plugin/scripting runtime decisions after Avatar sprint implementation. |
| 2026-05-22 | codex | updated | Closed the initial current-focus confirmation and recorded completion of this planning-suite handoff. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
| 2026-05-22 | codex | updated | Added the remaining user decision needed to populate the first real current focus. |
