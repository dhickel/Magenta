# Phase 06: Chat, Model Overrides, and Agent Chat Surfaces — Evidence

**Date**: 2026-05-13
**Branch**: `operational-ui-refactor`
**App running**: `http://localhost:18080`
**Playwright available**: No — all validation via curl; browser-click checks noted as BLOCKED.

---

## 1. Chat Page Load (`GET /chat`)

**Status**: PASS

### Request
```bash
curl -s http://localhost:18080/chat
```

### Markup elements verified present

| Element | Selector | Present |
|---------|----------|---------|
| Chat model selector | `#chat-model-select` | YES |
| Planning model selector | `#chat-planning-model-select` | YES |
| Session list container | `#chat-session-list` | YES (empty, JS-populated) |
| Chat input textarea | `#chat-input` | YES |
| Chat form | `#chat-form` | YES |
| Chat module (SSE) | `#magenta-chat-module` (data-sp-chat-transport="SSE") | YES |
| SSE stream endpoint marker | `data-sp-chat-stream-endpoint="/api/chat/stream"` | YES |
| History endpoint marker | `data-sp-chat-history-endpoint="/api/fragments/chat/transcript"` | YES |
| Plan status area | `#chat-plan-status` | YES |
| Plan title display | `#chat-plan-title` | YES |
| Plan hint display | `#chat-plan-hint` | YES |
| Plan evidence area | `#chat-plan-evidence` | YES |
| Planning panel | `#chat-planning-panel` | YES |
| Token usage display | `#chat-token-usage` | YES |
| Error display | `#chat-error` | YES |
| Active session display | `#chat-active-session` | YES |

### Model dropdown options

**Chat model select** (`#chat-model-select`):
- qwen3.6:35b (default selected)
- granite4.1:8b
- gemma4-fullctx:e4b
- gemma4-e4b-UC:latest
- gemma4-26b:32k
- deepseek-v4-pro

**Planning model select** (`#chat-planning-model-select`):
- qwen3.6:35b
- granite4.1:8b
- gemma4-fullctx:e4b
- gemma4-e4b-UC:latest
- gemma4-26b:32k
- deepseek-v4-pro (default selected)

### Browser-only checks (BLOCKED)
- Visual layout of sidebar sessions
- SSE live connection indicator
- Token usage bar animation
- Scroll behavior in chat history

---

## 2. Chat SSE Flow

**Status**: PASS

### Endpoint
`POST /api/chat/stream` — `Content-Type: application/json`

### Request format (from `ChatRequest.MsgRequest`)
```json
{
  "conversationId": "<optional UUID>",
  "message": "<required>",
  "model": "<optional>",
  "planningModel": "<optional>"
}
```

### Test: Simple message
```bash
timeout 15s curl -s -N -X POST http://localhost:18080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Say just OK"}'
```

### SSE events received (in order)
1. `event:start` — conversationId, model (qwen3.6:35b), turnId, interruptToken, planState
2. `event:context` — contextUsage (usedTokens, maxTokens, triggerTokens, percentUsed)
3. `event:chunk` — text ("OK"), renderedHtml, thinkingHtml, contextUsage, planState
4. `event:done` — conversationId, model, text, renderedHtml, contextUsage, planState

### Result
- Model responded with "OK" via `qwen3.6:35b` (default)
- Conversation ID generated: `f87464f8-0ec7-4481-abeb-6a46bc940bc8`
- Context usage: 1113/32000 tokens (3.48%)
- Session persisted and appears in session list

---

## 3. Model Selectors & Override in Chat

**Status**: PASS

### Test: Explicit model override
```bash
timeout 15s curl -s -N -X POST http://localhost:18080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Reply with just OK","model":"granite4.1:8b"}'
```

### Result
- SSE start event reports `"model":"granite4.1:8b"` — model override respected
- Response: "OK"
- Context usage: 1114/32000 tokens

### Test: Planning model override (non-planning mode)
```bash
timeout 15s curl -s -N -X POST http://localhost:18080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Say OK","planningModel":"deepseek-v4"}'
```

### Result
- Uses default chat model (qwen3.6:35b), not planning model
- Planning model only takes effect in `/plan` mode
- Correct behavior for non-planning chat

