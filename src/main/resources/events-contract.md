# Magenta2 Phase 01 JSONL Event Contract

Each line in the debug stream is a JSON object with this stable shape:

- `timestamp` (ISO-8601 instant)
- `sessionId` (string)
- `agentId` (string)
- `eventType` (string)
- `payload` (object)
- `correlationId` (string)

Core event types:

- `session_init`
- `user_message`
- `agent_message`
- `tool_call`
- `tool_result`
- `policy_denied`
- `security_decision`
- `session_end`

Additional audit event type:

- `policy_override`
