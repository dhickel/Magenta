# Date

2026-05-01

# Change Summary

Refactored plan mode to use a dedicated planning model, richer planning state, explicit deliverables, optional inputs/outputs, queued free-response user questions, approval, save-as-task, streamed clear-context execution, and validator-gated completion. Added execution failure cleanup so failed plan execution records evidence and leaves the plan in review instead of stuck executing.

# Files

- `src/main/java/io/mindspice/magenta2/ai/config/user/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/*`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/*`
- `src/main/java/io/mindspice/magenta2/api/web/*`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/schema.sql`
- `config/ai-config.example.json`

# Behavioral Impact

Planning uses `planningModel` and records draft state outside normal chat history. Planning answers are appended back to chat history as formatted user messages. `/clr-exec-plan` is removed; `/exec-plan` now clears context and streams execution from the approved plan. The browser shows a planning panel for queued text questions, approval, execution, save-as-task, and cancellation. Ready-for-approval plans render a transient markdown preview in chat without adding that preview to model context or message history. `plan_complete` runs a validator pass before marking a plan completed.

# Risks

Existing saved plans with old prompt columns remain in the database but new planning questions use `pending_questions_json`. Browser planning actions are new API surface and should be exercised manually after deployment.

# Follow-up Items

Add reusable task input execution UI after the basic plan workflow settles.
