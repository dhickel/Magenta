# Chat Planning Question Composer

## Date
2026-05-22

## Change Summary
Anonymous `/chat` planning questions now appear as a compact prompt card above the main chat composer instead of using a separate answer textarea. The main composer submits active planning answers to the existing plan-answer endpoint, and saved plan chat reuses the same prompt-card visual component while preserving its HTMX form flow.

## Files
- `src/main/java/io/mindspice/magenta2/api/web/ChatModuleRenderer.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `docs/technical/chat-planning-tasks.md`
- `docs/technical/frontend-htmx.md`

## Behavioral Impact
- `/chat` pending planning questions are answered through `#chat-input`.
- Slash-prefixed text is treated as answer text while a planning question is active.
- `/chat` question prompt cards show the existing question progress label such as `Question 1/3`.
- Saved plan chat keeps `#plan-chat-form` and HTMX submission while sharing prompt-card styling.
- Chat and orchestration asset query versions were bumped so browsers load the changed JS/CSS.

## Risks
- Stale planning-question metadata could misroute a normal chat turn as a planning answer; the implementation clears active-question data whenever the panel leaves question state.
- Full live planning transitions depend on configured model credentials; browser validation covered routing and rendered UI but encountered local invalid-key responses for live model-backed transitions.

## Follow-up Items
- Consider extracting the prompt-card styling into a single shared stylesheet if more pages adopt it.
