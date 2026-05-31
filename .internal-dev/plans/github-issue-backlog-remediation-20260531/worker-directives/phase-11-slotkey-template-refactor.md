# Phase 11 Worker Directive: SlotKey Template Refactor And Package Guide Enforcement (#33)

## Objective

Remediate GitHub issue #33 with a bounded SlotKey/RenderContext refactor pass for stable Home dashboard/dashboard widget/static surfaces, plus explicit package-guide enforcement so future frontend work considers SlotKeys for stable structures and agent/detail swaps without full rerenders.

## User-Visible Outcome

Stable dashboard/static fragments retain current behavior and routes while using reusable SimplyPages templates where appropriate. Frontend package guides stop treating "Avatar UI" as a product abstraction and instruct agents to use Home dashboard/dashboard terminology and SlotKey/RenderContext patterns.

## Issues

- #33 `Refactor dashboard and static pages toward reusable SlotKey templates`

## Direct Targets

Audit before editing:

- Home dashboard/dashboard widget code:
  - `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
  - `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
  - `src/main/java/io/mindspice/magenta2/avatar/dashboard/DashboardWidgetRegistry.java`
- Static/operational pages:
  - `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
  - shared navigation/shell builders under `src/main/java/io/mindspice/magenta2/api/web/`
- Package-guide enforcement targets:
  - `AGENTS.md`
  - `docs/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md` (legacy data/package naming; update wording to Home dashboard where product-facing)
  - `src/main/java/io/mindspice/magenta2/ai/chat/rendering/AGENTS.md` if rendered web fragments/templates are touched
  - Review other `AGENTS.md` files with `rg --files -g 'AGENTS.md'`; update only those with frontend/SimplyPages UI guidance.
- SimplyPages docs/demos to read:
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/getting-started/02-dynamic-pages-with-slotkey-rendercontext.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/03-template-rendercontext-slotkey-reference.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/02-dynamic-fragment-caching-patterns.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/pages/HtmxEditingDemoPage.java`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
- Tests:
  - `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java` for static page fragments touched
  - Add focused template/render tests if useful.
- Docs/specs:
  - `.internal-dev/specifications/simplypages.md`
  - `.internal-dev/specifications/web.md`
  - `.internal-dev/specifications/decisions.md` if a durable SlotKey adoption boundary is locked
  - `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md` should be updated or supplemented to note stale "Avatar UI" terminology if relied on.
  - `docs/end-user/avatar-dashboard.md` or renamed/linked dashboard docs if touched
  - `.internal-dev/changelogs/2026-05-31-slotkey-template-refactor.md`

## Forbidden Scope

- Do not force SlotKeys into highly dynamic structures where rebuilding is clearer.
- Do not change user-facing routes, URL behavior, HTMX targets, or full-page fallbacks except for bug fixes approved by validator.
- Do not perform a broad `Avatar*` code rename unless it is very small, safe, and fully covered; otherwise log stale naming debt.
- Do not create a new frontend framework or raw HTML workaround.

## Supporting Docs To Read

- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md` with stale-terminology caveat
- SimplyPages SlotKey/RenderContext docs listed above

## Experience Contract

- Primary product surface is Home dashboard/dashboard widget editor, not an Avatar UI.
- Dashboard empty-row/density remediation from #8 is out of scope; leave #8 open.
- Stable DOM ids and HTMX targets remain intact: `#dashboard-home`, `#avatar-widget-grid` if still code-named, widget root ids, shared modal/edit container, and dashboard selector.
- Refactored fragments must visually match existing operational density.
- Desktop/mobile screenshots must show no duplicate shell chrome, stale selected state, or degraded widget/editor layout.

## Required Package-Guide Enforcement

Add or update guidance so future agents must:

- Consider `Template`, `SlotKey`, and per-request `RenderContext` for stable Home dashboard, dashboard widget, static page, status strip, selector/detail, and repeated fragment structures.
- Use slot-keyed reuse for stable structures where only labels, counts, hrefs, chips, statuses, or bounded child fragments change.
- Add concise `.of(...)` factory helpers for any new SlotKey/template key or key-bundle helper types; do not scatter direct `new` construction through controllers or render methods when a named helper can keep call sites readable.
- Avoid sharing mutable component instances across requests.
- Preserve HTMX-first interactions and stable swap roots.
- Use Home dashboard/dashboard editor wording for product surfaces; treat `AvatarDashboard*`, `AvatarService`, and `avatar.sqlite` as legacy implementation names until a deliberate rename is planned.
- Log broad stale naming cleanup as deferred/stale abstraction debt instead of expanding #33.

