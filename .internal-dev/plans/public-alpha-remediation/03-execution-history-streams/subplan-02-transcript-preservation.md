# Subplan 02: Transcript Preservation

## Goal

Execute saved plans without deleting existing conversation transcript rows.

## Implementation Steps

1. Locate chat/plan execution memory-clearing call sites.
2. Replace clearing with a separate execution context/run memory model or append-only execution markers.
3. Ensure UI no longer claims context is being cleared.
4. Add test with existing transcript, saved-plan execution, and transcript readback.

## Validation

Pre-existing chat messages remain after execution starts/completes.
