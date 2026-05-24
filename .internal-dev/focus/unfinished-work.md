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
| UNFINISHED-20260524-05 | Release SimplyPages file explorer module for Magenta CI portability. | deferred | Merge/release SimplyPages PR #73 or otherwise publish the reusable file explorer module under the Magenta dependency coordinate before opening a Magenta PR that must pass clean CI on another machine. | unassigned | .internal-dev/changelogs/2026-05-24-workspace-file-explorer-ui.md | 2026-05-24 | 2026-05-31 | Local validation installed the PR-built SimplyPages jar as `io.mindspice:simplypages:1.1.0a`; this is acceptable for local integration but not a durable CI dependency until the upstream module is released. |
| UNFINISHED-20260524-03 | Replace workspace file explorer message-based API error mapping with typed domain errors. | deferred | After the workspace file explorer route/API surface stabilizes, introduce explicit domain error codes or typed exceptions in workspace services and map them in `WorkAreaController` without relying on exception-message text. | unassigned | .internal-dev/changelogs/2026-05-24-workspace-file-explorer-phase-2.md | 2026-05-24 | 2026-06-23 | Phase 2 kept message-based mapping as a pragmatic bridge for `400`/`404`/`409`; typed errors are deferred to avoid broadening the API contract while UI/module phases are still moving. |
| UNFINISHED-20260524-02 | Expand Avatar history tab beyond the baseline recent-work fallback. | deferred | After the shell baseline lands, design the smallest useful history expansion that combines Avatar user-surface chat history, reserved Avatar-agent runtime history, and output history without introducing a new persistence model. | unassigned | .internal-dev/changelogs/2026-05-24-avatar-shell-baseline-refactor.md | 2026-05-24 | 2026-06-23 | The baseline history tab intentionally ships as a compact recent-work fallback so the shell and navigation contract can land first. |
| UNFINISHED-20260524-01 | Design Avatar automatic refresh after shell baseline removes manual refresh. | deferred | After the Avatar shell baseline refactor lands, design the smallest acceptable auto-refresh policy for dashboard/tab surfaces, including scope, cadence, triggers, and whether any panels should remain manual-only. | unassigned | .internal-dev/plans/avatar-shell-baseline-refactor/implementation-plan.md | 2026-05-24 | 2026-06-23 | User explicitly chose to remove visible manual refresh in the baseline pass and defer interval refresh to later work. |
| UNFINISHED-20260523-01 | Decide whether to migrate historical untagged chat sessions. | deferred | If legacy `/chat` history visibility matters, design a one-time migration or operator repair path that can distinguish browser sessions from Avatar/internal sessions without reintroducing cross-surface leakage. | unassigned | .internal-dev/changelogs/2026-05-22-chat-session-scope-filter.md | 2026-05-23 | 2026-06-22 | The session-scope fix hides untagged legacy conversations from `/chat` rather than risking Avatar/agent/internal leakage. |
| UNFINISHED-20260523-02 | Decide whether planner recurrence should trigger automation. | deferred | If planner tasks should drive reminders, user contact, wait-for-input flows, or assignment creation, design that scheduler/automation separately from the v1 organizer records. | unassigned | .internal-dev/changelogs/2026-05-23-avatar-planner-organizer.md | 2026-05-23 | 2026-06-22 | V1 stores planner tasks, recurrence, subtodos, projections, and work links only; it intentionally does not automate execution. |
| UNFINISHED-20260522-04 | Design future email processing through scripting or internal messaging. | deferred | Plan a non-public ingestion path using the scripting API, internal messaging, or approved agent tools after endpoint lockdown/redaction rules are designed. | unassigned | .internal-dev/changelogs/2026-05-22-avatar-email-ingress-correction.md | 2026-05-22 | 2026-06-21 | Dwight rejected the first-pass public-ish Avatar email endpoint; the sprint removed it and left email processing as later scope. |
| UNFINISHED-20260522-03 | Decide whether to implement an Avatar plugin/scripting runtime. | deferred | Use the plugin research review before choosing Kawa, a safer DSL, Java SPI, or no runtime. | unassigned | .internal-dev/reviews/2026-05-22-avatar-plugin-system-research.md | 2026-05-22 | 2026-06-21 | Sprint scope was research-only; no plugin runtime was implemented. |

## Recently Closed

