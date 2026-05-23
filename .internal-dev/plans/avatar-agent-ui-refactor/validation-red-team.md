# Avatar Agent UI Refactor Validation And Red-Team Gates

## Automated Tests

### Avatar Layout

- Migrates old `avatar_dashboard_layout` into row/widget layout.
- Adds row with correct position normalization.
- Moves rows up/down and refuses invalid bounds.
- Adds widget from catalog and rejects duplicate first-party widget key.
- Moves widgets left/right/up/down only where capacity exists.
- Resizes widgets to `3|4|6|8|12` and rejects row overflow.
- Removes/toggles widgets and returns normalized layout.
- Controller fragments include stable ids and OOB targets.

### Planner And Recurrence

- Creates, updates, completes, and archives planner tasks.
- Adds/reorders/completes subtodos.
- Links notes and existing work/project/output ids.
- Validates daily/weekly/monthly recurrence fields.
- Projects near-term calendar occurrences.
- Rejects invalid cron in advanced mode with user-visible error fragment.
- Confirms planner tasks are not persisted as Magenta executable task definitions.

### Work Areas

- Creates Home Work Area under `<root>/home`.
- Marks existing confined directories as Work Areas.
- Rejects duplicate marks.
- Rejects path traversal.
- Rejects symlink escapes.
- Refuses unmark/delete of Home/system Work Areas.
- Detects queued/running assignments using a Work Area.
- Detects active output targets.
- Creates persistent job default Work Area under `<root>/ongoing_jobs/<slug>`.

### Assignment And Output Routing

- Persists selected Work Area and output route fields in `work_assignments`.
- Defaults null Work Area fields to Home for new runtime resolution.
- Keeps old assignments with null fields readable.
- Resolves `workspace/` to selected Work Area and `root/` to owned root.
- Routes default outputs to selected Work Area `outputs/`.
- Routes Work Area redirects to target Work Area `outputs/`.
- Routes direct redirects to existing confined directories.
- Rejects missing, non-directory, escaped, or protected direct output directories.

### Explorer

- Lists directories with stable HTMX fragment ids.
- Previews safe text files and rejects binaries/large files.
- Downloads confined files.
- Saves safe text edits.
- Creates directories.
- Renames files/directories without escape.
- Recursively deletes only after typed confirmation.
- Refuses protected delete targets:
  - owned root;
  - current Home;
  - system Work Areas;
  - active Work Areas in queued/running assignments;
  - active output targets;
  - symlink escapes.

## Browser Validation

Use a `gpt-5.3-codex` medium Playwright validation subagent. Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation if live chat/SSE or concurrent interaction is touched.

Required screenshots:

- `/dashboard` desktop baseline.
- `/agents` desktop baseline.
- `/avatar` view mode desktop.
- `/avatar` edit mode desktop.
- `/avatar` widget catalog modal.
- `/avatar` planner modal.
- `/avatar` Work Area explorer modal.
- Submit form Work Area/output picker desktop.
- `/avatar` mobile.
- Submit picker mobile.

Visual rejection criteria:

- Low-density consumer-card layout.
- Oversized hero-like Avatar sections.
- Purple/blue-gradient or one-note decorative palette.
- Nested decorative cards.
- Browser-default controls mixed into operational panels.
- Text overflow inside buttons, chips, rows, cards, or modals.
- Incoherent overlap at desktop or mobile widths.
- Standard CRUD implemented with broad JavaScript transport instead of HTMX.

Interaction checks:

- Open/close edit mode.
- Add row.
- Add widget from catalog.
- Move and resize widget.
- Save occurs per action with visible OOB refresh.
- Open planner tabs and perform one CRUD action.
- Browse Work Area, preview file, create directory, rename, and trigger guarded delete refusal.
- Open submit picker and select default/redirect routes.

## Runtime Validation

Set up an isolated data root and queued assignment with:

- agent-owned root;
- Home Work Area;
- second Work Area;
- direct existing directory under owned root.

Prove:

1. Selected Home Work Area appears as `workspace/`.
2. Owned root appears as `root/`.
3. Default output writes to `<selected-work-area>/outputs/...`.
4. Work Area redirect writes to `<target-work-area>/outputs/...`.
5. Direct redirect writes to the selected existing directory.
6. Output artifacts are persisted and downloadable.
7. Assignment audit/transcript shows Work Area and output route metadata.

Repeat with project-owned root if project assignment support is touched.

## Security Red Team

- `../` traversal in explorer path.
- URL-encoded traversal.
- Symlink inside owned root pointing outside `dataRoot`.
- Symlink inside Work Area pointing to another owner's root.
- Direct output redirect through symlink.
- Recursive delete of root.
- Recursive delete of active Work Area.
- Recursive delete of active output route.
- Safe edit of binary file.
- Safe edit over max byte limit.
- Stale/missing Work Area id in assignment submit.
- Disabled/inactive Work Area id in assignment submit.
- Cross-owner Work Area id in assignment submit.
- CSRF/auth behavior on unsafe routes.
- HTMX error fragments for bad requests.
- Browser reload after modal save.

## Startup And Full Checks

Required before final sign-off:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If full `mvn test` is too broad during a phase, the coordinator may choose focused tests for phase commits, but final integration must run the full applicable suite unless a blocker is explicitly approved by the user and recorded.

## Acceptance

The refactor is accepted only when:

- automated tests pass;
- Spring context starts;
- browser screenshots meet `/dashboard` and `/agents` visual density/consistency;
- runtime Work Area/output routing proof passes;
- security red-team checks pass or approved blockers are recorded;
- docs and `.internal-dev` closeout are complete;
- commits are pushed to the branch.
