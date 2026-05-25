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
| WEB-20260525-04 | Work Area explorer | active | Use Magenta-local details/list fragments with separate inspector and stable table columns. | Avatar Work Areas browser | OOB table/inspector/modal refreshes; direct service validation for paths/destinations. | Service/controller tests and focused browser validation. | `DECISION-20260524-03` | `workspace-file-explorer-details-list-rewrite.md` |
| WEB-20260525-05 | Top navigation | active | Primary shell nav links should be consistent across home, chat, dashboard, and Avatar shells; full-page shell nav uses full-page anchors unless HTMX swapping is explicitly intended. | `AppNavigation` and shell builders | Avoid accidental HTMX route swaps for full document navigation. | Controller tests. | none | `shell-navigation-htmx-vs-full-page.md` |
| WEB-20260525-06 | Agent history tab content | deferred | Replace static history placeholders with real agent run history when the query contract is accepted. | Agent detail history tab | Pagination/date filtering can be separate scope. | Controller/service/browser checks. | none | `agent-detail-workspace-health-pattern.md` |
| WEB-20260525-07 | Browser chat mid-turn queue | active | The `/chat` composer shows server-backed queued cards between the planning panel and input when normal messages are submitted during an active stream. The client must keep `?conversationId=` aligned with the active chat so plain reload restores queued cards, then retry draining until the existing active stream lock clears. Pending-card renders must be scoped to the active conversation and latest pending-list request so stale `CLAIMED` responses cannot repaint a drained queue. | `#chat-queued-messages-panel`, `chat-client.js`, `.planning-question-card` | Planning answers continue through plan answer routes; slash commands submitted mid-turn show wait feedback instead of queueing. Background queued drains may continue and ack after session switch, but must not steal the visible chat session or append messages into another active conversation. | Focused Playwright checks for slow-turn queueing, session-switch/away-and-return stale-card cleanup, reload persistence, FIFO drain, command/planning negatives, desktop/mobile screenshots. | none | `event-delegation-sse-dom-replacement.md`, `chat-planning-composer-architecture.md` |

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
