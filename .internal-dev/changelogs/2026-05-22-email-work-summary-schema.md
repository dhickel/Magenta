# Email Work Summary Schema

## Date
2026-05-22

## Change Summary
Root `AGENTS.md` now defines the default schema and workflow for user-requested email work summaries after long-running orchestration plans, remediation loops, validation campaigns, and multi-phase implementation work. Reports must be sent as renderable HTML with a plain-text fallback and should include high-level summary, important takeaways, watched changes, validation results, bugs/issues, notes, summarized changelogs, and full changelog appendices.

## Files
- `AGENTS.md`

## Behavioral Impact
- Future email closeouts should not use raw Markdown as the primary email body.
- Email summaries should start with one to two high-level paragraphs and optionally an important takeaways section when the user should see risks, decisions, or follow-ups first.
- Reports should include target/update lists, validation evidence, issues/bugs found, suggestions, notes, and concise per-changelog summaries.
- Full relevant `.internal-dev/changelogs/` entries should be appended at the bottom of the email so the report stands alone.

## Risks
- Large appendices can make long email reports lengthy; future agents should still keep the executive summary concise and use headings/tables so the email remains scannable.

## Follow-up Items
- Consider extracting a reusable HTML email template if multiple future reports use the same structure.
