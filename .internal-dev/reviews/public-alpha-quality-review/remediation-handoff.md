# Public Alpha Remediation Handoff

## Scope

Implementation handoff for the public-alpha blockers found by the `public-alpha-quality-review` campaign.

## Findings

The campaign filed 25 bugs. Treat bugs 01-17 and 19 as alpha blockers. Bugs 18 and 20-25 are remediation items that should be addressed before final alpha signoff unless explicitly deferred.

## Risk Assessment

The highest-risk clusters are coupled:

- Security fixes must cover auth/CSRF, path ids, shell/file/web tool confinement, and workflow XSS together.
- Execution semantics must remove direct run paths without breaking saved-definition submit-to-agent workflows.
- Schema fixes must be validated against both clean and warm DBs before any runtime lease/workspace work.
- Workflow editor fixes must preserve HTMX-first CRUD while keeping a narrow JS graph interaction where needed.

## Recommendations

### Phase 1: Security and Tool Confinement

- Add alpha auth/CSRF protections for public mutation/control routes.
- Validate agent ids and all filesystem path ids as plain path segments.
- Replace wildcard shell defaults and constrain shell/file tools to active assignment workspace and linked project scopes.
- Fix `web_fetch` redirect validation.
- Fix workflow graph XSS.

Validation:

- Add Spring web/security tests for mutation rejection.
- Add path traversal tests for agent ids.
- Add tool confinement tests for absolute paths, unrelated workspaces, redirect-to-private, and workflow XSS payloads.

### Phase 2: Execution Contract and History Preservation

- Remove or gate public direct-run routes.
- Replace chat `Execute now` with submit-to-agent semantics.
- Remove job `Start Run` or make it submit a `JOB_RUN` assignment.
- Preserve chat transcripts during plan execution.
- Fix plan-run SSE event names.

Validation:

- Route-level tests that public run buttons/API paths create assignments, not direct runs.
- Transcript preservation test.
- SSE contract test for plan-run stream names.

### Phase 3: Schema and Workspace Runtime

- Make `schema.sql` canonical for current tables.
- Fix `workspace_roots`/`workspace_leases` migration so leases are never dropped on normal startup.
- Materialize project workspace links or correct the runtime contract.
- Fix output symlink materialization and output attribution.
- Decide and document inbox table ownership.

Validation:

- Clean DB startup.
- Warm DB startup from a pre-migration fixture.
- DB probes for lease preservation, output attribution columns, and inbox table behavior.
- Browser/API proof that project-linked work can access the promised workspace path.

### Phase 4: Workflow Builder and UI

- Allow draft graph editing through incomplete intermediate states.
- Validate nonempty executable graph only at validate/submit.
- Keep graph drag/SVG as a narrow JS island; keep CRUD and validation HTMX/server-owned where practical.
- Fix agent lifecycle stale target.
- Fix mobile shell layout.
- Improve HTMX error statuses.

Validation:

- Browser test for building an approval workflow incrementally.
- Browser test that empty workflow cannot submit/run.
- Mobile viewport Playwright check.
- HTMX lifecycle swap test.

### Phase 5: Test Harness

- Add Spring web/application-context smoke tests for public REST/SSE controllers.
- Add reusable Playwright config/specs for the public page matrix.
- Enforce SQLite `foreign_keys=true` in repository/service fixtures where relevant.

## Follow-ups

After implementation, run:

- Focused tests for changed areas.
- Full `mvn test`.
- Clean and warm isolated SQLite startup.
- Playwright public-page and critical workflow regression suite.
