---
date: 2026-05-22
type: feature
scope: avatar-dashboard
owner: phase-05-avatar-dashboard-ui
---

# Avatar Dashboard UI

## Summary

- Added the `/avatar` personal dashboard shell with Avatar-specific CSS, a compact SSE chat client, and stable widget roots.
- Added HTMX fragments for widget refresh, layout editing, organizer CRUD, output previews, and internal Avatar event alert dismissal.
- Kept `/dashboard` operational and distinct; `/avatar` links back to operations instead of replacing the dashboard.
- Added focused controller coverage for shell rendering, stable widget roots, layout validation/persistence, organizer mutations, output previews, and page-scoped chat assets.

## Notes

- Alerts use existing inbox messages and internal Avatar events only. This phase does not depend on public email alert ingestion.
- Playwright validation is still pending and should be run by the coordinator-owned validation agent for desktop/mobile screenshots and interaction checks.
