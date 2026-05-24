# Closeout Plan

## Magenta Closeout

Required after implementation, not during this planning-only request:

1. Update docs:
   - `docs/api/00-index.md`
   - `docs/technical/api-reference.md` if detailed routes changed
   - `docs/technical/workspaces-tools-outputs.md`
   - `docs/end-user/avatar-dashboard.md`
   - `docs/end-user/projects-and-workspaces.md` if picker/output routing behavior changes
   - `docs/technical/frontend-htmx.md` if new JS island or reusable SimplyPages module usage needs documenting
2. Update package guides if responsibilities materially change:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md` only if Avatar boundary changes
3. Add `.internal-dev/changelogs/<date>-workspace-file-explorer.md`.
4. Add `.internal-dev/knowledge/` note for reusable lessons:
   - workspace file explorer path/tag/action-log policy,
   - SimplyPages explorer module integration,
   - Playwright validation gotchas if new.
5. Update `.internal-dev/focus/unfinished-work.md` for approved deferred items:
   - external filesystem metadata reconciliation,
   - chunked large-file editing,
   - richer Avatar file-action timeline,
   - broader picker rollout.
6. Check `.internal-dev/focus/current-focus.md`; report stale Avatar focus if this work becomes the new durable direction. Do not silently rewrite project direction.
7. Update `.internal-dev/focus/architecture-focus.md` and `.internal-dev/focus/decisions.md` if architecture decisions become durable:
   - DB-backed workspace file labels,
   - file action log,
   - reusable SimplyPages file module boundary.
8. Log out-of-scope bugs in `.internal-dev/bugs/` immediately when discovered.
9. Mirror new `.internal-dev/bugs/` reports to GitHub Issues if this repo has GitHub remote access.
10. Check related closed GitHub Issues before leaving local bug reports active.
11. Move finalized plan artifacts to `.internal-dev/plans/workspace-file-explorer/.archive/` only after implementation and validation are fully complete, or leave active if follow-up phases remain.

## SimplyPages Closeout

Required in upstream repo after upstream module implementation:

1. Add upstream changelog under upstream `.internal-dev/changelogs/`.
2. Add reusable knowledge/notes only per upstream repo guidance.
3. Update SimplyPages docs and indexes.
4. Update package `AGENTS.md` only if package responsibilities or conventions changed.
5. Run upstream tests and demo validation.
6. Commit upstream changes.
7. Push branch and open draft PR.
8. Include PR URL in Magenta closeout.

## Commit Workflow

Magenta:

- Use dedicated branch.
- Commit each completed phase.
- Final commit includes implementation plus docs and `.internal-dev` closeout updates.
- Do not stage unrelated dirty files.
- Use path-limited staging if unrelated files remain dirty.

SimplyPages:

- Use isolated branch/clone/worktree.
- Commit only upstream module, docs, demo, tests, and closeout artifacts.
- Do not stage unrelated dirty files found at baseline.

## GitHub PR Expectations

Magenta PR:

- Summary of explorer behavior.
- Link to this plan suite.
- Link to SimplyPages PR.
- Schema changes.
- API changes.
- Validation summary with commands and Playwright screenshots.
- Known deferred items.

SimplyPages PR:

- Summary of generic FileExplorer/FilePicker module.
- Demo route.
- Docs/tests.
- Explicit note that app owns filesystem security and persistence.
- Link to Magenta integration PR if available.

## Final Report To User

Final report after implementation should be concise but include:

- What changed.
- Branch/commit/PR links.
- Upstream PR link.
- Acceptance criteria status.
- Validation highlights.
- Hard blockers or deferred items.
- Any stale focus state needing user decision.

## Planning-Only Closeout For This Request

This request is complete when:

- The plan suite exists under `.internal-dev/plans/workspace-file-explorer/`.
- Final response lists artifact paths.
- Final response identifies hard blockers/open gates.
- No product code/tests/docs outside planning scope were edited.

