---
schema_version: 1
document_type: internal-dev-inbox-guide
last_reviewed: 2026-05-23
owner: unassigned
status: active
---

# Internal Dev Inbox

`.internal-dev/inbox/` is the repo-local intake area for AgentMail messages that affect active Magenta engineering work.

## Purpose

- Preserve inbound email instructions in the repository context so work can survive compaction and handoff.
- Separate unread email intake from acted-on email instructions.
- Give the main agent a simple place to check when Dwight says to check email.
- Avoid losing instructions when a long-running wait is interrupted.

## Files

- `queue.md`: newly received or not-yet-dispatched email instructions.
- `read.md`: message IDs and thread IDs that have already been read, acknowledged, summarized, and either dispatched or closed.

## Intake Rules

1. A low-token email intake agent or wait process watches AgentMail using the current email-followup cadence.
2. When a new inbound message from Dwight arrives, record it in `queue.md` with:
   - received timestamp
   - thread ID
   - message ID
   - sender
   - subject
   - extracted instruction summary
   - acknowledgment status
3. Acknowledge receipt by email before acting on the instruction.
4. Check the thread for additional inbound messages before dispatching work.
5. After the main agent interprets and dispatches the instruction, move the message ID to `read.md` with outcome notes.

## Constraints

- Do not store credentials, API keys, or AgentMail credential file contents here.
- Keep email bodies summarized unless the full text is needed as a durable engineering artifact.
- If a message contains private or sensitive non-project content, summarize only the actionable engineering instruction.
- This inbox is a coordination artifact, not a product feature or public user-facing mailbox.
