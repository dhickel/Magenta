# Planning Question Card Visual Spacing

## Date

2026-05-22

## Change Summary

Updated planning question cards so both `/chat` and saved-plan chat render a yellow question marker and keep clear spacing between the question prompt and the composer input.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- `test-results/question-card-chat-validation.png`
- `test-results/question-card-plan-validation.png`
- `test-results/question-card-chat-revalidation.png`
- `test-results/question-card-plan-revalidation.png`

## Behavioral Impact

Planning questions are visually clearer via a yellow question marker. The `/chat` planning question panel now has more bottom spacing before the message input, and question text/count/marker layout uses grid cells to avoid overlap on desktop and mobile.

## Risks

The saved-plan validation used a DOM fixture for the question-card layout rather than driving a full saved-plan chat flow. This was scoped to CSS/layout validation because the shared server renderer emits the same card markup.

## Follow-up Items

- None.
