# Topic
Agent queue transcript linking and cancel lifecycle

# Source References
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

# Key Takeaways
- Assignment transcripts should resolve conversation IDs from multiple sources in stable order: checkpoint/output data first, durable assignment links second, nested evidence/input data, then legacy plan-run/session metadata fallback.
- Model-backed orchestration should persist `assignmentId -> conversationId` before entering the blocking chat execution call so diagnostics remain recoverable if checkpoint persistence or terminal output is missed.
- `CANCEL_REQUESTED` is an internal transition for running leased work. Non-running cancelable rows should go straight to terminal `CANCELLED`.
- Running cancellation must preserve lease owner and expiry so the runner can write the terminal `CANCELLED` result through `saveAssignmentIfLeaseOwner`.
- Queue diagnostics can update both diagnostics content and the live transcript with HTMX out-of-band swapping, avoiding a separate Watch action.

# Engine Relevance
This keeps operational queue behavior observable and cancellable without introducing a new orchestration abstraction. Future queue lifecycle changes should preserve the distinction between terminal cancellation and the short-lived running-work cancellation request.

# Open Questions
None.
