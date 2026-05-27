---
schema_version: 1
document_type: web-specification
status: active
owner: web
created: 2026-05-25
---

# Web Specification

## Intended Contract

Magenta web surfaces are operational tools, not marketing pages. They should be dense, readable, HTMX-first where feasible, and validated with focused browser checks when UI behavior changes.

## Web Entries

| id | page_or_fragment | status | intended_contract | observed_anchors | interaction_rule | validation | related_decisions | related_knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| WEB-20260525-01 | Avatar dashboard | active | Rectify Avatar dashboard issues and continue feature iteration while preserving the operational console visual system. | `/avatar`, `/dashboard`, `/agents` | HTMX-first fragments; narrow JS only for streaming, local resizing, or path-of-least-resistance behavior. | Focused Playwright screenshots and visual critique for UI changes. | `DECISION-20260524-01`, `DECISION-20260523-03` | `simplypages-avatar-layout-and-editing.md`, `avatar-shell-resizable-rail-geometry.md` |
| WEB-20260525-02 | Avatar visual contract | active | Use dense panels, compact controls, thin blue-gray borders, low shadow, small radii, semantic chips, compact headings, and useful first-viewport density. | Existing dashboard and per-agent dashboard routes | Avoid loose widget collages, hero sections, oversized personal-product cards, browser-default controls, and nested decorative cards. | Compare screenshots against `/dashboard` and `/agents`. | `DECISION-20260522-07` | `simplypages-avatar-layout-and-editing.md` |
| WEB-20260525-03 | Avatar shell | active | Agent-style tabbed shell with persistent chat rail; only dashboard tab is layout-editable. | Avatar shell baseline | Keep tab state addressable and normalize non-dashboard edit mode. | Controller tests and browser tab/rail checks. | `DECISION-20260524-01` | `avatar-shell-resizable-rail-geometry.md` |
| WEB-20260525-04 | Work Area explorer | active | Use Magenta-local details/list fragments with separate inspector and stable table columns, full-row selection, and modal tag management from the inspector. | Avatar Work Areas browser | OOB table/inspector/modal refreshes; direct service validation for paths/destinations and single-target tag params. | Service/controller tests and focused browser validation. | `DECISION-20260524-03` | `workspace-file-explorer-details-list-rewrite.md` |
| WEB-20260525-05 | Top navigation | active | Primary shell nav links should be consistent across home, chat, dashboard, and Avatar shells; full-page shell nav uses full-page anchors unless HTMX swapping is explicitly intended. | `AppNavigation` and shell builders | Avoid accidental HTMX route swaps for full document navigation. | Controller tests. | none | `shell-navigation-htmx-vs-full-page.md` |
| WEB-20260525-06 | Agent history tab content | deferred | Replace static history placeholders with real agent run history when the query contract is accepted. | Agent detail history tab | Pagination/date filtering can be separate scope. | Controller/service/browser checks. | none | `agent-detail-workspace-health-pattern.md` |
| WEB-20260525-07 | Browser chat mid-turn queue | active | The `/chat` composer shows server-backed queued cards between the planning panel and input when normal messages are submitted during an active stream. The client must keep `?conversationId=` aligned with the active chat so plain reload restores queued cards, then retry draining until the existing active stream lock clears. Pending-card renders must be scoped to the active conversation and latest pending-list request so stale `CLAIMED` responses cannot repaint a drained queue. | `#chat-queued-messages-panel`, `chat-client.js`, `.planning-question-card` | Planning answers continue through plan answer routes; slash commands submitted mid-turn show wait feedback instead of queueing. Background queued drains may continue and ack after session switch, but must not steal the visible chat session or append messages into another active conversation. | Focused Playwright checks for slow-turn queueing, session-switch/away-and-return stale-card cleanup, reload persistence, FIFO drain, command/planning negatives, desktop/mobile screenshots. | none | `event-delegation-sse-dom-replacement.md`, `chat-planning-composer-architecture.md` |
| WEB-20260525-08 | Browser chat model selectors | active | The `/chat` Agent Model and Planning Model selectors display configured model aliases as the visible option text and submit alias keys as values. Provider `remoteModelName` values remain routing details, not primary chat UI labels. | `FrontendController.chatToolbar()`, `ChatService.availableModelOptions()` | If a selected/default value is only known by remote model name, map it back to the configured alias before selecting the option. | Controller rendering tests and focused browser check when selector behavior changes. | none | `spring-ai-model-options-routing.md` |
| WEB-20260526-01 | Workspace and project browser MVP | active | MVP file browsing/editing focuses on selected Work Areas and project directories. Work Area markdown/text editing uses a compact Avatar-style editor with explicit save, local undo/redo/revert controls, and unsaved markdown preview/split modes. Internal agent workspace roots, run staging, system outputs, and structural internals are not normal management surfaces; they may be read-only diagnostics in future scope. | Work Area explorer, project surfaces, output views | Browser interactions stay HTMX-first and service-owned; JavaScript is narrow and justified only when it remains the simpler path. | Focused Playwright screenshots and visual critique for changed file-browser/project surfaces. | `DECISION-20260526-01` | `workspace-file-explorer-details-list-rewrite.md` |
| WEB-20260526-02 | Agent Skills browser/editor and guided creation MVP | active | Provide operational `/skills` management surfaces for root-repository skills and per-agent assignment with HTMX-first fragments. UI must clearly separate implemented MVP behavior from deferred scope and must not imply UI script execution support. | `/skills` operational shell and fragments implemented for list/filter/detail, diagnostics, directory overview, file viewer/editor, add-file flow, optional top-level directory creation, guided scaffold creation, refresh, and agent assignment controls. | Standard CRUD/filter/row actions use HTMX; catalog-affecting mutations return OOB `#skills-list` refreshes; no custom skills JavaScript is required. MVP only manages root `skills/` repository plus agent assignment. | Controller/fragment tests cover shell/nav, list/filter, diagnostics, editor/save, file create, guided creation, assignment fragments, and OOB list refresh after catalog-affecting mutations. Phase 06 reconciled Playwright proof after first-run selector/path-script false negatives and recorded corrected evidence. | `DECISION-20260526-03`, `DECISION-20260526-06` | `agent-skills-specification-reference.md`, `agent-skills-ui-htmx-pattern.md` |

## Ownership Boundary

This file owns page/fragment UX contracts. SimplyPages component mechanics belong in `simplypages.md`; routes/payloads belong in `api.md`.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-06 | watching | Some history and observability panels are baseline or placeholder surfaces. | Deferred features before expanding UI claims. | 2026-06-24 |

## Validation Expectations

UI changes require focused Playwright validation under the repo policy, including screenshots and a visual quality critique. Browser checks should cover alignment, spacing, density, overflow, mobile stacking, and whether the page uses available space coherently.

## Related Decisions

`DECISION-20260522-07`, `DECISION-20260523-03`, `DECISION-20260524-01`, `DECISION-20260524-03`.

## Related Knowledge

Search knowledge filenames for `avatar`, `dashboard`, `shell`, `workspace-file`, `htmx`, and `simplypages`.
