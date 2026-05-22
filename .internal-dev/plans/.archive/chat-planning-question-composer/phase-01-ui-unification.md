# Chat Planning Question Composer UI Unification

## Context
The `/chat` anonymous planning UX currently renders pending questions in a separate planning panel with its own answer textarea. Saved plan chat under `/plans` renders its prompt directly above the chat composer textarea. The project prefers reusable SimplyPages components/modules and consistent UX across similar chat surfaces.

## Goal
Move `/chat` planning question answering into the main chat composer while preserving the question count and styling the prompt as a compact card above the input. Share prompt rendering between `/chat` and saved plan chat where practical.

## In Scope
- Reuse a shared Java component/helper for planning question prompt presentation.
- Update `/chat` client behavior so pending planning questions submit through the main composer instead of a separate question textarea.
- Keep approval, continue, execute, execute-clean, and cancel planning controls working.
- Update relevant docs and internal development records.
- Run focused unit/browser/startup validation.

## Out of Scope
- Changing backend planning state machines.
- Redesigning full chat or plan editor layouts.
- Replacing existing SSE or HTMX transport architecture.

## Implementation Steps
- Inspect SimplyPages docs or demos for reusable component style before editing.
- Add or update a shared prompt-card renderer in the web layer.
- Use the shared prompt-card renderer in saved plan chat and `/chat` session chat markup.
- Update `chat-client.js` to synchronize pending question text/count into the composer prompt card and route main composer submission to the planning answer flow.
- Adjust CSS for the card-like question prompt and responsive layout.
- Update docs that describe frontend/chat planning behavior.

## Validation
- Run focused Java tests covering web rendering or controller behavior where applicable.
- Run relevant frontend/browser validation with Playwright through a subagent against a running app.
- Smoke test Spring Boot application context startup.

## Exit Criteria
- `/chat` pending planning questions display as a card above the main composer with `Question m/n`.
- Main composer submissions answer planning questions without sending a normal chat turn.
- Saved plan chat uses the shared prompt UI.
- Existing chat, saved plan chat, and planning actions remain functional.
- Required docs, `.internal-dev` records, and commit are complete.
