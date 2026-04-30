# Context

Tool transcript messages are persisted after tool execution, but the web stream only informs users after all tool loop iterations finish. Tool history also flattens every tool result into a generic summary.

# Goal

Show each completed tool call to users as it completes, with collapsed-by-default details and bounded expanded arguments/results.

# In Scope

- Add structured tool activity DTOs to chat messages, stream events, and responses.
- Emit a tool SSE event after each completed tool response.
- Improve tool transcript summaries and display truncation.
- Render tool activity cards in the browser history and live stream.

# Out of Scope

- Durable storage outside existing chat memory.
- Tool approval or orchestration changes.
- Changing the model-visible raw output retention policy beyond adding display fields.

# Implementation Steps

- Extend chat model records with additive tool activity fields and compatibility constructors.
- Refactor tool transcript creation to return structured entries for persistence and UI conversion.
- Add streaming callback support in `ChatService` tool loop.
- Update the browser client and page CSS to render expandable tool cards.
- Add focused unit tests for transcript display and payload compatibility.

# Validation

- Run focused tool transcript, chat service, controller/frontend tests.
- Run full Maven test suite if focused tests pass.

# Exit Criteria

- Each tool response can be emitted as a `tool` SSE event before final assistant completion.
- History retains visible tool cards with collapsed summaries and expanded details capped at 2,000 characters.
- Existing chat response/history consumers remain compatible with prior fields.
