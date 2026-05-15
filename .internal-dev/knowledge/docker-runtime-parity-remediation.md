# Topic
Docker runtime parity remediation patterns

# Source References
- `.internal-dev/reviews/docker-runtime-parity-validation/08-final-report.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

# Key Takeaways
- After lifecycle mutations, the UI should be fed from a fresh runtime inspection, not from the requested target state alone.
- Several “missing UI” findings were already-backed capabilities; the smallest clean fix was to expose them through HTMX fragments instead of creating a second transport stack.
- For routine operational CRUD and controls, HTMX fragments fit the existing SimplyPages architecture better than additional page-local JavaScript.

# Engine Relevance
These patterns keep operator surfaces truthful and keep the browser layer aligned with the backend contract without inventing duplicate state machines.

# Open Questions
- Whether lease acquisition/release should become a first-class operator flow or remain internal orchestration machinery.
