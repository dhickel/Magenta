# Topic
Blocker validation escalation policy for alpha-critical dependencies

# Source References
- `AGENTS.md`

# Key Takeaways
- Blocking dependencies (for example live Docker/Podman daemon validation) must not be silently deferred or bypassed.
- If a blocker prevents real execution validation, the agent must stop and consult the user immediately.
- Alternative or reduced validation paths may be recorded for context, but cannot be treated as completion for blocker-class acceptance.
- Any deferred blocker requires explicit user approval and must remain labeled as blocked.

# Engine Relevance
- This policy reduces false-complete status during release-critical work.
- It clarifies when orchestration agents must escalate instead of continuing with partial validation.

# Open Questions
- Should blocker severity levels be codified (for example `release-blocker`, `alpha-blocker`, `non-blocking`) to standardize escalation behavior further?
