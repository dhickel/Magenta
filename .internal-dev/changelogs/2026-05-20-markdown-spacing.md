## Date
2026-05-20

## Change Summary
Added scoped rendered-markdown spacing rules for chat message bodies, planning preview documents, and thinking bodies so list markers, quotes, and code blocks stay inside message containers with consistent indentation.

## Files
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Behavioral Impact
Rendered markdown lists, blockquotes, and preformatted blocks in chat-facing surfaces now respect the visual message margins instead of starting too far left or overflowing horizontally.

## Risks
Low. The rules are scoped to rendered chat/planning/thinking containers and CSS asset versions were bumped so browsers fetch the updated styles.

## Follow-up Items
None.
