---
schema_version: 1
document_type: decisions
last_reviewed: 2026-05-22
owner: unassigned
status: active
---

# Decisions

## Active Decisions

| id | decision | status | owner | source | decided_on | knowledge_ref | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DECISION-20260522-02 | Summary-style internal jobs use `summaryModel`; context compaction uses `compactionModel` with summary fallback. | active | unassigned | chat summary/title model fix | 2026-05-22 | .internal-dev/knowledge/summary-title-model-selection.md | 2026-06-21 | Conversation title generation is intentionally decoupled from the selected chat model. |
| DECISION-20260522-01 | Initialize `.internal-dev/focus/` as strict-schema living documents without migrating existing notes. | active | unassigned | task request | 2026-05-22 | .internal-dev/knowledge/internal-dev-focus-workflow.md | 2026-06-21 | Focus workflow knowledge captures the reusable maintenance rules. |

## Superseded Decisions

| id | decision | status | owner | source | decided_on | superseded_on | superseded_by | knowledge_ref | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-22 | codex | updated | Added durable summary/title/compaction model routing decision. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
