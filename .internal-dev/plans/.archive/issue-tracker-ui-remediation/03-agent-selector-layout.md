# Agent selector layout
# Context
Issue tracker UI remediation lane for the operational/chat surfaces.

# Goal
Implement the assigned remediation cleanly while preserving `/chat` isolation and HTMX-first interaction patterns.

# In Scope
See the issue title for the concrete surface owned by this phase.

# Out of Scope
Unrelated workflow/job redesign, retroactive reclassification of historical mixed chat rows, and drag-and-drop step ordering.

# Implementation Steps
1. Re-read the relevant controllers/services/templates before editing.
2. Make the smallest complete change that closes the issue.
3. Add or update focused tests for the changed contract.
4. Record validation evidence in the shared handoff notes.

# Validation
Run focused automated tests for the touched subsystem plus the Playwright checks listed in `play_wright_tests.md`.

# Exit Criteria
The issue behavior is fixed, tests pass, and the final validation phase can consume the result without extra interpretation.
