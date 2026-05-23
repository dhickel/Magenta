# Chat Session Scope Fix Notes

## Global Assumptions

- `/chat` session history should list only sessions that actually belong to the browser `/chat` surface.
- Agent side-panel chat, Avatar compact chat, orchestration/system chat, planning chat, and other internal/system conversations should not appear in the `/chat` session view.
- The worker may modify narrowly scoped chat session listing/filtering code and focused tests.

## Active Agents

- Pending: chat session scope fix worker.

## Completed Work

- Started after the Avatar UI style-guide analysis began.
- Identified the leak as a shared `/api/chat` persistence path with no explicit UI-surface marker.
- Added a `surface` marker to browser and Avatar chat requests, persisted it in session metadata, and filtered the `/chat` session list to browser-surface conversations in normal mode.

## Validation Results

- Focused Maven tests passed: `ChatServiceTest` and `ChatControllerTest`.
- Live HTTP smoke on the running app returned `200` for `/chat`.
- Playwright browser validation was blocked because the shared browser instance was already in use in `~/.cache/ms-playwright/mcp-chrome-4e05678`.

## Remediation Notes

- Planning conversations are also filtered out by mode so the browser session list does not expose anonymous plan drafts.
- Historical untagged sessions may still be ambiguous until they are reopened and rewritten with an explicit surface marker.

## Blockers

- None for the session-scope fix.
- Follow-up robustness issue filed for lowercase `surface` JSON values: https://github.com/dhickel/Magenta/issues/7

## Closeout Work

- Recorded implementation and tests.
- Added `.internal-dev` changelog and knowledge entry.
- Added `.internal-dev` bug report and mirrored it to GitHub issue #7 for case-insensitive `surface` parsing.

## Final Validation Status

- Passed focused Maven validation and Playwright/API validation. The follow-up enum-case robustness issue is tracked separately and intentionally not fixed in this patch.

## Handoff Notes

- Keep the fix focused; do not change unrelated chat behavior.

## Validation Results (2026-05-22 fresh server rerun)

- Fresh server target validated after restart at approximately `2026-05-22 23:48:33` local.
- Playwright captured `/chat` and `/avatar` screenshots under `target/playwright-chat-session-scope/`.
- `/api/chat/sessions` on fresh load returned HTTP `200` with keys `conversationIds` and `sessions`.
- Runtime seed check via HTTP:
  - `POST /api/chat` with `surface=BROWSER` (new conversation UUID) returned `200`.
  - `POST /api/chat` with `surface=AVATAR` (new conversation UUID) returned `200`.
  - Follow-up `GET /api/chat/sessions` returned only the `BROWSER` conversation in both `conversationIds` and `sessions`; the `AVATAR` conversation did not appear.
- `/avatar` DOM verification passed: page includes `data-chat-surface="avatar"`.
