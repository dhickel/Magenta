# Avatar UI Style Guide Orchestration Notes

## Global Assumptions

- Task is documentation/style-analysis only: inspect existing main dashboard and per-agent dashboard, then create a durable internal note and AGENTS.md reference for future Avatar dashboard styling.
- Browser inspection should use Playwright against a running local app.
- User requested a `gpt-5.4` medium subagent for the Playwright visual inspection.

## Active Agents

- Completed: Playwright UI style analyst (`019e52e6-2b4f-7040-abea-79ac831f415d`, `gpt-5.4` medium).

## Completed Work

- `.internal-dev` beginning pass read targeted focus files.
- Playwright inspection covered `/dashboard`, `/agents`, representative agent dashboard routes, and a brief `/avatar` contrast pass.
- Added style guidance note and AGENTS.md references.

## Validation Results

- Playwright screenshots saved under `target/playwright-avatar-style-guide/`.
- Analyst found existing dashboard style is an operational console with dense panels, compact controls, thin blue-gray borders, small radii, semantic status chips, and HTMX/fragment-oriented interactions.
 - 2026-05-22 Playwright inspection completed against `http://localhost:18080` for `/dashboard`, `/agents`, `/agents/avatar`, and a brief contrast pass on `/avatar`.
 - Screenshots saved under `target/playwright-avatar-style-guide/`: `dashboard-main-full.png`, `agents-index-full.png`, `agent-avatar-detail-full.png`, `agent-avatar-route-full.png`, `avatar-contrast-full.png`, plus `dashboard-main-snapshot.md`.

## Remediation Notes

- None yet.
 - Main dashboard pattern: framed shell with centered top banner, left orchestration sidebar, and dense operational panels; restrained blue-gray palette, 8-12px radii, thin borders, low shadow, status chips, and list/table-like scanning density.
 - Agent surfaces keep the same shell but switch to a split master-detail layout on `/agents` and a full-width detail dashboard on `/agents/{id}`; content is grouped into plain data panels, metric cards, tab-like button rows, and compact lifecycle/action controls.
 - Interaction style is HTMX/fragment driven: `/dashboard` loads `_stats`, `_active-work`, `_open-projects`, `_agents`, inbox/output/event side panels; `/agents` loads `_list`, `_detail/{id}`, and `_detail/{id}/dashboard` without heavy client-side chrome.
 - Avatar contrast: widget cards roughly match panel borders/radii, but the page is much more fragmented and inconsistent in control styling; several buttons still render browser defaults, density is lower, and the mixed personal-dashboard grid does not yet feel aligned with the operational shell used by `/dashboard` and `/agents`.

## Blockers

- None yet.

## Closeout Work

- Created `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`.
- Added references in root `AGENTS.md` and `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`.
- Created `.internal-dev/changelogs/2026-05-22-avatar-ui-style-guidelines.md`.

## Final Validation Status

- Documentation/style-guide pass complete; waiting for separate chat-session scope fix before final turn closeout.

## Handoff Notes

- The style guide should describe patterns, layout, density, interaction style, and what Avatar dashboard work should preserve.
