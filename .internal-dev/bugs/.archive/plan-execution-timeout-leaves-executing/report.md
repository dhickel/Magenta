# Summary

Saved plan execution can remain in `EXECUTE_PLAN` / `EXECUTING` after an MCP-driven stream timeout and tool-mode failure evidence.

# Scope

Live saved-plan execution through `/api/chat/{conversationId}/plan/execute/stream`, especially when the browser/MCP caller times out before the stream reaches `done` or `error`.

# Reproduction

1. Start the app against an isolated database:
   `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-mcp-fix-validation.sqlite --magenta.executor.chat-threads=4'`
2. Use Playwright MCP to run a chat-only plan through `/plan`, `/plan/answers`, `/plan/approve`, and `/plan/execute/stream`.
3. Let the MCP tool call hit its 120 second timeout while the browser-side `fetch` is waiting on the execution stream.
4. Load `/api/chat/{conversationId}/history`.

# Expected

If execution fails or is cancelled after the client times out, the plan should settle into a terminal reviewable state such as `NORMAL` / `NEEDS_REVIEW`, with failure evidence and no active execution mode.

# Actual

Conversation `2b444c1a-9472-4877-855e-5bae07d625ba` remained in `mode=EXECUTE_PLAN`, `status=EXECUTING` after the client-side MCP timeout. It had only the synthetic execution user message persisted, but the plan state already contained failure evidence and unmet criteria.

# Evidence

Persisted plan evidence included:

- `Summary: Plan execution failed: plan_ask_questions tool requires plan mode which is not active in this chat context...`
- `Deviation: Cannot execute plan as designed — plan_ask_questions tool is gated behind plan mode which is not available`
- `Unmet criterion: The user's reply in chat history contains the exact string "MCP ARRAY FIX OK" — NOT MET`

After an additional wait and history reload, the plan still reported `mode=EXECUTE_PLAN`, `status=EXECUTING`, with no assistant completion message.

When the temporary Spring Boot process was stopped, graceful shutdown waited on one active request and eventually logged `Graceful shutdown aborted with one or more requests still active`. The still-running execution then failed after Hikari shutdown with `CannotGetJdbcConnectionException: Failed to obtain JDBC Connection`, followed by `onErrorDropped` because the async request had already completed processing.

# Impact

The UI can show an indefinitely executing saved plan even though the execution has already generated failure evidence. Users and agents may not know whether it is still running, failed, or safe to retry.

# Status

Fixed on 2026-05-06.

# Next Action

Archived after adding configurable stream/model timeouts and routing stream timeout, stream error, and client-send failure paths through execution failure finalization.
