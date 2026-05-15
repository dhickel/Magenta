# Topic
Operational UI label and tab contract alignment for alpha orchestration surfaces

# Source References
- `.internal-dev/plans/alpha-operational-ui-polish-and-contract-completion/00-orchestration-plan.md`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/agents.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Key Takeaways
- Replace user-facing `Worktype` copy with `Manager Type` while preserving internal `workTypeProfile` persistence fields to avoid risky schema churn.
- If a UI surface is HTMX-first, minimal JS for active tab affordance is acceptable and should avoid API transport logic.
- Route-level wording updates (for example, approval wording to generic messages) require synchronized controller HTML assertions in tests; otherwise stale test expectations will fail despite intended behavior.
- Agent-specific chat actions should route through agent detail HTMX tabs rather than generic `/chat?agent=...` links when operational surfaces already provide agent-stream endpoints.

# Engine Relevance
- Applies to all future orchestration UI refinements where frontend contract language changes without backend schema renaming.
- Reinforces the local pattern: HTMX handles CRUD and content swaps; JS only handles small UI state affordances.

# Open Questions
- Should system chat settings become first-class runtime fields (`systemChatModel`, `systemChatPrompt`, `systemChatApprovedTools`, `systemChatContextLimit`, `systemChatEnabled`) in this same branch or a dedicated follow-up?
