# Summary

Saved plan execution can fail when the execution model sends a scalar string for a `List<String>` tool parameter.

# Scope

Live saved-plan execution through `/api/chat/{conversationId}/plan/execute/stream`, especially execution turns that call `plan_report` or `plan_complete`.

# Reproduction

1. Start the app against an isolated database:
   `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-mcp-chat-test.sqlite --magenta.executor.chat-threads=4'`
2. Start plan mode with `/api/chat/commands` and command `/plan`.
3. Answer the queued planning question with a minimal no-side-effect plan: respond exactly with `PLAN EXECUTION OK`; do not write files, run shell commands, call web search, or modify anything outside chat history.
4. Approve with `PATCH /api/chat/{conversationId}/plan/approve`.
5. Execute with `POST /api/chat/{conversationId}/plan/execute/stream`.

# Expected

Execution should either complete the plan and emit a `done` SSE event, or reject malformed model tool arguments in a controlled way that lets the model retry with valid JSON-compatible argument shapes.

# Actual

The execution stream emitted several `tool` events and then an `error` event:

`Cannot construct instance of java.util.ArrayList ... no String-argument constructor/factory method to deserialize from String value ('None.')`

The saved plan was moved to `NORMAL` / `NEEDS_REVIEW` and failure evidence was recorded.

# Evidence

Test conversation: `fae58440-16ba-486c-9c24-8a099098c193` in the isolated `/tmp/magenta2-mcp-chat-test.sqlite` database.

Server log showed Spring AI `MethodToolCallback` failing conversion from JSON to `java.util.List<java.lang.String>`, caused by Jackson receiving the scalar string `None.` where a list was expected. The app then recorded execution evidence:

- `Summary: Execution failed before completion.`
- `Deviation: Cannot construct instance of java.util.ArrayList ... String value ('None.')`
- `Unmet criterion: Saved plan execution did not complete.`

# Impact

Models that produce human strings such as `None.` for optional list arguments can break plan execution after partial tool activity. The failure state is correctly visible as `NEEDS_REVIEW`, but execution does not finish even for a trivial no-side-effect plan.

# Status

Open.

# Next Action

Consider hardening plan execution tool schemas or argument coercion for optional list fields, and add a regression test where `plan_report` or `plan_complete` receives scalar text for a list parameter.
