# Date
2026-05-13

# Change Summary
- Updated root `AGENTS.md` to enforce strict handling of alpha-blocking dependencies during validation.
- Added explicit rules to prevent deferring or working around critical blockers without user consultation.

# Files
- `AGENTS.md`

# Behavioral Impact
- Agents must now stop and consult the user when critical infrastructure blockers prevent real validation.
- Unit-only or substitute validation can no longer be treated as completion for blocker-class requirements.
- Deferred blocker states require explicit user approval and must remain marked as blocked.

# Risks
- This stricter policy may pause throughput more often when environment prerequisites are missing.

# Follow-up Items
- Apply the same blocker escalation language in package-local guides where runtime validation is safety-critical.