---

## 4. Settings Page & Model Routing Persistence

**Status**: PARTIAL PASS (model alias confusion bug)

### GET /settings
Page loads with model dropdowns, available models chip list, and form targeting `hx-put="/settings"`.

### Settings form IDs present
- `#settings-default-agent-id` (value: `23579fcf-ca99-4862-a2fd-b8eb6073928c`)
- `#settings-default-agent-name` (value: `magenta`)
- `#settings-default-model` (selected: `local-qwen`)
- `#settings-planning-model` (selected: `deepseek-v4`)
- `#settings-summary-model` (selected: `granite4.1:8b`)
- `#settings-compaction-model` (selected: `local-gemma-e4b`)
- `#settings-context-buffer` (value: `33`)

### PUT /settings — valid alias
```bash
curl -s -X PUT http://localhost:18080/settings \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "defaultModel=local-qwen&planningModel=deepseek-v4&summaryModel=granite4.1:8b&compactionModel=local-gemma-e4b&..."
```
**Result**: "Use Save to persist changes." — SUCCESS. GET confirms values persisted.

### PUT /settings — raw model name (BUG)
```bash
curl -s -X PUT http://localhost:18080/settings \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "defaultModel=qwen3.6:35b&..."
```
**Result**: `Error: defaultModel references missing model: qwen3.6:35b`

**BUG FOUND**: The settings form dropdowns include both model aliases (e.g., `local-qwen`, `deepseek-v4`) and remote model names (e.g., `qwen3.6:35b`, `deepseek-v4-pro`). The "Available Models" chip list shows only remote model names. The backend validation (`RuntimeSettingsService.validateModel()`) checks against `aiConfig.models()` keys, which are the aliases. Selecting a raw model name from the dropdown causes a save error. The dropdown should either:
- Show only valid model keys (aliases), or
- The backend should accept remote model names by reverse-resolving them

### Browser-only checks (BLOCKED)
- Dropdown interaction behavior
- Error message visibility styling
- Form re-render after save

---

## 5. Session Management

**Status**: PASS

### GET /api/chat/sessions
Returns `{conversationIds: [...], sessions: [...]}` with session objects containing:
- conversationId, title, titleJobStatus, favorite, archived, updatedAt

### PATCH /api/chat/{id}/title
```bash
curl -s -X PATCH http://localhost:18080/api/chat/{id}/title \
  -H "Content-Type: application/json" \
  -d '{"title":"My Test Chat"}'
```
**Result**: Title updated successfully. Returns updated session object.

### PATCH /api/chat/{id}/favorite
```bash
curl -s -X PATCH http://localhost:18080/api/chat/{id}/favorite \
  -H "Content-Type: application/json" \
  -d '{"isFavorite":true}'
```
**Result**: Favorite toggled. Returns updated session with `favorite: true`.

### PATCH /api/chat/{id}/archive
```bash
curl -s -X PATCH http://localhost:18080/api/chat/{id}/archive \
  -H "Content-Type: application/json" \
  -d '{"archived":true}'
```
**Result**: Archive toggled. Returns updated session with `archived: true`.

### DELETE /api/chat/{id}
```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE http://localhost:18080/api/chat/{id}
```
**Result**: 204 No Content. Session removed from session list.

### Session switching (via history endpoint)
`GET /api/chat/{id}/history` returns full history for any conversation ID, enabling session switching.

### Transcript fragment endpoint
`GET /api/fragments/chat/transcript?conversationId={id}` returns rendered HTML chat history for HTMX swap.

### Browser-only checks (BLOCKED)
- Session list UI rendering with rename inline forms
- Bulk select/delete/archive/favorite operations
- Session click-to-switch behavior

---

## 6. Planning Mode & Plan Status Display

**Status**: PASS

### Plan initiation via /api/chat/commands
```bash
curl -s -X POST http://localhost:18080/api/chat/commands \
  -H "Content-Type: application/json" \
  -d '{"command":"/plan"}'
```
**Result**: Planning mode entered. Uses `deepseek-v4-pro` (matching the settings planning model). Returns queued question from `ask_user_questions` tool.

