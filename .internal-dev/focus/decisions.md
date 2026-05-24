---
schema_version: 1
document_type: decisions
last_reviewed: 2026-05-24
owner: unassigned
status: active
---

# Decisions

## Active Decisions

| id | decision | status | owner | source | decided_on | knowledge_ref | review_after | notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| DECISION-20260524-02 | Advanced planning, orchestration, implementation, and validation/red-team roles live in global Codex custom agents, while `advanced-planner` and `orchestrate-plan` remain compact trigger routers. | active | unassigned | .internal-dev/changelogs/2026-05-24-codex-custom-subagent-routing.md | 2026-05-24 | .internal-dev/knowledge/codex-custom-subagent-routing.md | 2026-06-23 | Repos with `.internal-dev` use `.internal-dev/plans/<task-slug>/` and `.internal-dev/reviews/` as the default durable planning/review stores for these subagent workflows. |
| DECISION-20260524-01 | Avatar uses an agent-style tabbed shell with a persistent right chat rail, and only the dashboard tab is layout-editable. | active | unassigned | .internal-dev/changelogs/2026-05-24-avatar-shell-baseline-refactor.md | 2026-05-24 | docs/technical/avatar-dashboard-fragments.md | 2026-06-23 | The shell removes the top-level Organizer and manual refresh controls, keeps tab state in the URL, and persists desktop rail width in browser-local state instead of adding a new server-side model. |
| DECISION-20260523-05 | API enum wire values should be normalized narrowly at the JSON boundary, and empty submitted jobs complete as no-op job runs unless product policy changes to reject them at submission. | active | unassigned | .internal-dev/changelogs/2026-05-23-chat-surface-and-empty-job-run-fixes.md | 2026-05-23 | .internal-dev/knowledge/api-boundary-enums-and-empty-job-run-completion.md | 2026-06-22 | Chat surface values accept known names case-insensitively while rejecting blank/unknown values; empty job submissions leave assignment-owned `job_runs` terminal instead of `RUNNING`. |
| DECISION-20260523-04 | Long-running email-coordinated work records inbound AgentMail instructions in `.internal-dev/inbox` before dispatching implementation. | active | unassigned | .internal-dev/inbox/README.md | 2026-05-23 | .internal-dev/inbox/README.md | 2026-06-22 | Acknowledge inbound email first, check for additional messages, summarize actionable instructions in the inbox queue, and move handled messages to the read ledger. |
| DECISION-20260523-03 | Avatar dashboard layout editing happens in place on the rendered dashboard surface, with modal/detail flows reserved for module-specific iteration. | active | unassigned | .internal-dev/changelogs/2026-05-23-avatar-simplypages-demo-parity-refactor.md | 2026-05-23 | .internal-dev/knowledge/simplypages-avatar-layout-and-editing.md | 2026-06-22 | Future layout agents must compare against the SimplyPages editing demo and validate compact top-corner decorators, add-widget sections, insert-row separators, and practical visual quality with Playwright. |
| DECISION-20260523-02 | The Avatar UI refactor uses SimplyPages-native row/column layout editing and runtime-owned Work Areas instead of restyling the old flat widget layout. | active | unassigned | .internal-dev/plans/.archive/avatar-agent-ui-refactor/implementation-plan.md | 2026-05-23 | .internal-dev/knowledge/avatar-work-area-ui-refactor-implementation.md | 2026-06-22 | Implementation preserves Avatar-on-existing-runtime boundary; Work Area selection and output routing are explicit assignment metadata, and planner recurrence remains non-automated. |
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
| 2026-05-24 | codex | updated | Added the durable Codex custom-subagent routing decision and `.internal-dev` planning-store requirement for advanced planning/orchestration workflows. |
| 2026-05-24 | codex | updated | Added the durable Avatar shell decision after landing the tabbed shell, persistent chat rail, dashboard-only edit scope, and browser-local desktop divider persistence. |
| 2026-05-23 | codex | updated | Added API enum normalization and empty submitted job no-op completion decision from GitHub #6/#7 remediation. |
| 2026-05-23 | codex | updated | Added durable AgentMail inbox coordination decision after Dwight requested persistent email intake during remote work. |
| 2026-05-23 | codex | updated | Tightened the Avatar in-place layout editing decision after replacing the heavy edit-mode panels with SimplyPages demo-style decorators and insertion controls. |
| 2026-05-23 | codex | updated | Added durable in-place Avatar dashboard layout editing and Playwright visual-validation decision. |
| 2026-05-23 | codex | updated | Added Avatar Work Area and SimplyPages layout-editor planning decision. |
| 2026-05-23 | codex | updated | Updated Avatar Work Area/layout decision to point at implementation knowledge after the branch landed. |
| 2026-05-23 | codex | updated | Added durable chat-session surface filtering decision from the `/chat` sidebar scope fix. |
| 2026-05-22 | codex | updated | Added Avatar dashboard HTMX/chat-client decision from Phase 05. |
| 2026-05-22 | codex | updated | Added Phase 03 operational tools/runtime-boundary decision. |
| 2026-05-22 | codex | updated | Added Avatar sprint architecture/process decision from the planning suite. |
| 2026-05-22 | codex | updated | Added global email follow-up wait skill decision. |
| 2026-05-22 | codex | updated | Added non-compacting context snapshot decision for history reload and completed execution finalization. |
| 2026-05-22 | codex | updated | Added durable summary/title/compaction model routing decision. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
