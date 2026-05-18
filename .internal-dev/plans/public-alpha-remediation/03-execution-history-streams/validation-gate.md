# Execution, History, and Streams Validation Gate

## Validator Instructions

Read the execution-domain review files and bug reports 05, 06, 14, 15, and 21 before validating.

## Required Checks

- Public execution controls create assignments with expected priority.
- Direct-run public paths are removed, gated, or demonstrably internal-only.
- Saved plan execution preserves transcript.
- Plan SSE event names are semantic.
- Job Start Run creates `JOB_RUN` assignment or no longer exists publicly.
- Schedule/reaction invalid templates are rejected at save.
- Focused tests, full `mvn test`, bounded startup, and focused browser validation pass.
