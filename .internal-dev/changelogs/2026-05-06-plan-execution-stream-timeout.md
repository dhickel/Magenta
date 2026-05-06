## Date

2026-05-06

## Change Summary

Saved-plan execution streams now have a configurable 360 second timeout and route stream timeout, stream error, and client-send failure paths through execution failure finalization. The browser planning panel now executes approved plans through the streaming endpoint instead of the removed `/exec-plan` slash-command path, and plan cancellation has a direct UI action endpoint.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/application.yml`
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`

## Behavioral Impact

- `/api/chat/{conversationId}/plan/execute/stream` times out after `magenta.plan.execution-stream-timeout-seconds`, defaulting to 360 seconds.
- Execution stream timeout, stream error, and client disconnect/send failure settle saved plans into `NORMAL` / `NEEDS_REVIEW` through `recordExecutionFailure`.
- OpenAI-compatible chat requests use `magenta.ai.openai-compatible-read-timeout-seconds`, defaulting to 360 seconds instead of a hard-coded 120 seconds.
- Slash commands are limited to `/new` and `/plan`; plan execution and cancellation are triggered through UI/API actions.

## Risks

- The timeout failure path records a reviewable failure rather than attempting to keep detached work alive. Long executions need the configured timeout raised if 360 seconds is insufficient.
- A true resume-from-partial-execution workflow still does not exist; rerunning execution starts from the saved plan and clears prior execution chat context.

## Follow-up Items

- Consider a dedicated resumable execution UX if users need to continue from preserved partial evidence rather than rerun a saved plan from the beginning.
