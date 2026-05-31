# Date

2026-05-31

# Change Summary

Implemented GitHub issue #33 Phase 11 with a bounded SlotKey/RenderContext refactor for stable Home dashboard selector and dashboard panel shells, plus package-guide enforcement for future template reuse.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/HomeDashboardTemplates.java`: added reusable SimplyPages templates and `.of(...)` slot/helper factories.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`: routed stable selector and panel shell rendering through the new templates while preserving dynamic routes and swap roots.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`: added focused coverage for stable ids/classes/HTMX attributes and dynamic context rendering.
- `AGENTS.md`, `docs/AGENTS.md`, `src/main/java/io/mindspice/magenta2/AGENTS.md`, `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`, `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`: added SlotKey/RenderContext enforcement and Home dashboard terminology guidance.
- `.internal-dev/specifications/web.md`, `.internal-dev/specifications/simplypages.md`, `.internal-dev/specifications/decisions.md`: recorded the active SlotKey reuse boundary.
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`: updated reusable guidance and marked Avatar names as legacy implementation terminology.
- `docs/technical/avatar-dashboard-fragments.md`: documented the template boundary for dashboard fragments.

# Behavioral Impact

No intended route, URL, HTMX target, full-page fallback, dashboard editor density, or empty-row behavior change. The stable selector and panel shell now use reusable templates while dynamic link/id attributes remain per request.

# Specification Impact

Updated `web.md`, `simplypages.md`, and `decisions.md` to make SlotKey/RenderContext consideration an active frontend contract for stable Home dashboard/static structures.

# Risks

Browser proof is still required to confirm visual and interaction parity on Home dashboard normal/edit modes across desktop and mobile. Broad `Avatar*` code/package renaming remains deferred stale naming debt.

# Follow-up Items

- Delegate Playwright browser validation for Home dashboard normal/edit desktop/mobile and selector/detail/widget swaps.
- Leave GitHub issue #8 empty-row/density remediation open and out of this phase.
