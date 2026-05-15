# Phase 07: Failure Modes And No-Fallback Contract

## Context

Docker-first architecture is only real if the system fails loudly when Docker cannot satisfy the contract.

## Goal

Validate that Docker absence, image absence, stopped containers, mount problems, and permission/lease conflicts do not silently degrade into host execution or misleading success states.

## In Scope

- Docker disabled/unreachable startup behavior.
- Missing image behavior.
- Stopped or deleted managed container at execution time.
- Stop/restart failure truthfulness.
- Broken mount or unwritable output scenario where safely reproducible.
- Workspace lease conflict behavior.

## Out of Scope

- Destructive chaos testing outside an isolated validation environment.

## Implementation Steps

1. Start an isolated run with Docker disabled or daemon unavailable and verify user-facing behavior.
2. Attempt agent work and confirm it fails with actionable Docker-specific messaging instead of running on the host.
3. Simulate a missing image or unavailable container scenario and verify the same no-fallback behavior.
4. Stop or remove a managed container out of band, then trigger execution from the browser and verify recovery or explicit failure.
5. Re-test the lifecycle UI against a stop failure and confirm displayed state matches real state.
6. Trigger a workspace lease conflict and verify execution does not proceed as if it had writable access.
7. Where safe, create an output-write failure and verify the run is not reported as cleanly successful.

## Validation

Required checks:
- No Docker-unavailable scenario results in successful host execution.
- All failure states are visible to an operator.
- Error messages identify the blocking runtime condition.
- UI status remains truthful under failure, not merely optimistic.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/07-failure-mode-evidence.md` exists.
- Any silent fallback or false-success behavior is logged as a blocker.
