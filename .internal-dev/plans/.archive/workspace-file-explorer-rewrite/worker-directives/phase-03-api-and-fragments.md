# Phase 03 Worker Directive: API And Fragments

## Objective

Expose stable API and HTMX fragment contracts for the details/list explorer, inspect panel, tags, operation modals, and viewer modal entry points. Keep controllers thin and do not complete the final visual rewrite in this phase.

## Required Supporting Docs To Read

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/frontend-htmx.md`
- SimplyPages HTMX endpoint patterns: `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`

## Exact Editable Files/Modules/Routes

May edit:

- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- New small records/helpers under `src/main/java/io/mindspice/magenta2/api/web/`
- `src/test/java/io/mindspice/magenta2/api/web/WorkAreaControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`

Routes to cover/refine:

- `GET /avatar/_work-areas/{workAreaId}/explorer`
- `GET /avatar/_work-areas/{workAreaId}/explorer/list`
- `GET /avatar/_work-areas/{workAreaId}/inspect`
- `GET /avatar/_work-areas/{workAreaId}/viewer`
- `GET /avatar/_work-areas/{workAreaId}/viewer/text`
- `PUT /avatar/_work-areas/{workAreaId}/text`
- `GET /avatar/_work-areas/{workAreaId}/modal/{action}`
- `POST /avatar/_work-areas/{workAreaId}/files/rename`
- `POST /avatar/_work-areas/{workAreaId}/files/delete`
- `POST /avatar/_work-areas/{workAreaId}/files/action/{copy|move}`
- tag list/create/add/remove routes as needed

## Forbidden Scope

- No broad UI/CSS redesign; leave visual completion to Phase 04/05.
- No domain service redesign except narrow call adaptation needed after Phase 02.
- No SimplyPages upstream mutation.
- No repo-local email ledger ; use direct AgentMail daemon/wait state only.
- No JavaScript transport layer.

## Experience Contract

This phase does not own final styling, but its fragments must make the target experience possible without later route invention.

- Full explorer fragment can render or refresh a bounded details/list shell.
- List fragment can update the table without losing current path/selection.
- Inspect fragment can update the separate right panel for a selected file or directory.
- Modal fragments can host viewer, rename, delete, copy, move, and tag flows.
- Mutation responses can clear the modal and refresh table plus inspect panel together through stable targets/OOB swaps.
- Error fragments are visible in the same operational area the user is interacting with.
- Fragment IDs must be stable enough for Playwright validation and future worker handoff.

Failure examples:

- A route returns only JSON where the HTMX surface needs a user-visible fragment.
- A mutation updates the table but leaves stale inspect details.
- Viewer and operation routes require the later UI worker to invent new endpoint semantics.
- Error handling depends on browser console or raw transport failure.

## Implementation Sequence

1. Read current controller routes and tests.
2. Define request/response/form records for inspect, tags, operations, and viewer routes.
3. Ensure JSON API routes remain compatible where possible.
4. Add/adjust Avatar HTMX routes with stable target IDs:
   - full explorer shell;
   - table/list region;
   - inspect panel;
   - modal container.
5. Ensure mutation routes return OOB fragments sufficient to refresh table, inspect, and modal container consistently.
6. Ensure errors return visible fragments or clear API status payloads.
7. Keep filesystem and tag logic delegated to domain services.
8. Add controller tests for route success, validation errors, unsupported viewer behavior, tag routes, operation forms, and OOB target IDs.
9. Append evidence to `shared/implementation-notes.md`.

## Acceptance Criteria

- Controllers remain thin.
- Fragment routes exist for table/list, inspect, viewer, and operations.
- Mutation routes can refresh all affected UI targets.
- Error behavior is visible and test-covered.
- Existing API compatibility is preserved or intentional route changes are documented in notes.
- Tests cover required status/fragment contracts.

## Negative Checks

- Fail if a controller directly resolves filesystem paths.
- Fail if mutation returns only the table while inspect/modal state can become stale.
- Fail if errors are raw stack traces or invisible transport failures.
- Fail if tag creation/assignment is not reachable from API/fragment contract.
- Fail if unsupported/binary files can be routed into text save.

## Validation Commands

```bash
mvn test -Dtest=WorkAreaControllerTest,AvatarDashboardControllerTest,WorkAreaExplorerServiceTest
git status --short
```

## Stop Conditions

- Fragment contract conflicts with target design.
- Required route changes imply broad API compatibility break.
- Backend service gaps from Phase 02 block clean controller implementation.

## Senior Engineer Notes

This phase is about contracts, not polish. The key quality bar is that the later UI worker can build a real table and inspect panel without inventing route semantics. Stable IDs and OOB refreshes matter more than pretty markup here.

## Do Not Close Unless

- [ ] Fragment route contract is test-covered.
- [ ] Mutation responses refresh table/inspect/modal consistently.
- [ ] Error fragments/statuses are test-covered.
- [ ] Controllers delegate filesystem/tag behavior.
- [ ] `shared/implementation-notes.md` has phase evidence.
- [ ] Validation red-team has passed before Phase 04 starts.
