# Date

2026-05-08

# Change Summary

Fixed three readiness-blocking issues: chat naming jobs failing with models that don't support tool-calling options, thinking collapsible boxes rendering as raw inline text, and plan approval/continue/cancel buttons not responding to clicks.

# Files

- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/js/chat-client.js`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`

# Behavioral Impact

**Chat naming** now uses `basicChatOptions()` (model name only, no think/reasoning-effort settings) and guards against re-enqueue when any previous title job exists — even a failed one. Title jobs will reliably complete for models that previously failed due to unsupported option formats.

**Thinking display** now has styled `<details>` boxes with border, background, padding, and open/closed state indicators. The native disclosure widget provides collapsible behavior; CSS provides visual separation from the message body.

**Plan buttons** now use document-level event delegation instead of panel-level delegation. This survives the repeated `innerHTML` replacement that occurs during SSE streaming when `renderPlanningPanel()` is called from `updatePlanStatus()`.

# Risks

- `basicChatOptions()` returns `ToolCallingChatOptions` (the supertype) with only model name set — future Spring AI versions may interpret absent fields differently.
- The document-level click handler fires on every click anywhere in the page; performance impact is negligible since `closest('[data-plan-action]')` short-circuits immediately for non-matching targets.

# Follow-up Items

- Consider extracting the title job model selection to use a dedicated lightweight/fast model rather than the conversation's chat model.
- Audit other `innerHTML` + direct `addEventListener` patterns in `app.js` if those elements ever become dynamically replaced.
