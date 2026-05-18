# Subplan 03: Fixture Parity

## Goal

Align test fixtures with production-critical behavior.

## Implementation Steps

1. Enable SQLite `foreign_keys=true` in repository/service fixtures where relevant.
2. Review schedule/reaction disabled test config and add targeted tests with production-like behavior.
3. Keep tests deterministic by controlling clocks/pollers rather than disabling behavior globally where the behavior is under test.

## Validation

Fixture behavior catches FK and schedule/reaction validation failures.
