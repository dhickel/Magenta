---
schema_version: 1
document_type: changelog
date: 2026-05-22
owner: codex
status: complete
---

# Email Follow-Up Wait Skill

## Change Summary

Added a global companion skill for sending reusable work-summary emails and waiting for email replies with low-token AgentMail polling. The Magenta `AGENTS.md` email section now points to that skill instead of duplicating the full report schema.

## Files

- `AGENTS.md`
- `.internal-dev/knowledge/email-followup-wait-workflow.md`
- `.internal-dev/focus/decisions.md`
- Global skill: `~/.codex/skills/email-followup-wait/`
- Global AgentMail helper: `~/.codex/skills/agentmail/scripts/agentmail`

## Behavioral Impact

- Agents can now use `email-followup-wait` for “send the closeout report and wait for reply” and “wait for email instructions” workflows.
- The wait loop polls AgentMail without repeated model calls, using a 6-hour cap, 5-minute checks for the first hour, and 10-minute checks afterward.
- Qualifying standalone waits only accept new mail from `dwight.hickel@gmail.com` after the wait starts.
- Chat messages should cancel an active email wait; email replies should return to the main thread for interpretation before subagent dispatch.

## Validation

- Generated `agents/openai.yaml` for the new skill.
- Validated the wait script against fixture payloads for matching and non-matching messages.
- Ran skill validation with `quick_validate.py`.
- Checked AgentMail helper status/message fetch commands against the live inbox without exposing credentials.

## Risks

- The wait skill is reliable only while the Codex session/process remains active. Durable overnight resume across restarts remains a future Magenta persisted-wait feature.

## Follow-up Items

- None required for the skill workflow.