| id | title | status | owner | source | closed_on | notes |
| --- | --- | --- | --- | --- | --- | --- |
| UNFINISHED-20260524-04 | Complete resumed workspace file explorer Phase 3-6 pipeline. | closed | codex | .internal-dev/reviews/2026-05-24-workspace-file-explorer-orchestration-audit.md | 2026-05-24 | Closed after the reusable SimplyPages module was validated and published as draft PR #73, Magenta consumed it in the Avatar Work Areas explorer, browser blockers were remediated, focused tests passed, and delegated Playwright validation passed. |
| UNFINISHED-20260523-04 | Review and plan Avatar high-level visual polish. | closed | unassigned | .internal-dev/changelogs/2026-05-23-avatar-ui-polish.md | 2026-05-23 | Closed by the advanced planning and implementation pass that collapsed empty rows, focused add-widget selection, strengthened Avatar chat hierarchy, constrained noisy lists, and passed delegated Playwright visual validation. |
| UNFINISHED-20260523-03 | Polish Avatar edit-mode control hierarchy and mobile ergonomics. | closed | unassigned | .internal-dev/changelogs/2026-05-23-avatar-simplypages-demo-parity-refactor.md | 2026-05-23 | Closed by replacing heavy edit-mode panels with SimplyPages demo-style top-corner decorators, row micro controls, add-widget sections, insert-row catalog behavior, and delegated Playwright visual validation. |
| UNFINISHED-20260522-01 | Confirm the first durable current focus. | closed | user | .internal-dev/plans/.archive/avatar-dashboard-sprint/README.md | 2026-05-22 | Avatar was selected as the first durable implementation focus; current pass remains planning-only. |
| UNFINISHED-20260522-02 | Create the Avatar dashboard sprint plan suite. | closed | unassigned | .internal-dev/plans/.archive/avatar-dashboard-sprint/final-orchestration-plan.md | 2026-05-22 | Planning suite was created as a handoff for later implementation. |

## Review Log

| reviewed_on | reviewer | outcome | notes |
| --- | --- | --- | --- |
| 2026-05-24 | codex | closed | Closed the resumed workspace file explorer Phase 3-6 recovery item after upstream validation, draft SimplyPages PR #73, Magenta UI integration, focused Maven tests, Spring startup, and delegated Playwright validation passed. |
| 2026-05-24 | codex | updated | Added deferred release/CI-portability follow-up for publishing the SimplyPages file explorer module consumed by Magenta. |
| 2026-05-24 | codex | updated | Added active recovery item for completing workspace file explorer Phase 3-6 after orchestration audit identified the earlier premature stop. |
| 2026-05-24 | codex | updated | Added deferred typed-domain-error cleanup for the workspace file explorer API after Phase 2 intentionally kept message-based status mapping as a bridge. |
| 2026-05-24 | codex | updated | Added the deferred Avatar history-tab follow-up after shipping the shell baseline with a deliberate recent-work fallback instead of expanding persistence scope. |
| 2026-05-24 | codex | updated | Added the explicitly deferred Avatar auto-refresh follow-up while planning the shell baseline refactor that removes visible manual refresh controls. |
| 2026-05-23 | codex | closed | Closed Avatar high-level visual polish after the approved review criteria were implemented and browser-validated. |
| 2026-05-23 | codex | updated | Added a needs-triage Avatar visual polish item from the high-level design review requested after visual validation. |
| 2026-05-23 | codex | closed | Closed the deferred Avatar edit-mode density item after the SimplyPages demo parity refactor passed delegated Playwright visual validation. |
| 2026-05-23 | codex | updated | Added deferred Avatar edit-mode visual polish after Playwright validation accepted the functional refactor with non-blocking density concerns. |
| 2026-05-23 | codex | updated | Added deferred decision for historical untagged chat-session migration after introducing explicit chat surface filtering. |
| 2026-05-23 | codex | updated | Added deferred planner automation decision after the Avatar planner organizer implementation. |
| 2026-05-22 | codex | updated | Added deferred closeout items for future email processing and plugin/scripting runtime decisions after Avatar sprint implementation. |
| 2026-05-22 | codex | updated | Closed the initial current-focus confirmation and recorded completion of this planning-suite handoff. |
| 2026-05-22 | codex | initialized | Created strict-schema living document. |
| 2026-05-22 | codex | updated | Added the remaining user decision needed to populate the first real current focus. |
