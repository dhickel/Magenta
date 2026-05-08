# Summary

Orchestration job items have no retry or continue-on-failure policy, so a transient item failure fails the entire job assignment.

# Scope

Confirmed in `OrchestrationRunnerService.runJob()` and `OrchestrationJobItem`.

# Reproduction

Run a job with multiple items where any non-wait item throws once during `runJobItem()`.

# Expected

Job item behavior should be explicit: retry a configured number of times, optionally continue on failure, or fail the job with recorded item evidence.

# Actual

Each item is attempted once. Any exception bubbles to `runAssignment()` and fails the assignment.

# Evidence

`runJob()` directly invokes `runJobItem()` inside the item loop with no retry wrapper. `OrchestrationJobItem` has no retry count or continue-on-failure fields.

# Impact

Longer jobs are brittle and can lose useful partial progress on transient errors. Operators have no per-item policy to distinguish must-pass steps from best-effort steps.

# Status

Fixed in this pass.

# Next Action

Archived after adding persisted per-item retry and continue-on-failure fields with retry/failure evidence handling.
