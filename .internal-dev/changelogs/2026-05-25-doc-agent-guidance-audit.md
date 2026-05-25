# Documentation and Agent Guidance Audit

## Date

2026-05-25

## Change Summary

Audited documentation and agent guidance for stale `.internal-dev` workflow references. Updated package and docs guidance to point at the active flat specifications, knowledge, changelog, bug, plan, and review stores, clarified that AgentMail coordination uses the global `mailctl` daemon/wait workflow rather than a repo-local inbox ledger, and aligned web/security guidance with the current open-alpha posture.

## Files

- `docs/AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/workspace-file-architecture-rules.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/core/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/knowledge/security-access-control-domain-validation.md`
- `.internal-dev/knowledge/htmx-fragment-error-statuses.md`

## Behavioral Impact

No product behavior changed. Future agents get current guidance for documentation routing, Avatar UI prerequisites, core package closeout, and AgentMail coordination.

## Specification Impact

Specification Impact: none. The existing `workflow.md`, `web.md`, `simplypages.md`, and `decisions.md` contracts already captured the intended behavior; this audit aligned stale guidance and source-reference lists to those contracts.

## Risks

Low. Changes are documentation-only and limited to active guidance/source-reference files.

## Follow-up Items

- Historical changelogs and archived docs still contain older references by design and were left unchanged.
