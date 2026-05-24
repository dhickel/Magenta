# Senior Engineer Guidance

Status: shared guidance for all workers

## Primary Principle

Do not salvage the old explorer UX. Reuse services, tests, and safe route logic when useful, but the visual and interaction model is a fresh details/list file explorer. If a worker finds itself adding cards, dropdown-first actions, or modal-only browsing, it is drifting from the spec.

## Architecture Guidance

Keep filesystem behavior in `io.mindspice.magenta2.ai.orchestration.workspaces`. Controllers should translate HTTP/form parameters into service calls and render fragments. UI components should receive already-confined, already-classified view data.

Path safety is not a UI affordance. Every backend operation must re-resolve and revalidate source and destination paths. Treat UI hidden inputs as untrusted.

Use current label/tag persistence if it meets the contract. Avoid a second tag model unless validation proves the existing `workspace_file_labels` schema cannot support custom file and directory tags.

## UI Guidance

The target is a familiar operational file manager:

- compact toolbar;
- breadcrumb/path controls;
- row table with headers;
- selected row state;
- separate right inspect panel;
- modal viewer only for file viewing or focused operation confirmation;
- no card view for file/directory entries.

Use SimplyPages primitives directly when they express the target cleanly. `Table` is acceptable because it supports component cells for tag chips and actions. Avoid string post-processing of rendered component HTML.

Use compact icon or icon-like button labels where available. If the existing component system lacks icons, use short text labels only where necessary and keep rows stable.

## HTMX Guidance

Each endpoint should have one obvious primary target. Use OOB swaps when a mutation must refresh table, inspector, and modal container together. Error responses should render a visible fragment into the same target area or modal, not just return raw JSON/transport errors.

Keep JavaScript narrow. Modal tab switching or local dirty state can be JS if it is the simplest path. File CRUD, selection, list refresh, inspect refresh, tag mutation, copy, move, rename, and delete should be HTMX-first.

## Viewer Guidance

Markdown rendering should be safe and resilient. Do not let a CommonMark/runtime failure break the modal. Render the raw Text tab and show the render failure below the rendered panel. Plain text is not Markdown and must not imply rendered Markdown is available.

Size and encoding limits are user safety features. Unknown/binary content should not be silently rewritten or shoved through a text area.

## Testing Guidance

Backend tests should be adversarial: root escapes, symlinks, Windows paths, encoded separators, stale paths, collisions, copy/move into descendant, deleting protected roots, binary files, oversized files, invalid UTF-8, missing created timestamps, and tag subtree behavior.

UI validation must inspect screenshots, not only click success. Reject outputs that look like cards, hide required columns, strand the inspect panel, make row actions wrap badly, or become unusable on mobile.

## Email And Closeout Guidance

Every phase gate requires an email report. Keep it brief but concrete: phase name, work completed, files changed, validation evidence, blockers/risks, next phase. Final report must include HTML and plain-text fallback, then low-token AgentMail listening. Always run `mailctl status` at email/wait gates.

## Failure Modes Seen Before

- Treating a reusable module as product truth even when it violates the UX.
- Passing route/controller tests while the browser UI is still visually wrong.
- Updating the table after a mutation but leaving the inspect panel stale.
- Copying files but failing to copy tags.
- Handling Markdown happy path only and crashing on render failure.
- Letting modal workflows own browsing rather than supporting a persistent explorer surface.
