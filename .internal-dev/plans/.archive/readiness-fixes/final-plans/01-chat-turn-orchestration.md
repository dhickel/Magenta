# 01 -- Chat Turn Orchestration (`ChatService` core)

## Context

`ChatService.java` (~1814 lines) is the central orchestrator for all chat interactions. Over multiple feature cycles -- plan mode, task mode, context compaction, tool-loop guards, repair logic, streaming, and audit -- responsibilities accreted into a single class without clear seams. The result is a service that simultaneously owns:

- Prompt composition and mode-specific instruction merging
- Tool access policy with plan/task/normal/execution mode filtering
- A 270-line monolithic tool-call loop with embedded repair/retry/compaction/audit
- Plan/task terminal-turn repair (empty-final-response retry, plan-turn repair, execution-completion repair)
- Context usage maintenance and compaction coordination
- Audit recording scattered inline throughout the flow
- ThreadLocal context management for tool execution scope

Helper method names are behavior-heavy but weakly named. For instance:
- `currentInstructions` (line 1203) actually assembles the full turn instruction list (system prompt + user message)
- `toolChatMessage` (line 1213) runs the entire tool chat and picks the last assistant message from stored history

## Goal

Decompose chat-turn orchestration into explicit, testable components while preserving current user-facing behavior and SSE/API contracts.

## In Scope

- `ChatService` responsibility reduction and stage-based turn pipeline extraction.
- Intent-revealing helper renames for prompt/context mutation and terminal repair logic.
- Characterization tests that lock pre-refactor behavior.

## Out of Scope

- Net-new chat features or protocol changes.
- Frontend stream contract changes.
- Cross-module architectural rewrites outside chat turn orchestration.

## Current Architecture

### Call Chain: Non-Streaming Chat

```
ChatController.chat(ChatRequest) → ChatService.chat(ChatRequest)
  → RequestResolver.resolve(MsgRequest) [conversationId, model, newConversation flag]
  → ChatService.chat(ResolvedChatRequest)
    → turnCoordinator.submit(...) OR chatNow(request, null)
      → approvedTools(request) → filterApprovedTools(names, request) → interactionMode(conversationId)
      → [TOOL PATH] toolChatWithRetry → toolChat (THE 270-LINE MONSTER)
      → [PLAIN PATH] plainChat (20 lines, 5 responsibilities)
```

### Method Inventory by Responsibility

**A. Turn Entry & Dispatch (4):** `chat(ChatRequest)`, `chat(String,String,String)`, `chat(ResolvedChatRequest)`, `chatNow(...)`
**B. Prompt/Context Assembly (4):** `defaultSystemPrompt()`, `effectiveSystemPrompt()`, `currentInstructions()`, `prompt()`
**C. Tool Access Control (2):** `approvedTools()`, `filterApprovedTools()`
**D. Tool Loop (9 embedded methods):** `toolChat()`, `toolChatMessage()`, `toolChatWithRetry()`, `toolOptions()`, `toolFinalOptions()`, `toolTranscriptEntries()`, `toolMessage()`
**E. Plain Chat (5):** `plainChat()`, `plainStream()`, `streamNow()`, `stream()`, `stream(Resolved,ActiveTurn)`
**F. Repair (7):** `isEmptyFinalResponse()`, `emptyFinalResponseControlMessage()`, `requiresPlanTurnRepair()`, `requiresExecutionCompletionRepair()`, `invalidPlanTurnControlMessage()`, `invalidExecutionCompletionControlMessage()`, `toolUseAbortControlMessage()`
**G. Thinking & Rendering (7):** `renderAssistantMessage()`, `assistantMessageWithThinking()`, `thinkingText()`, `collectThinking()`, `combinedThinking()`, `splitThinkingFallback()`
**H. Audit/Persistence:** Scattered inline calls throughout `plainChat`, `toolChat`, `plainStream`
**I-M. Context, Conversation, Plan/Task, Snapshot, Utility:** ~20 more methods

**Total: ~54 methods across 1814 lines**

## Target Architecture

### Named Stages

```
┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
│ RESOLVE  │──>│ ASSEMBLE │──>│ AUTHORIZE│──>│ EXECUTE  │
│ (already │   │ prompt   │   │ tools    │   │ model    │
│ resolved)│   │ context  │   │ policy   │   │ call(s)  │
└──────────┘   └──────────┘   └──────────┘   └─────┬────┘
                                                     │
                      ┌──────────────────────────────┘
                      ▼
                 ┌──────────┐   ┌──────────┐   ┌──────────┐
                 │ FINALIZE │──>│  AUDIT   │──>│ RESPOND  │
                 │ repair + │   │ persist  │   │ build    │
                 │ messages │   │ events   │   │ response │
                 └──────────┘   └──────────┘   └──────────┘
```

### Extracted Components (all in `ai.chat.service.turn/`)