### Plan state in SSE events
Every SSE event includes a `planState` object with:
- mode: "NORMAL" or "PLAN"
- status: null | "READY_FOR_APPROVAL" | "APPROVED"
- title, summary, goal, notes
- deliverables, inputs, outputs, steps, assumptions, acceptanceCriteria
- executionEvidence, validationFeedback
- promptType, promptQuestion, promptOptions, promptQuestionIndex, promptQuestionCount
- approvalMarkdown, approvalHtml

### Planning panel actions (from chat-client.js JS logic)
The planning panel renders contextual buttons based on plan state:
- **During planning**: "Cancel planning" button (`data-plan-action="cancel"`)
- **READY_FOR_APPROVAL**: "Approve plan" + "Continue planning"
- **APPROVED**: "Execute now" + "Save as task"

### Plan endpoints tested
| Endpoint | Method | Status |
|----------|--------|--------|
| `/api/chat/commands` (plan) | POST | PASS |
| `/api/chat/{id}/plan/answers` | POST | PASS (stale answer validation works) |
| `/api/chat/{id}/plan/approve` | PATCH | Endpoint exists (not fully tested — requires plan in READY_FOR_APPROVAL state) |
| `/api/chat/{id}/plan/continue` | PATCH | Endpoint exists |
| `/api/chat/{id}/plan/cancel` | PATCH | PASS — returns mode:NORMAL |
| `/api/chat/{id}/plan/save-task` | PATCH | Endpoint exists |
| `/api/chat/{id}/plan/execute` | POST | Endpoint exists |
| `/api/chat/{id}/plan/execute/stream` | POST | Endpoint exists (SSE stream) |
| `DELETE /api/chat/{id}/plan` | DELETE | Endpoint exists (exit plan + discard) |

### Browser-only checks (BLOCKED)
- Planning panel visual rendering with questions form
- Approval preview insertion into chat history
- Execute/save-task button behavior
- Plan status bar expand/collapse

---

## 7. Interrupt & Cancel

**Status**: PASS (endpoint), NOTE (no visible Stop button)

### Interrupt endpoint
`POST /api/chat/turns/{turnId}/interrupt`
```json
{"conversationId":"<uuid>","interruptToken":"<token>","message":"<new message>"}
```

Test with non-existent turn:
```bash
curl -s -X POST http://localhost:18080/api/chat/turns/nonexistent/interrupt \
  -H "Content-Type: application/json" \
  -d '{"conversationId":"...","interruptToken":"fake","message":"stop"}'
```
**Result**: `{"status":"TURN_NOT_ACTIVE"}` — endpoint exists and validates correctly.

### Interrupt mechanism (from chat-client.js)
- During active streaming, the Send button text changes to "Send update"
- Sending a new message while streaming calls `sendInterruptOrQueue()`
- If active turn exists: POSTs interrupt with new message to `/api/chat/turns/{id}/interrupt`
- If no active turn: queues the message for after current turn completes
- The SSE `event:interrupt` is handled client-side to append the pending user message

### NOTE: No visible Cancel/Stop button
The chat page markup contains no standalone "Stop generating" or "Cancel" button for active streaming. The interrupt is initiated by sending a new message. This design choice (interrupt-by-new-message) is a deliberate interaction pattern, not a missing feature. The planning panel does have explicit "Cancel planning" button for plan mode.

### Browser-only checks (BLOCKED)
- "Send update" button state during streaming
- Message queuing behavior after interrupt
- Stream interruption visual feedback

---

## 8. Agent Chat Panels & Agent Surfaces

**Status**: PARTIAL PASS (back-end API works, front-end panel not wired)

### Agent list page (`GET /agents`)
- Lists 3 agents: "Agent 71374", "Agent 89607", "magenta"
- Each shows status (ACTIVE), Docker (STOPPED), model, queue count, inbox count
- Actions: Wake/Sleep/Restart/Refresh/Disable/Delete

### Agent detail page (`GET /agents/{id}`)
Tabs available: Dashboard, Queue, Inbox, Jobs, Schedules, Reactions, Workspace, Outputs, History.
**No "Chat" tab visible.** Side panel shows Profile editor and Submit Work form.

