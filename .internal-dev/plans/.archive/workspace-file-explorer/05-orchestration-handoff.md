# Orchestration Handoff

## Execution Model

Use a phase-gated orchestration with one mutating implementation lane active at a time inside Magenta unless the orchestrator can prove non-overlap. Safe non-mutating research/review lanes may run in parallel.

Required model assumptions:

- Implementation worker: `implementation_worker_agent`, model `gpt-5.3-codex`, reasoning effort `medium`.
- Validation/red-team worker: `validation_redteam_agent`, model `gpt-5.3-codex`, reasoning effort `medium`.
- Playwright validation must be delegated to validation worker, not run inline by the main implementation worker.

## Phase Gates

### Phase 0: Branch And Baseline

Mutating:

- Create Magenta branch `feature/workspace-file-explorer`.

Checks:

- `git status --short`.
- Confirm no repo-local email ledger is recreated.

Commit:

- No commit required unless baseline planning/progress artifacts are added.

### Phase 1: Workspace Domain Foundation

Mutating:

- WU-02 service core.
- WU-03 tags.
- WU-04 action logging.

Serial only:

- Schema/repository/service changes should be coordinated by one worker to avoid inconsistent migrations.

Checks:

- Workspace service/repository tests.

Commit:

- Commit after tests pass.

### Phase 2: Viewer/Editor And API Contracts

Mutating:

- WU-05 text/markdown/image/binary policy.
- WU-08 API routes/compatibility.

Checks:

- Service and controller tests.

Commit:

- Commit after tests pass.

### Phase 3: SimplyPages Upstream Module

Mutating:

- WU-09 in upstream repo.

Can run in parallel with Phase 2 only if:

- It uses a clean isolated upstream branch/clone.
- It works only from agreed view-model/config contract.
- It does not mutate Magenta.

Checks:

- Upstream unit/demo tests.
- Demo browser validation.

Commit/PR:

- Commit upstream and open draft PR.

### Phase 4: Magenta UI Integration

Mutating:

- WU-06 Avatar/Work Areas explorer UI.
- WU-07 first picker integration.

Dependencies:

- Phase 1 and Phase 2 complete.
- Phase 3 module available locally or integrated as dependency/source update.

Checks:

- Web/controller tests.
- Focused Magenta tests.

Commit:

- Commit after tests pass.

### Phase 5: Docs And Internal Closeout Prep

Mutating:

- WU-10 docs.
- `.internal-dev` changelog/knowledge/unfinished/focus/decisions updates per `07-closeout-plan.md`.

Checks:

- Docs source references.
- `git status --short`.

Commit:

- Commit closeout docs/internal-dev updates with final implementation phase or as final closeout commit.

### Phase 6: Independent Validation And Remediation

Mutating:

- Remediation only if validator finds failures.

Checks:

- WU-11 automated tests.
- WU-12 delegated Playwright.
- Spring context smoke.

Commit:

- Commit any remediation after rerunning failed validations.

## Safe Parallel Non-Mutating Groups

May run while Phase 1 worker codes if they do not edit files:

- Review current tests and write validation checklist.
- Inspect SimplyPages docs/demo/source and propose upstream API shape.
- Review docs that will need updates.
- Red-team path traversal cases as a test matrix.

Must not run in parallel as mutating lanes:

- `schema.sql` and repository migrations.
- `WorkAreaExplorerService` and new metadata/action services.
- `AvatarDashboardController` and `AvatarDashboardComponents`.
- shared CSS/JS.
- upstream `framework.css`.

## Implementation Worker Prompt Template

```text
You are implementation_worker_agent using model gpt-5.3-codex with reasoning effort medium.

Repository: /home/hickelpickle/Code/Java/magenta2.
You are implementing one assigned work unit from .internal-dev/plans/workspace-file-explorer/.
Read:
1. AGENTS.md
2. .internal-dev/AGENTS.md
3. .internal-dev/focus/AGENTS.md
4. .internal-dev/plans/workspace-file-explorer/00-specification-lock.md
5. The assigned work-unit section in 03-domain-work-units.md
6. Any package AGENTS.md for files you edit

Do not recreate a repo-local email ledger.
Keep controllers thin and services responsible.
Use SimplyPages/HTMX patterns for UI.
Stop if path confinement, encoding, tag-follow, action logging, or dirty-worktree overlap is uncertain.
Run the validation listed for your work unit and report exact commands/results.
Commit only when your phase gate is satisfied and the orchestrator instructs you to commit.
```

## Upstream Worker Prompt Template

```text
You are implementation_worker_agent using model gpt-5.3-codex with reasoning effort medium.

Repository: /home/hickelpickle/Code/Java/cannasite/java-html-framework.
Task: implement the reusable SimplyPages FileExplorer/FilePicker module from Magenta plan .internal-dev/plans/workspace-file-explorer/04-upstream-simplypages-pr-plan.md.

First inspect git status. The checkout may have unrelated dirty files. Do not overwrite them.
If dirty state overlaps or branch isolation is unsafe, stop and ask whether to use a clean temp clone/worktree/current checkout.
Read root AGENTS.md and package AGENTS.md files for components/modules/layout/demo before edits.
Do not add Magenta-specific workspace, DB, tag, audit, Avatar, or route behavior.
Implement module, tests, demo, docs, changelog, then open a draft PR.
```

## Validation Red-Team Prompt Template

```text
You are validation_redteam_agent using model gpt-5.3-codex with reasoning effort medium.

Repository: /home/hickelpickle/Code/Java/magenta2.
Read .internal-dev/plans/workspace-file-explorer/00-specification-lock.md and 06-validation-redteam-plan.md.
Validate the implementation against every acceptance criterion and stop rule.
Run relevant Java tests, bounded Spring startup, API/path/security tests, and delegated Playwright browser validation.
Playwright validation must capture screenshots and critique layout quality, density, hierarchy, overflow, mobile stacking, delete modals, editor tabs, picker flow, and JavaScript justification.
Report pass/fail by criterion with evidence. Do not remediate unless the orchestrator assigns remediation.
```

## Remediation Policy

- Validator findings are triaged by criterion.
- Security/path/encoding/destructive-action failures block completion.
- UI visual issues block completion if they impair operation, overlap/clip controls, or violate the repo UI validation policy.
- Non-blocking polish can be deferred only with user approval and `.internal-dev/focus/unfinished-work.md` update.

## GitHub Publishing

Magenta:

- Final branch should be pushed and opened as a PR if requested or if normal repo workflow expects it.
- PR body must link this plan suite and summarize validation.

SimplyPages:

- Upstream branch should become a draft PR to `dhickel/SimplyPages`.
- PR body must explain generic module boundary and Magenta integration use case.

## Final Reporting

Final implementation report must include:

- Branches and commits.
- Upstream PR URL.
- Magenta PR URL if created.
- Acceptance criteria pass/fail.
- Validation commands and key results.
- Playwright screenshot artifact paths.
- Known blockers/deferred items.
- `.internal-dev` artifacts created/updated.
