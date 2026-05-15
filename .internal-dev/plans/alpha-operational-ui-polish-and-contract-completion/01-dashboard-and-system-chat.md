# Phase 01 - Dashboard And System Chat

## Context

The dashboard currently renders a disabled "System chat" input, HTMX-loaded stat sections, and a JavaScript freshness ticker. Current code shows `#stat-freshness` starts as `loading`, but no server fragment sets `data-freshness`, so the UI can display a broken freshness token. Inbox wording is approval-centric even though inboxes contain broader messages. The active work, open projects, and agents sections are functional but visually weaker than the running cards and counter cards.

System chat also lacks a clear configuration contract. `RuntimeSettingsService` can resolve default system prompt, approved tools, and default model through the default agent, but there is no explicit system-chat profile with a bounded tool set, model, or context limit.

## Goal

Make the dashboard alpha-credible: no broken freshness text, inbox count means messages, primary dashboard sections read as deliberate operational cards, and system chat is an expandable accordion with a visible "Open Chat View" affordance and a real config source.

## In Scope

- Replace the broken freshness ticker with a server-owned value or remove it.
- Change inbox display from approval-centric wording to `0 messages` / message count wording.
- Add bordered or bubble-style visual separation for Active Work, Open Projects, and Agents.
- Convert System chat from a disabled placeholder into an accordion.
- Define and implement a bounded System Chat config contract.
- Fix side-navigation selected state so Dashboard is not always styled as selected.

## Out of Scope

- Replacing `/chat`.
- Building autonomous system-agent execution beyond a bounded dashboard chat entrypoint.
- Adding new long-running orchestration machinery.

## Implementation Steps

1. Inspect `OrchestrationController.dashboardStatusStrip()`, `dashboardStats()`, `dashboardMainLayout()`, `dashboardHxSection()`, `dashboardHxSideSection()`, and `buildSideNav()`.
2. Remove the orphan `dashboard-stat-freshness` card unless the implementer can make it truthful in one small change. Preferred alpha behavior: no freshness card until the backend has a real last-refresh timestamp.
3. If keeping freshness, make `/dashboard/_stats` return a `data-freshness="<Instant>"` on the rendered stat strip and make `dashboard.js` read that value from the swapped fragment. Acceptance: the page never shows `DATAFRESHNESS`, `loading`, or stale literal placeholder text after HTMX load.
4. Change dashboard inbox counts and labels from approval-only language to generic messages. Use the runtime inbox where the surface means agent/operator messages and the workflow inbox only where a gate/approval count is explicitly intended.
5. Add CSS classes in `orchestration.css` for dashboard section cards:
   - bordered section container with stable radius no larger than existing card radius;
   - spacing between Active Work, Open Projects, and Agents;
   - card/bubble styling matching `.agent-counter-card` or `.orch-card` without nesting cards inside cards.
6. Replace `dashboardChatBand()` with a semantic accordion using `<details>`/`<summary>` or SimplyPages equivalent:
   - collapsed label: `System Chat`;
   - inside content: button/link labeled `Open Chat View`;
   - if chat is wired in this phase, use a narrowly scoped operational endpoint, not `/chat` mutation.
7. Add a System Chat config record. Preferred shape:
   - `systemChatModel`
   - `systemChatPrompt`
   - `systemChatApprovedTools`
   - `systemChatContextLimit`
   - `systemChatEnabled`
   Store this with runtime settings if it is operator-editable, or as a default-agent-derived read model if the implementation proves a separate profile is unnecessary.
8. Add settings UI fields for the system-chat config using HTMX form submission. JavaScript is not justified for ordinary settings save.
9. Fix side nav active state by passing the current route into nav construction or adding a small helper like `buildSideNav(String activePath)`. Acceptance: `/dashboard`, `/plans`, `/workflows`, `/projects`, `/agents`, `/outputs`, and `/settings` each highlight only their own nav item.

## Validation

- Controller tests prove `/dashboard` contains no disabled placeholder chat input and no stale `loading`/`DATAFRESHNESS` text after `/dashboard/_stats` renders.
- Controller tests prove inbox stat wording is message-based.
- Controller tests or HTML assertions prove nav active class moves per route.
- Runtime settings tests cover system-chat config validation, especially unknown model keys and unknown tool names.
- Playwright MCP validates `/dashboard` at desktop and mobile widths: accordion expands, `Open Chat View` is visible only when expanded, primary dashboard sections are visually separated, and no dashboard text overlaps.

## Exit Criteria

- Dashboard notes are fixed in live UI, not just in static HTML.
- System chat config source is documented in code and surfaced in settings or default-agent config.
- Any JavaScript left in dashboard is limited to the freshness ticker or live chat behavior and has a clear path-of-least-resistance comment.

