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
| DECISION-20260522-05 | Avatar sprint implementation will use separate `avatar.sqlite`, HTMX-first `/avatar`, existing chat/tool/runtime services, and research-only plugin planning. | active | unassigned | .internal-dev/plans/avatar-dashboard-sprint/README.md | 2026-05-22 | .internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md | 2026-06-21 | Plugin runtime is deferred; Kawa is only a trusted local scripting candidate unless a real sandbox is designed. |
| DECISION-20260522-04 | Reusable email closeout reports and reply waits live in the global `email-followup-wait` skill; `agentmail` remains the transport skill. | active | unassigned | email follow-up wait skill | 2026-05-22 | .internal-dev/knowledge/email-followup-wait-workflow.md | 2026-06-21 | Repo instructions should reference the skill instead of duplicating the full report schema. |
| DECISION-20260522-03 | Chat history reload and completed execution finalization use non-compacting context snapshots; model-backed compaction stays on prompt/maintenance paths. | active | unassigned | chat completion compaction reload repair | 2026-05-22 | .internal-dev/knowledge/chat-completion-context-maintenance.md | 2026-06-21 | Read-only reload must preserve completed transcript visibility even when the compaction model is unavailable. |
| DECISION-20260522-02 | Summary-style internal jobs use `summaryModel`; context compaction uses `compactionModel` with summary fallback. | active | unassigned | chat summary/title model fix | 2026-05-22 | .internal-dev/knowledge/summary-title-model-selection.md | 2026-06-21 | Conversation title generation is intentionally decoupled from the selected chat model. |
| DECISION-20260522-01 | Initialize `.internal-dev/focus/` as strict-schema living documents without migrating existing notes. | active | unassigned | task request | 2026-05-22 | .internal-dev/knowledge/internal-dev-focus-workflow.md | 2026-06-21 | Focus workflow knowledge captures the reusable maintenance rules. |

## Superseded Decisions

| id | decision | status | owner | source | decided_on | superseded_on | superseded_by | knowledge_ref | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-22 | codex | updated | Added Avatar sprint architecture/process decision from the planning suite. |
| 2026-05-22 | codex | updated | Added global email follow-up wait skill decision. |
| 2026-05-22 | codex | updated | Added non-compacting context snapshot decision for history reload and completed execution finalization. |
| 2026-05-22 | codex | updated | Added durable summary/title/compaction model routing decision. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
