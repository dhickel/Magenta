# Avatar Shell Baseline Refactor Shared Notes

## Global Assumptions

- Dashboard is the only Avatar tab with user-editable layout.
- Active tab persists through URL state, and desktop chat-rail width persists through browser-local state.
- Queue/History/Profile/Outputs/Work Areas must reuse existing services and not introduce a new runtime data model.
- Manual refresh is removed in this pass; interval refresh is deferred.

## Active Agents

- None yet.

## Completed Work

- Planning suite created.

## Validation Results

- None yet.

## Remediation Notes

- None yet.

## Blockers

- None yet.

## Closeout Work

- Record deferred auto-refresh work in `.internal-dev/focus/unfinished-work.md`.
- Update end-user and technical Avatar docs.

## Final Validation Status

- Not started.

## Handoff Notes

- Keep `AvatarDashboardController.java`, `AvatarDashboardComponents.java`, `avatar-dashboard.css`, and shell JS single-writer at any given time.