### Agent chat API endpoint
`POST /api/agents/{agentId}/chat/stream` (SSE) — TESTED:
```bash
curl -s -X POST http://localhost:18080/api/agents/23579fcf-ca99-4862-a2fd-b8eb6073928c/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message":"Hello","pageContext":"agent-dashboard"}'
```
**SSE events received:**
1. `event:start` — `{"event":"start","agentId":"23579fcf-...","agentName":"magenta"}`
2. `event:done` — `{"event":"done","agentId":"23579fcf-...","conversationId":"85e7ca9b-...","model":"local-qwen","message":"Hello! How can I help you today?"}`

**Result**: Agent chat API works end-to-end. Model resolves to `local-qwen` (agent's configured model). Response in single `done` event (no intermediate chunks — different SSE contract than main chat).

### Agent side-panel chat (agent-chat.js)
- File exists: `/js/orchestration/agent-chat.js`
- Exports `initAgentChat()` function
- Creates a collapsible side panel with toggle Open/Close
- Sends messages to `/api/agents/{agentId}/chat/stream`
- **NOT INTEGRATED**: No page imports or calls `initAgentChat()`. No `data-agent-chat-panel` attribute on any page. Test confirms absence.

### DEFECT: Agent side-panel chat not wired
The agent-chat.js module is fully implemented but not loaded on any orchestration page. The test at `OrchestrationControllerTest.java:499` explicitly asserts `doesNotContain("open-agent-chat")`. This appears to be intentionally deferred rather than a bug — the agent detail page uses tab-based navigation instead.

### Browser-only checks (BLOCKED)
- Agent list table interaction
- Agent detail tab switching
- Profile editor form
- Submit work form behavior
- Docker lifecycle button actions

---

## 9. Plan/Task/Job Model Override Fields

**Status**: PASS

### Plan editor (`GET /plans/_editor/_new`)
Model override fields present:
- `#plan-planning-model` — Planning Model dropdown (Default, qwen3.6:35b, granite4.1:8b, gemma4-fullctx:e4b, gemma4-e4b-UC:latest, gemma4-26b:32k, deepseek-v4-pro)
- `#plan-execution-model` — Execution Model dropdown (same options)

### Job editor (`GET /jobs/_editor/_new`)
Model override field present:
- `#job-model` — Default Model dropdown (same options)

### Project editor (`GET /projects/_editor/_new`)
Model override field present:
- `#project-model` — Default Model dropdown (same options)

### Workflow editor (`GET /workflows/_editor/_new`)
- **No model override fields found** — workflows don't expose model selection

### Note: Same alias/raw-name mixing in dropdowns
Plan, job, and project editors show raw model names (e.g., `qwen3.6:35b`) in their model dropdowns. The same alias-vs-raw-name confusion from the settings page applies here. Backend validation for these editors should be verified for consistency.

### Browser-only checks (BLOCKED)
- Plan editor save/reload cycle
- Job editor form submission
- Project editor form submission
- Model dropdown interaction

---

## Summary of Defects Found

1. **Settings model dropdown alias/raw-name confusion (BUG)**: Settings page dropdowns mix model aliases (`local-qwen`) and remote model names (`qwen3.6:35b`). Only aliases pass backend validation. The "Available Models" chip list shows raw names that fail validation if selected. Same issue likely affects plan/job/project editors.

2. **Agent side-panel chat not wired (DEFERRED FEATURE)**: `agent-chat.js` provides a collapsible agent chat panel, but it is not imported or initialized on any page. The agent detail page has no "Chat" tab. Test explicitly asserts absence of `open-agent-chat` marker.

3. **No standalone Stop/Cancel button during streaming (DESIGN NOTE)**: Interrupt is triggered by sending a new message rather than a dedicated stop button. The Send button text changes to "Send update" during streaming. This is a design choice, not a bug, but may not be immediately discoverable.

---

## Checks Requiring Browser Interaction (BLOCKED by Playwright unavailability)

1. Chat SSE live connection establishment and reconnection
2. Session list UI with inline rename, bulk operations
3. Planning panel question/answer form interaction
4. Plan approval preview rendering in chat history
5. "Send update" button behavior during streaming
6. Token usage bar animation
7. Agent list table sorting/filtering
8. Agent detail tab switching
9. Settings form dropdown selection UX
10. Plan/job/project editor form submissions