1. **`TurnContext`** — Mutable carrier (plain Java class) holding all data flowing between stages. Fields: `resolvedRequest`, `activeTurn`, `systemPrompt`, `turnInstructions`, `approvedTools`, `toolsEnabled`, `toolActivities`, `finalAssistantMessage`, `planCompletionDetected`, `validatedFinalMessage`, `forcedPlanningQuestion`, `storedContextUsage`. One instance per turn.

2. **`PromptContextAssembler`** — Compose system prompt merging default agent prompt with mode-specific runtime instructions from PlanService/TaskService. Extracted from: `effectiveSystemPrompt` (line 1535), `currentInstructions` (line 1203), `defaultSystemPrompt` (line 1524), `interactionMode` (line 1561).
   - Public: `void assemble(TurnContext ctx)`

3. **`ToolAccessPolicy`** — Resolve which tool callbacks are available, filtered by interaction mode and approved-tool configuration. Extracted from: `approvedTools` (line 1435), `filterApprovedTools` (line 1452). Static allowlists move here.
   - Public: `void authorize(TurnContext ctx)`

4. **`ToolTurnEngine`** — Execute the tool-calling loop: prepare prompt, call model, execute tools, handle checkpoints, detect completion, handle abort, apply repair hooks. Extracted from `toolChat` (line 931). Internal loop decomposed into step methods.
   - Public: `TurnEngineResult execute(TurnContext ctx)`
   - Uses internal `LoopState` record for immutable state tracking

5. **`TerminalTurnRepair`** — Enforce terminal behavior guarantees for PLAN/EXECUTE modes. Extracted from repair methods (lines 1297-1383) and forced-question logic (lines 1149-1153). Renamed methods with intent-revealing names.
   - Public: `void enforce(TurnContext ctx)`

6. **`TurnAuditWriter`** — Write audit records and context-usage snapshots. Facade over `AuditService`. Extracted from scattered inline audit calls.
   - Public: `void recordTurnStart(TurnContext ctx)`, `void recordTurnEnd(TurnContext ctx)`

7. **`ChatTurnPipeline`** — Orchestrator wiring all stages in sequence. Replaces `ChatService.chatNow` and `ChatService.streamNow`.
   - Public: `MsgResponse executeTurn(TurnContext ctx)`, `Flux<ChatMessage> executeStreamTurn(TurnContext ctx)`

### Rename Plan

| Current Name | New Name | Destination |
|---|---|---|
| `effectiveSystemPrompt` | `mergeModePrompt` | PromptContextAssembler |
| `currentInstructions` | `assembleTurnInstructions` | PromptContextAssembler |
| `approvedTools` | `resolveApprovedTools` | ToolAccessPolicy |
| `filterApprovedTools` | `filterToolsByMode` | ToolAccessPolicy |
| `isEmptyFinalResponse` | `hasNoContentOrToolCalls` | TerminalTurnRepair |
| `requiresPlanTurnRepair` | `needsPlanTurnRepair` | TerminalTurnRepair |
| `requiresExecutionCompletionRepair` | `needsExecutionCompletionRepair` | TerminalTurnRepair |
| `toolChat` | Decomposed into step methods | ToolTurnEngine |
| `plainChat` | `executePlainTurn` | ChatTurnPipeline |

### Refactored ChatService

After extraction, `ChatService` becomes a facade (~400-500 lines) that:
- Owns the public API (`chat(...)`, `stream(...)`)
- Delegates to `RequestResolver` for resolution
- Delegates to `ChatTurnPipeline` for turn execution
- Keeps conversation/plan/task orchestration methods
- Keeps thinking/message rendering as utilities

---

## Implementation Steps

