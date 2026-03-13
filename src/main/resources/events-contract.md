# Magenta2 Phase 01 JSONL Event Contract

Each line in the debug stream is a JSON object with this stable shape:

- `timestamp` (ISO-8601 instant)
- `sessionId` (string)
- `agentId` (string)
- `eventType` (string)
- `payload` (object)
- `correlationId` (string)

Core event types:

- `session_started`
- `message_in`
- `message_out`
- `tool_call`
- `tool_result`
- `context_compacted`
- `context_send_budget`
- `model_failure`
- `security_decision`
- `session_closed`
