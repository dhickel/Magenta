# Closeout Report Plan

## Purpose

Define the closeout artifacts and evidence required after the Agent Skills implementation phases complete. This file is not an implementation directive by itself; Phase 06 owns execution.

## Required Closeout Artifacts

- `.internal-dev/changelogs/<date>-agent-skills-system.md`
- Updated `.internal-dev/specifications/architecture.md`
- Updated `.internal-dev/specifications/services.md`
- Updated `.internal-dev/specifications/api.md`
- Updated `.internal-dev/specifications/web.md`
- Updated `.internal-dev/specifications/simplypages.md`
- Updated `.internal-dev/specifications/decisions.md`
- Updated `.internal-dev/specifications/deferred-features.md`
- Updated `.internal-dev/knowledge/agent-skills-specification-reference.md`
- Additional `.internal-dev/knowledge/<domain>.md` if implementation reveals reusable lessons.
- Updated `docs/end-user/00-index.md`
- `docs/end-user/agent-skills.md`
- Updated `docs/technical/00-index.md`
- `docs/technical/agent-skills.md`
- Updated affected package `AGENTS.md` files.
- Active bug reports under `.internal-dev/bugs/` only for discovered defects that remain out of scope, mirrored to GitHub if required.

## Changelog Required Headings

Use `.internal-dev/AGENTS.md` changelog template:

- `Date`
- `Change Summary`
- `Files`
- `Behavioral Impact`
- `Specification Impact`
- `Risks`
- `Follow-up Items`

The changelog must include:

- root `skills/` repository behavior;
- DB-backed metadata and assignments;
- parser/loader behavior;
- runtime catalog/activation behavior;
- UI browse/create/edit/assign behavior;
- official spec validation status;
- deferred project-local/layered assignment scopes.

## Validation Evidence To Record

- Focused parser/discovery/metadata tests.
- Focused assignment/catalog/activation/chat tests.
- Focused API/file-management tests.
- Focused UI/controller tests.
- Full `mvn test`.
- Bounded Spring startup.
- Playwright desktop/mobile screenshots and visual critique summary.
- Final `gpt-5.5` xhigh official spec-adherence review result.
- Any blocked validation with explicit user approval, if applicable.

## Final User Report Shape

The main thread final report should include:

- what was implemented;
- validation commands/results;
- browser proof summary;
- official spec-adherence result and any documented Magenta divergences;
- deferred scopes left for future work;
- commit/branch information if committed.

Do not send an email report unless the user separately asks for one. If they do, use the global `email-followup-wait` skill and keep secrets/private unrelated workspace details out of the report.