### Step 0: Create Package Structure
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/turn/`

### Step 1: Write Characterization Tests (BLOCKING prerequisite)
**File:** `src/test/java/.../ChatServiceCharacterizationTest.java`

Write 18 tests locking current behavior BEFORE any extraction:
- Normal mode single tool → final answer (basic happy path)
- Plain chat without tools
- Plan mode: ask_questions ends turn, ready_for_approval ends turn, forced question on free text
- EXECUTE_PLAN: completion detected, needs review on no completion
- EXECUTE_TASK: completion detected, needs review on no completion
- ToolLoopGuard: abort on identical calls, abort on error rate
- Thinking-only response retried
- Context compaction during tool loop
- Tool-unsupported fallback to plain chat
- Snapshot/restore on transient failure
- System prompt composition: plan mode, execute task mode
- discardLastUserMessage, history rendering

### Step 2: Extract `TurnContext`
Pure data carrier, no dependencies. Low risk. Mutable class with builder.

### Step 3: Extract `PromptContextAssembler`
Depends on AiConfig, RuntimeSettingsService, PlanService, TaskService. Key invariant: plan mode prompt completely replaces default system prompt.

### Step 4: Extract `ToolAccessPolicy`
Move static allowlists here. Mode-dependent filtering must exactly preserve branch order: PLAN → TASK → EXECUTE_PLAN → EXECUTE_TASK → NORMAL.

### Step 5: Extract `TerminalTurnRepair`
Pure utility at this step — ChatService.toolChat calls its methods rather than owning them inline.

### Step 6: Extract `TurnAuditWriter`
Wraps AuditService with `recordTurnStart()` / `recordTurnEnd()` phase methods.

### Step 7: Extract `ToolTurnEngine` (HIGHEST RISK)
The core extraction. Decompose the 270-line method into internal step methods using a `LoopState` record. The while/while/if nesting is subtle — preserve exact operation order.

### Step 8: Create `ChatTurnPipeline` orchestrator
Wires all stages. Replaces `chatNow` and `streamNow`.

### Step 9: Refactor `ChatService` to delegate to pipeline
Replace method bodies, remove extracted code, keep only facade + conversation/plan management + thinking utilities.

### Step 10: Update Tests
Move component-specific tests to new test files. Characterization tests must still pass against ChatService public API.

### Step 11: Update Spring Configuration
Verify `@ComponentScan` covers the `turn` sub-package.

---

## Risk Assessment

| Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|
| Plan mode terminal behavior regression | HIGH | MEDIUM | 4 characterization tests lock exact behavior |
| Tool loop state corruption during extraction | HIGH | MEDIUM | LoopState record is immutable; each iteration copies |
| Context compaction timing changes | MEDIUM | LOW | Characterization test locks compaction behavior |
| ThreadLocal leak in streaming | HIGH | LOW | try/finally around execute() preserved from original |
| Existing test breakage | LOW | HIGH | Public static references kept; delegates to new classes |

Rollback: Each step is a separate commit. `git revert` per-commit rollback. All characterization tests pass before and after each step.

## Validation

- Run characterization tests first, then rerun after each extraction seam.
- Verify public `ChatService` API behavior remains unchanged.
- Run full regression suite: `mvn test`.

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `alpha-milestone-gate-summary.md`: "`ChatService` handles too many responsibilities (84KB file). This violates SRP and poses a high regression risk."
- `alpha-milestone-gate-summary.md`: "Decompose `ChatService` into specialized components."
- `code-quality-and-smells-report.md`: "`ChatService` handles chat, streaming, planning, task execution, model routing, auditing, and context maintenance."
- `code-quality-and-smells-report.md`: "`ChatService.filterApprovedTools` restricts available tools based on `PlanMode`" and "`ChatService` repair logic ensures PLAN/TASK turns end with a terminal tool call."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, the list of tests run, and any new/changed test files.

Validation sub-agent prompt:
```text
You are validating the Chat Turn Orchestration remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/01-chat-turn-orchestration.md`, then manually inspect the implementation diff and the changed test files. Do not trust the implementer's summary or test claims without checking the code.

Validation contract:
- Confirm `ChatService` no longer directly owns prompt assembly, tool filtering, tool loop execution, terminal repair, and turn audit responsibilities.
- Confirm public chat APIs, response records, SSE event names/payloads, and PLAN/TASK terminal-turn behavior remain compatible with the pre-refactor behavior.
- Confirm extracted components have narrow responsibilities and intent-revealing method names.
- Confirm characterization tests cover normal chat, tool chat, PLAN, EXECUTE_PLAN, EXECUTE_TASK, repair paths, compaction timing, and fallback behavior.
- Confirm `mvn test` and a bounded Spring Boot startup smoke were run or clearly report why they were not.

Return findings first, ordered by severity, with file/line references and exact missing validation if any.
```

Manual work proof to verify:
- Inspect the final diff for `ChatService.java` and each new `ai.chat.service.turn` class.
- Verify characterization tests were committed before or alongside extraction work.
- Verify test output from focused chat tests, `mvn test`, and startup smoke rather than accepting a verbal pass/fail summary.

---

## Exit Criteria

1. All 18 characterization tests pass against `ChatService` public API
2. `ChatService.java` reduced to ~400-500 lines (from 1814)
3. Seven new components extracted into `ai.chat.service.turn/`: TurnContext, PromptContextAssembler, ToolAccessPolicy, ToolTurnEngine, TerminalTurnRepair, TurnAuditWriter, ChatTurnPipeline
4. ChatService no longer owns prompt composition, tool filtering, tool loop, repair logic, or audit directly
5. All existing tests pass (`mvn test`)
6. Helper methods renamed to intent-revealing verbs with pre/post-conditions
7. Plan/Task terminal behavior unchanged
8. External API (`ChatResponse.MsgResponse`, `ChatMessage`, SSE events) stable

## Critical Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — primary target
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — existing tests must pass
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java` — tight integration with ToolTurnEngine
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AuditService.java` — TurnAuditWriter wraps it
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java` — already extracted
