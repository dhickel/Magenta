# Date

2026-05-18

# Change Summary

Removed the deprecated `io.mindspice.magenta2.ai.chat.workflow` package after confirming active code, tests, schema, and runtime references use the canonical `io.mindspice.magenta2.ai.orchestration.workflow` implementation.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowBindingKind.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowInputBinding.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRun.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRunStatus.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowStep.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowStepRun.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowStepRunStatus.java`
- `.internal-dev/knowledge/workflow-route-model.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`

# Behavioral Impact

No active workflow behavior changes are intended. Public workflow routes, orchestration runtime workflow assignments, workflow tests, and schema ownership remain on the canonical orchestration workflow package.

# Risks

External or ad hoc code importing the retired chat workflow package will no longer compile. In-repo scans found no active references outside historical `.internal-dev` documents and the removed package itself.

# Follow-up Items

- Continue Domain 08 with subplan 02; do not reintroduce workflow behavior through the retired chat workflow namespace.
