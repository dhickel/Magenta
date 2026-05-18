# Date

2026-05-18

# Change Summary

Implemented bug-04 executable workflow validation. Empty workflow drafts can still be saved, but validation, public submit/run, HTMX submit, and service run paths now reject definitions that have no executable graph or no single reachable start path.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

- Empty workflows return clear operator-visible validation errors before validate, submit, or run.
- Disconnected workflow definitions with multiple start roots fail executable validation.
- Workflows with cyclic/no-start graphs report the missing start path alongside existing cycle validation.
- Draft persistence remains structural-only so incomplete drafts can be saved during authoring.

# Risks

- Existing workflows that relied on multiple independent start roots now need to be connected under one explicit start path before submission or run.

# Validation

Focused workflow/controller tests passed with 96 tests, static call graph scan confirmed draft persistence remains separate from executable validate/submit/run paths, `git diff --check` passed, and bounded startup reached `Started Magenta2Application` on port `40005`. Delegated browser validation remains the subplan gate before marking bug-04 passed.

Delegated validation passed on commit `4f2eb4f` with focused tests, full `mvn test`, `git diff --check`, bounded startup, and browser-origin checks for empty draft validate/submit/run rejection, disconnected workflow rejection, unchanged run lists, SSE `failed` events, and valid one-node graph validation.
