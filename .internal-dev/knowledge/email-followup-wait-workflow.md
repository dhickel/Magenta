---
schema_version: 1
document_type: knowledge
date: 2026-05-22
owner: codex
status: active
---

# Email Follow-Up Wait Workflow

## Topic

Reusable closeout email reports and low-token reply waits should live in a global companion skill rather than as long repo-local instructions.

## Source References

- `~/.codex/skills/email-followup-wait/SKILL.md`
- `~/.codex/skills/email-followup-wait/scripts/wait-for-reply`
- `~/.codex/skills/agentmail/scripts/agentmail`
- `AGENTS.md`

## Key Takeaways

- Keep `agentmail` focused on transport primitives: send, fetch, reply, list messages, and list threads.
- Put closeout report shape, wait cadence, cancellation rules, and reply handling in `email-followup-wait`.
- Use script-driven polling for the waiting phase so sleeping and checking mail do not consume model tokens.
- Default wait policy is a 6-hour cap, 5-minute polling for the first hour, and 10-minute polling afterward.
- Process qualifying replies on the main thread before dispatching subagents.

## Engine Relevance

This pattern supports “email me when done and continue if I reply” without making every repo carry a long email report schema. It also gives Magenta a future durable-wait target if the workflow needs to survive process restarts.

## Open Questions

- Whether to later implement a persisted Magenta `WAITING_FOR_EMAIL_REPLY` state for durable overnight resume.
