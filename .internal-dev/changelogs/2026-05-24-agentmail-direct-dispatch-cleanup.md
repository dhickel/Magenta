# Date

2026-05-24

# Change Summary

Removed the repo-local AgentMail instruction ledger workflow and made direct AgentMail daemon/wait dispatch the active engineering email workflow. Inbound email instructions should be handled from `mailctl status`, `mailctl next`, and `mailctl wait` by the main thread, then delegated directly when useful.

# Files

- `.internal-dev/focus/decisions.md`
- `.internal-dev/knowledge/email-followup-wait-workflow.md`
- `.internal-dev/changelogs/2026-05-23-avatar-ui-polish.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/`
- `.internal-dev/plans/workspace-file-explorer/`
- Removed the old repo-local email ledger files

# Behavioral Impact

Engineering email coordination no longer records inbound AgentMail instructions into repository-local queue/read files. The durable runtime state for email reachability is the AgentMail remote-mode daemon state under the global AgentMail skill, and the active agent dispatches work directly after receiving mail.

# Risks

Historical artifacts may still mention the old workflow in archived context, but active decision records and file-explorer orchestration plans now point at direct daemon/wait dispatch.

# Follow-up Items

None.
