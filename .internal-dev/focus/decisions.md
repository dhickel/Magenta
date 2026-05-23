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
| DECISION-20260523-02 | The next Avatar UI refactor will use SimplyPages-native row/column layout editing and runtime-owned Work Areas instead of restyling the current flat widget layout. | active | unassigned | .internal-dev/plans/avatar-agent-ui-refactor/implementation-plan.md | 2026-05-23 | .internal-dev/knowledge/avatar-work-area-ui-refactor-planning.md | 2026-06-22 | Plan preserves Avatar-on-existing-runtime boundary; Work Area selection and output routing become explicit assignment metadata. |
| DECISION-20260523-01 | Browser `/chat` session lists use explicit chat surface metadata plus normal-mode filtering instead of raw conversation-id enumeration. | active | unassigned | .internal-dev/changelogs/2026-05-22-chat-session-scope-filter.md | 2026-05-23 | .internal-dev/knowledge/chat-session-surface-scope-filter.md | 2026-06-22 | This prevents Avatar, agent, planning, and internal chat conversations from leaking into the browser chat sidebar. |
| DECISION-20260522-07 | Avatar dashboard interactions are HTMX-first and use a compact dedicated chat client instead of the full browser chat client. | active | unassigned | .internal-dev/changelogs/2026-05-22-avatar-dashboard-ui.md | 2026-05-22 | docs/technical/avatar-dashboard-fragments.md | 2026-06-21 | `/avatar` owns widget fragments and layout editing; it does not load `/js/chat-client.js`. |
| DECISION-20260522-06 | Agent operational tools use existing Spring AI tool registration, exact approved-tool names, current orchestration context, and Avatar identity checks instead of a separate operational runtime. | active | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/phase-03-agent-workspace-tooling.md | 2026-05-22 | .internal-dev/changelogs/2026-05-22-agent-operational-tools.md | 2026-06-21 | PLAN/TASK drafting modes exclude `agent_` and `avatar_` tools; side-panel agent chat installs context inside the queued chat turn. |
| DECISION-20260522-05 | Avatar sprint implementation will use separate `avatar.sqlite`, HTMX-first `/avatar`, existing chat/tool/runtime services, and research-only plugin planning. | active | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/README.md | 2026-05-22 | .internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md | 2026-06-21 | Plugin runtime is deferred; Kawa is only a trusted local scripting candidate unless a real sandbox is designed. |
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
| 2026-05-23 | codex | updated | Added Avatar Work Area and SimplyPages layout-editor planning decision. |
| 2026-05-23 | codex | updated | Added durable chat-session surface filtering decision from the `/chat` sidebar scope fix. |
| 2026-05-22 | codex | updated | Added Avatar dashboard HTMX/chat-client decision from Phase 05. |
| 2026-05-22 | codex | updated | Added Phase 03 operational tools/runtime-boundary decision. |
| 2026-05-22 | codex | updated | Added Avatar sprint architecture/process decision from the planning suite. |
| 2026-05-22 | codex | updated | Added global email follow-up wait skill decision. |
| 2026-05-22 | codex | updated | Added non-compacting context snapshot decision for history reload and completed execution finalization. |
| 2026-05-22 | codex | updated | Added durable summary/title/compaction model routing decision. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
