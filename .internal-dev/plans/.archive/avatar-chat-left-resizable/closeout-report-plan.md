# Closeout Report Plan

## Email Subject

`Magenta Avatar left chat rail implementation complete`

## HTML Report Shape

- Short summary of implemented behavior.
- Table of changed files and low-level changes.
- Validation evidence:
  - focused Maven test command and result;
  - bounded startup command and result;
  - Playwright explicitly skipped unless user later approves it.
- Residual risks:
  - visual browser proof pending if Playwright remains skipped;
  - any startup/test blockers;
  - any accepted mobile ordering tradeoff.
- Senior engineer recommendations:
  - run desktop/mobile screenshot pass later;
  - test wide desktop drag behavior;
  - keep future chat behavior changes separate from shell geometry.

## Plain-Text Fallback

Include the same sections in terse plain text with file paths and commands.

## Wait Workflow

Use AgentMail/email-followup-wait after the report is sent. Check `mailctl status` before waiting. Do not drop email listening while a wait-for-response contract is active.
