---
document_type: ui-style-guidelines
status: active
source: playwright-inspection
created: 2026-05-22
---

# Avatar Dashboard UI Style Guidelines

Use this note before redesigning `/avatar` or adding dashboard-like Avatar surfaces. It captures the visual and interaction patterns observed in the existing `/dashboard` and per-agent dashboard flows.

## Inspection Basis

- Running app: `http://localhost:18080`
- Inspected routes: `/dashboard`, `/agents`, a representative agent detail/dashboard route, and a brief `/avatar` contrast pass.
- Playwright artifacts: `target/playwright-avatar-style-guide/`
  - `dashboard-main-full.png`
  - `agents-index-full.png`
  - `agent-avatar-detail-full.png`
  - `agent-avatar-route-full.png`
  - `avatar-contrast-full.png`
  - `dashboard-main-snapshot.md`

## Existing Dashboard Patterns

The main dashboard is an operational console, not a consumer landing page. It uses a centered shell with a framed left navigation block and a broad content lane. The main content is panel-first: status summaries, active work, agents, inbox, outputs, and event surfaces appear as dense operational regions rather than oversized cards.

The layout is moderate-to-high density. Headings are compact, spacing is tight but readable, and multiple useful panels remain visible in the first viewport. The page favors scanability over decorative whitespace.

Visual language is restrained:

- Near-white panels on a light blue-gray page background.
- Thin blue-gray borders.
- Low or absent shadow.
- Small radii, generally `8px` or less in local CSS and no large pill-shaped cards.
- Compact buttons and links.
- Semantic status chips for state, using muted fills and clear text.

Interaction behavior appears fragment-oriented. The dashboard shell loads focused fragments such as stats, active work, projects, agents, inbox, outputs, and events. Standard list refreshes, panel updates, and row actions should remain HTMX-first unless a narrow JavaScript helper is clearly simpler.

## Per-Agent Dashboard Patterns

The per-agent experience is a master-detail operational screen. `/agents` presents a compact list/filter column with agent rows, state chips, and row-scoped actions. Selecting an agent swaps detail content into the right side, while direct agent routes remain addressable.

The agent detail/dashboard keeps the same visual system:

- A tab-like control row for detail sections.
- Simple fact grids.
- Compact metric cards.
- Workspace health/status panels.
- Lifecycle and queue action strips.
- Dense lists and tables over large visual widgets.

Tabs read as sectional controls inside the operational shell, not as separate app-level navigation. The page should feel inspectable and repeatable: every panel has a clear operational purpose and a predictable action area.

## Avatar Redesign Guidelines

Redesign `/avatar` as part of the Magenta operational console. Do not make it a separate consumer-style dashboard.

Keep these patterns:

- Use the shared operational shell and navigation language where practical.
- Prefer a two-column or master-detail layout when content benefits from a stable context rail.
- Use white or near-white panels with thin blue-gray borders and low shadow.
- Keep panel radii at `8px` or less unless reusing an existing component that already sets the radius.
- Keep headings compact; reserve large type for actual page titles.
- Maintain moderate-to-high density so chat, tasks, calendar, notes, outputs, and system state can coexist in one scan.
- Use small metric blocks and semantic status chips for state.
- Use action bars, tab rows, and row actions that match `/dashboard` and `/agents`.
- Keep CRUD, filtering, section refresh, modal open/save, row actions, and widget updates HTMX-first.
- Use JavaScript only for narrow behavior where it is the path of least resistance, such as streaming chat or local-only convenience behavior.

Avoid these patterns:

- Loose widget collages that feel detached from the rest of Magenta.
- Oversized personal-product cards, hero sections, large decorative panels, or marketing-style composition.
- Browser-default buttons mixed with custom operational controls.
- One-off styling that redefines the dashboard palette or spacing.
- Low-density panels that hide operational state below the fold.
- Nested cards where a panel contains more decorative panels instead of structured rows, lists, or metrics.

## Avatar Content Treatment

Treat personal-assistant features as operational surfaces:

- Chat should be a first-class work panel with clear session/model/status treatment.
- Tasks and todos should use compact rows with title, state, due/context metadata, and row actions.
- Calendar should show near-term schedule density, not a large decorative calendar when space is constrained.
- Notes should be searchable/list-like, with metadata and short excerpts.
- Outputs and recent work should mirror the existing output/work panels.
- Agent/system overview should use status chips, compact counters, and short lists rather than large widgets.
- Alerts/inbox should use the same compact handling pattern as inbox and agent message surfaces.

## Consistency Checklist

Before signing off on Avatar dashboard styling:

- Compare screenshots against `/dashboard` and `/agents`.
- Confirm at least the first viewport has useful operational density.
- Confirm panel borders, radii, button sizing, chips, typography, and spacing match `magenta.css` patterns.
- Confirm standard interactions are HTMX fragments unless explicitly justified.
- Confirm JavaScript is limited and documented by behavior, not used as a general transport layer.
- Confirm text does not overflow compact panels or buttons at desktop and mobile widths.
- Confirm `/avatar` still feels like Magenta's command surface, not a separate app.
