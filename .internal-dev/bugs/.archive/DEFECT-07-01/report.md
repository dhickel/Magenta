# DEFECT-07-01: No Output Content Viewing Mechanism — Outputs Page Is Metadata-Only

## Summary
The `/outputs` page lists output artifacts with metadata (name, type, path, agent, plan, run) but provides no mechanism to view, read, or download the actual content. No `_read`, `_view`, `_content`, `_file`, or similar endpoints exist. Clicking output rows does nothing. The outputs page is a dead end — it shows that outputs exist but cannot show what they contain.

## Scope
- `/outputs` HTML page: table with metadata, no links or action buttons
- `/outputs/_list` fragment: same metadata-only table
- `/api/outputs` REST endpoint: returns JSON metadata, `contentJson` field is null
- No endpoint discovered for: `_read`, `_view`, `_content`, `_file`, `_raw`, `_detail` on any output path
- Content only accessible via direct filesystem access

## Reproduction
1. Navigate to `/outputs` or call `GET /outputs/_list`
2. Observe output row with metadata but no clickable element or view action
3. Try any plausible endpoint pattern: all return 404

## Expected
Users should be able to view, read, or download output content through the UI or API. Text outputs should be readable inline, file outputs should be downloadable, JSON outputs should be viewable.

## Actual
Outputs table is metadata-only. Content is inaccessible through the application.

## Evidence
- Phase 07 evidence file: `.internal-dev/reviews/docker-backed-alpha-e2e-validation/07-outputs-workspaces-artifact-contract-evidence.md`
- All 8 attempted read/view/content endpoint patterns returned 404
- `contentJson` field is `null` for the hello_file output
- Content only accessible via `cat` on the filesystem path

## Impact
**Alpha blocker for output UX.** Users cannot access the results of their plan/workflow execution through the UI. The output system delivers metadata without delivering the outputs themselves.

## Status
Fixed — Phase 5 validation confirmed (2026-05-13)

## Resolution
Output content viewing and downloading implemented:
- `GET /outputs/_content/{artifactId}` — renders content fragment with metadata, inline text content, and download link
- `GET /api/outputs/{artifactId}/download` — serves raw file content with appropriate Content-Type

## Evidence
Tested with artifact IDs `f6b2e8d8` (field_2) and `18b999b4` (field_1) from the plan execution run:
- `GET /outputs/_content/f6b2e8d8...` returned HTML fragment with metadata (type: text, file: field_2.txt, created: 2m ago), download link, and `<pre>` content body
- `GET /api/outputs/f6b2e8d8.../download` returned raw content with HTTP 200, Content-Type: text/plain
- Both artifacts viewable and downloadable
- Playwright MCP browser validation confirmed the outputs page loads without console errors

## Next Action
None — fix verified. Archive when all Phase 5 blockers are resolved.
