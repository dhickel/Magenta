# Orchestration Plan

## Context

This is a multi-phase alpha-readiness effort. The work must be run on a dedicated branch, committed at the end of each completed phase, and coordinated through subagents with their own subplans.

The two product outcomes are related but not tightly coupled:

- Documentation can be researched and authored by domain after the docs structure is created.
- Entity selectors must be sequenced: backend lookup contract first, reusable component second, integrations third.

## Goal

Deliver a coordinated implementation plan that lets a follow-on agent orchestrate documentation and selector implementation without inventing architecture or skipping validation.

## In Scope

- Create a dedicated implementation branch before phase work starts.
- Use `gpt-5.5` `medium` for implementation/documentation subagents by default.
- Preserve repo testing policy: test-running subagents use `gpt-5.3-codex` `medium`.
- Commit after each phase.
- Keep `.internal-dev` progress, changelog, knowledge, bugs, and archive hygiene.
- Keep implementation HTMX-first and SimplyPages-native.

## Out of Scope

- Public website/marketing docs.
- External hosted documentation deployment.
- OpenAPI generation unless the implementing agent proves it is a small additive helper.
- Replacing SimplyPages or converting operational UI into a client-rendered app.
- Deep full-app Playwright campaigns without explicit user approval.

## Implementation Steps

1. Create branch.
   - Command: `git switch -c alpha-docs-and-entity-selectors`.
   - If the branch already exists, switch to it only after checking uncommitted work.
   - Do not overwrite unrelated dirty files.

2. Establish phase ledger.
   - Add or update a progress tracker in this plan directory during implementation.
   - Track each phase as `planned`, `in_progress`, `blocked`, or `complete`.
   - Track assigned subagents, touched files, validation commands, and commit hashes.

3. Run Phase 1 documentation foundation first.
   - This creates the docs tree and the rules future phases must follow.
   - It also updates root `AGENTS.md`; coordinate carefully if that file has unrelated user edits.

4. Run documentation content phases in parallel where safe.
   - Split by audience/domain: technical architecture/API, end-user workflows, operations/security.
   - Each documentation subagent must inspect code before writing claims.
   - Documentation must not copy stale README assumptions.

5. Run selector implementation serially.
   - Backend lookup service and endpoints must land before component integration.
   - Reusable component must land before page replacements.
   - Low-risk field replacements should land before dependent-flow replacements.

6. Validate after each phase.
   - Run focused tests for touched code.
   - For UI selector phases, start the app and delegate focused Playwright validation to a subagent.
   - Run bounded startup after backend or wiring changes.

7. Close out.
   - Run full `mvn test`.
   - Run bounded Spring startup smoke.
   - Run focused Playwright validation on changed UI flows.
   - Write `.internal-dev/changelogs/<date>-alpha-docs-and-entity-selectors.md`.
   - Write `.internal-dev/knowledge/<topic>.md` for reusable selector and docs-governance lessons.
   - Log bugs discovered outside this scope under `.internal-dev/bugs/`.
   - Move this plan directory to `.internal-dev/plans/.archive/` only after implementation is complete.

## Validation

- Every phase has a commit hash and validation notes.
- No subagent changes are merged without coordinator review.
- Documentation claims are backed by current code paths, controllers, services, schema, or package guides.
- UI selector flows are validated in a live browser through a Playwright subagent.
- Any blocker that prevents real execution validation is reported to the user and not treated as complete.

## Exit Criteria

- All child plans are complete or explicitly blocked with user-approved deferral.
- Final branch contains implementation, docs, tests, and `.internal-dev` closeout artifacts.
- Final commit includes implementation and `.internal-dev` updates.

