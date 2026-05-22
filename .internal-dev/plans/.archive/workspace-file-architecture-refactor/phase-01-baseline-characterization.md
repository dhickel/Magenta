# Phase 01: Baseline Characterization

## Context

The review lanes identified workspace/output divergences and two concrete workflow defects. Before broad source changes, implementation needs focused tests and a known validation baseline.

## Goal

Establish regression coverage for the refactor's highest-risk behavior while preserving current compatibility expectations.

## In Scope

- Targeted baseline test runs.
- Focused tests for project output placement, effective workspace resolution, workflow `WAITING` assignment behavior, async context propagation, durable workflow output separation, active/waiting temp retention, gated loose discovery, and chat-file separation.
- Minimal fixture helpers if needed.

## Out of Scope

- Production source refactors.
- Schema changes.
- API behavior changes.
- Removing loose artifact discovery.

## Implementation Steps

1. Read `agent-notes.md`, `review-synthesis.md`, and `implementation-plan.md`.
2. Run the targeted baseline tests named in `implementation-plan.md`.
3. Add focused regression tests in the closest existing test classes.
4. Keep assertions deterministic and avoid broad fixture rewrites.
5. If a test exposes a current bug and cannot pass without source changes, either include the source fix in the relevant later phase or record the test as planned coverage rather than committing a broken suite.
6. Append notes with test locations and baseline result.

## Validation

- Run the targeted baseline command set.
- Run any new or changed tests.
- Run Spring context smoke if no dependency blocks it.

## Exit Criteria

- Baseline behavior and known gaps are recorded.
- Added tests are either passing with current compatibility behavior or explicitly assigned to the implementation phase that will make them pass.
- No production behavior changed in this phase.
- Phase notes are appended and the phase is committed after validation.