## Implementation Steps

1. Run `git status --short --branch` and preserve unrelated changes.
2. Audit candidate surfaces and classify them:
   - Must refactor now: small stable structures with obvious repeated values.
   - Leave as-is: highly dynamic structures or refactors too risky for one commit.
   - Deferred stale naming debt: broad `Avatar*` code/package rename.
3. Update frontend-related `AGENTS.md`/package guides first so enforcement is first-class.
4. Extract one or more reusable template classes/helpers for stable Home dashboard/static fragments using `SlotKey` and `RenderContext`.
5. Keep controllers thin; move template construction to component/helper classes, and expose `.of(...)` factories on new key/helper value types instead of requiring repeated constructor calls.
6. Preserve current HTMX routes/targets and full-page fallbacks.
7. Add tests proving rendered ids/classes/routes remain stable and dynamic values render through context.
8. Update specs/docs/knowledge/changelog with current terminology and any deferred naming debt.

## Senior-Engineer Guidance

- `Template.of(componentTree)` is the preferred wrapper; build a new `RenderContext` per request unless intentionally confined.
- Good candidates: status strips, selector items, static cards with dynamic counts, repeated widget headers, master/detail rows.
- Bad candidates: structures whose child layout varies heavily per request or where SlotKeys obscure simple code.
- This phase can be staged. Closing #33 requires at least package-guide enforcement plus a meaningful bounded refactor of stable structures; log remaining candidates.

## Acceptance Criteria

- Frontend-related package guides explicitly enforce SlotKey/RenderContext consideration for stable dashboard/static structures.
- At least one coherent stable Home dashboard/dashboard widget/static surface is refactored to SimplyPages `Template`/`SlotKey`/`RenderContext` without behavior regression.
- New key/helper value types introduced by the refactor provide `.of(...)` factory helpers and keep direct constructor usage out of repeated frontend call sites.
- Stale "Avatar UI" product wording is corrected in touched docs/specs/guides; remaining legacy code names are documented as naming debt if not renamed.
- No dashboard editor density or empty-row behavior changes are introduced for #8.
- Tests cover stable root ids, HTMX attributes, dynamic context values, and full-page/fragment behavior.
- Browser proof confirms visual/interaction parity.

## Negative Checks

- No broad product rewrite.
- No route/HTMX target breakage.
- No mutable shared component instances across requests.
- No new "Avatar UI" product abstraction in docs/guides.

## Validation Commands

- `mvn -q -Dtest=AvatarDashboardControllerTest,FrontendControllerTest,OrchestrationControllerTest test` adjusted to touched surfaces.
- Template-specific focused tests if added.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Separate Playwright/browser validation required.

## Browser Checklist

- Home dashboard normal mode desktop/mobile.
- Home dashboard edit mode desktop/mobile.
- Any refactored static page/fragment desktop, plus mobile if layout-relevant.
- HTMX dashboard selector/detail/widget swap checks.
- Console/network review.
- Visual critique for density, spacing, wrapping, overflow, selected state, modal layering, and duplicate shell chrome.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-11-validation-report.md`
- Browser artifacts under `artifacts/github-issue-backlog-remediation-20260531/phase-11-browser/`

## Closeout Expectations

Main thread closes #33 after validation, commit, push, and email.

## Stop Conditions

- Stop if the only meaningful refactor requires broad `Avatar*` code/package renaming.
- Stop if SimplyPages lacks required SlotKey behavior and a library issue/PR decision is needed.

## Do Not Close Unless

- Package-guide enforcement is committed.
- A real bounded SlotKey/RenderContext refactor is committed or a validator-approved closeout explains why current code already satisfies the issue and logs remaining debt.
- Browser proof verifies Home dashboard/static surfaces, not an obsolete Avatar UI concept.
