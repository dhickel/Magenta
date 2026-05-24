# Upstream SimplyPages PR Plan

## Objective

Add a reusable, generic FileExplorer/FilePicker module to SimplyPages and publish it as a clean upstream PR. The module must provide rendering and interaction shell primitives only. Magenta-specific filesystem, DB, Work Area, tag, audit, and Avatar behavior stays in Magenta.

## Current Upstream State

Repository:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework`

Verified guidance:

- Root `AGENTS.md` defines SimplyPages as Java-first SSR, minimal JavaScript, pragmatic HTMX, with docs/tests/changelog requirements.
- Component package owns generic low-level UI components.
- Module package owns high-level composed modules.
- Demo package owns illustrative Spring Boot examples.

Verified useful existing primitives:

- Components: `Div`, `Header`, `Paragraph`, `Markdown`, forms, display `Card`, `DataTable`, `Table`, `Alert`, `Badge`, `Tag`, `InfoBox`, media `Image`, navigation `Breadcrumb`/`Link`.
- Layout: `Row`, `Column`, `Grid`.
- Patterns: HTMX endpoint contracts, modal container updates, OOB updates, SlotKey/Template reuse.

Dirty-state blocker:

- The upstream checkout has unrelated modified/untracked files. Do not overwrite them. Use a temporary clean clone or an approved branch/worktree strategy before implementation.

## Reusable Module Boundary

In scope for SimplyPages:

- File explorer shell rendering.
- File picker shell rendering.
- Breadcrumb/path rendering.
- Toolbar rendering.
- Card and compact list entry rendering.
- Entry action button rendering from configured action descriptors.
- Inspector slot and viewer slot.
- Generic tags/chips display supplied by app view model.
- Confirmation modal rendering patterns.
- HTMX endpoint URL configuration.
- Minimal CSS in `framework.css`.
- Optional narrow JS for local view toggle/history/dirty-state hooks if kept generic.
- Demo with fake in-memory file tree provider.

Out of scope for SimplyPages:

- Filesystem access.
- Path traversal/security decisions.
- SQLite schema.
- Magenta Work Area semantics.
- Tag persistence.
- Audit/action logging.
- Avatar-specific classes/styles/routes.
- App authorization/CSRF.

## Proposed API Shape

Records/classes:

- `FileExplorerModule`
- `FilePickerModule`
- `FileExplorerConfig`
- `FilePickerConfig`
- `FileExplorerView`
- `FileExplorerEntry`
- `FileExplorerBreadcrumb`
- `FileExplorerAction`
- `FileExplorerInspector`
- `FileExplorerMode`
- `FilePickerMode`
- `FileExplorerSelection`

Configuration fields:

- `rootLabel`
- `currentPath`
- `breadcrumbs`
- `entries`
- `selectedEntry`
- `tags`
- `toolbarActions`
- `entryActions`
- `viewMode`
- `listEndpoint`
- `navigateEndpointTemplate`
- `viewerEndpointTemplate`
- `inspectorEndpointTemplate`
- `actionEndpointTemplate`
- `modalContainerId`
- `listTargetId`
- `inspectorTargetId`
- `viewerTargetId`
- `pickerCallbackTarget`
- `allowCreateFolder`
- `allowCreateText`
- `allowCreateMarkdown`
- `allowRename`
- `allowDelete`
- `allowCopyMove`
- `allowTags`

Rendering contracts:

- Stable root id.
- Stable list target id.
- Stable inspector target id.
- Stable viewer target id.
- Modal container id supplied by app.
- Actions emit `hx-*` attributes based on config.
- Text is escaped through normal SimplyPages render paths.

## Demo Plan

Add a demo route such as:

- `/demos/file-explorer`
- `/demos/file-picker`

Demo behavior:

- In-memory tree with directories, text, markdown, image placeholder, binary placeholder.
- HTMX navigation fragments.
- Confirmation modal examples.
- Picker modes with selected-path result display.
- Demonstrates inspector slot with fake tags.

Demo must not become the actual framework logic provider. It should exercise public APIs.

## Test Plan

Framework tests:

- Module renders toolbar, breadcrumb, cards/list rows, inspector slot, viewer slot.
- HTMX attributes point to configured endpoints and stable targets.
- Picker modes render correct controls.
- Confirmation modal markup supports file single-step and directory two-step configuration.
- Text/tag/path values are escaped.
- Module lifecycle idempotency.

Demo tests:

- Route loads.
- Navigation fragment returns expected target.
- Picker selection updates target.
- Delete modal routes render expected step.

Browser validation:

- Run demo Spring Boot app.
- Capture desktop/mobile screenshots.
- Verify nonblank explorer, sensible layout, no overlap, usable controls.

## Documentation Plan

Update:

- `docs/reference/components-and-modules-catalog.md`
- Add `docs/reference/file-explorer-module-reference.md` or similar.
- Add/update `docs/patterns/03-htmx-endpoint-and-swap-patterns.md` if new OOB/multi-target patterns are introduced.
- Update `docs/INDEX.md` and `docs/README.md`.
- Add changelog under upstream `.internal-dev/changelogs/` during upstream closeout.

Docs must show:

- Data-provider responsibility belongs to the consumer app.
- Security is app responsibility.
- How to configure routes/targets.
- How to use picker modes.
- How to provide inspector/viewer slots.

## PR Workflow

1. Verify upstream dirty state.
2. If dirty state overlaps or branch is unsafe, stop and ask user whether to use temp clean clone/worktree/current checkout.
3. Create branch, suggested `feature/reusable-file-explorer-module`.
4. Implement module, tests, demo, docs, changelog.
5. Run `./mvnw -pl simplypages test`.
6. Run relevant demo tests.
7. Run demo browser validation.
8. Commit upstream changes.
9. Push branch and open draft PR to `dhickel/SimplyPages`.
10. Link upstream PR in Magenta closeout and Magenta PR body.

## Failure Modes

- Module couples to Magenta route names: reject.
- Framework CSS becomes too Magenta-styled: revise to neutral SimplyPages defaults.
- JS grows into a client app: reduce to generic local behavior or remove.
- Demo logic leaks into framework classes: split back out.
- Dirty upstream changes would be overwritten: stop.

## Senior Engineer Notes

The reusable module should be a shell and view-model renderer, not a filesystem framework. That is the clean architectural line: SimplyPages helps apps render and coordinate HTMX fragments; Magenta decides what a safe file path means and what actions are allowed. This makes the PR defensible upstream and keeps Magenta's security model testable in Magenta.

