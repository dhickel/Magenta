# Date

2026-05-29

# Change Summary

Implemented Phase 04 dashboard agent operational widgets: Agent Status/Queue, Agent Outputs, and Agent Files/Notes. The widgets use explicit source chips, selector-backed settings, scoped output queries, service-confined Work Area previews, and registry tool descriptors that preserve current-context normal-agent tool boundaries.

Scoped repair: Agent Files/Notes settings now render Work Area selector options from the selected/bound agent's Work Areas and preserve an existing selected agent Work Area. Reserved Avatar Work Area selector behavior remains unchanged for legacy/non-Agent-Files/Notes surfaces.

# Files

- `src/main/java/io/mindspice/magenta2/avatar/dashboard/*` - added Phase 04 view records and widget registry definitions/settings/tool descriptors.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` - added runtime-service read models, scoped output preview, Work Area mini-view owner guards, and Agent Files/Notes settings selector scoping for selected-agent Work Areas.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java` - rendered Phase 04 widgets, binding selectors, source chips, bounded rows, and service-confined file preview modal.
- `src/main/resources/static/css/avatar-dashboard.css` - added compact agent operational row/health styling and bumped the dashboard CSS asset version.
- `src/test/java/.../AvatarDashboardControllerTest.java` and `AgentOperationalToolConfigurationTest.java` - added focused coverage for selected/no/missing agent states, output scoping, Work Area guard, Agent Files/Notes settings selector preservation, and widget tool descriptor registration.
- `.internal-dev/specifications/*`, `docs/end-user/avatar-dashboard.md`, `docs/technical/avatar-dashboard-fragments.md`, and `docs/api/00-index.md` - documented the Phase 04 service/API/web contracts.

# Behavioral Impact

Dashboard users can add agent operational widgets that bind to selected agents, projects, jobs, or Work Areas without making the dashboard itself agent-owned. Output lists are scoped by widget settings unless `dashboard` source mode is explicitly chosen. Work Area file previews are read through Work Area services and require the selected Work Area to belong to the selected agent when an agent is configured. Agent Files/Notes settings expose the selected agent's Work Areas instead of only the reserved Avatar agent's Work Areas.

# Specification Impact

Updated architecture, services, API, web, SimplyPages, and decisions specifications with the Phase 04 agent operational widget contract and security boundary.

# Risks

Browser proof and independent repair revalidation are still pending by directive. The Work Area mini-view intentionally shows a bounded preview/list rather than the full file explorer; full browsing remains on agent detail routes.

# Follow-up Items

- Delegate Phase 04 browser proof for selected/no-agent states, output modes, scoped preview rejection, Work Area mini-view, and desktop/mobile visual quality.
