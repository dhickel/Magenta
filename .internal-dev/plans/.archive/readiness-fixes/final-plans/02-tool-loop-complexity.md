# 02 -- Tool Loop Complexity and State Safety

## Context (What Is Broken, Why)

`ChatService.toolChat` (line 931-1201) is the nerve center of every tool-capable chat turn. It orchestrates tool dispatch, thinking extraction, context compaction, interrupt handling, runaway-tool abort, four distinct retry/repair paths, plan/task completion detection, audit recording, and final message assembly -- all within a single 270-line method driven by a mutable `boolean continueModelLoop` flag and three manual retry counters.

The alpha milestone gate review flagged this as the single highest-risk code in the codebase: state transitions are obscured by procedural control flow, retry conditions are distributed across four separate `if` blocks, and defect triage requires mentally simulating the entire method to understand why any particular path was taken.

## Goal

Replace monolithic tool-loop control flow with explicit, deterministic phase transitions and finite retry semantics that are easier to reason about and test.

## In Scope

- `toolChat` phase model extraction with typed outcomes.
- Centralized retry precedence and deterministic `ToolLoopGuard` behavior preservation.
- Diagnostic markers for branch-level observability.

## Out of Scope

- Replacing `ToolLoopGuard` policies themselves.
- Introducing a generic workflow/state-machine framework.
- Modifying external response contracts.

## Current Architecture: Full Loop Flow Map

### Method Entry: `toolChat()` (line 931-936)

**Phase 0 -- Setup (lines 940-980):** Save model, get instructions, prepare prompt, set ToolCallingChatOptions, set PlanToolExecutionContext, audit user message, initialize 16+ accumulator variables including `ToolLoopGuard`, three retry counters, and `continueModelLoop = true`.

**Phase 1 -- Outer Loop (lines 981-1148):** `while (continueModelLoop)` with inner tool-call loop and 4 retry paths.

**Inner Tool-Call Loop (lines 983-1081):** `while (response != null && response.hasToolCalls())`:
| Step | Lines | Concern |
|------|-------|---------|
| A | 984-991 | Guard identical calls (ToolLoopGuard) |
| B | 992 | Thinking extraction |
| C | 993-1009 | Tool execution + transcript building |
| D | 1010-1013 | History update |
| E | 1014-1028 | Interrupt polling |
| F | 1029-1035 | Guard tool responses (error rate) |
| G | 1036-1059 | Context checkpoint |
| H | 1060 | Audit context usage |
| I | 1061-1063 | Forward tool messages to consumer |
| J | 1064-1069 | Plan completion detection |
| K | 1070-1078 | Task completion detection |
| L | 1079-1080 | Next model call |

**Retry Paths (lines 1083-1147):**
1. **ToolUseAbort** (line 1083): Control message, final model call without tools
2. **Empty Final Response** (line 1109): Thinking-only response retry
3. **Plan Turn Repair** (line 1122): Enforce plan terminal tool calls
4. **Execution Completion Repair** (line 1136): Enforce plan_complete/task_complete

**Phase 2 -- Termination (lines 1149-1201):** Post-loop fallback, thinking aggregation, message assembly, persistence, context maintenance, response building.

### ToolLoopGuard Internals

Package-private `final class ToolLoopGuard` with:
- `identicalToolCallCounts` map keyed by `toolName + "\n" + normalizedArgs`
- `recentToolOutcomes` sliding window (max 8)
- Constants: `TOOL_ERROR_WINDOW_SIZE=8`, `TOOL_ERROR_WINDOW_LIMIT=5`, `IDENTICAL_TOOL_CALL_LIMIT=5`
- Fully deterministic: same input sequence always produces same abort decision

---

## Target Architecture

### 1. Step Enum and Dispatcher

```java
enum TurnPhase {
    PREPARE,       // Initialize loop state, build first prompt
    INVOKE_MODEL,  // Call model, collect thinking
    EVALUATE,      // Decide next step based on response
    EXECUTE_TOOLS, // Guard, execute, checkpoint (one round of tools)
    REPAIR,        // Apply retry/repair logic
    FINALIZE,      // Assemble final message, persist, audit
    DONE           // Terminal state
}
```

**Valid transitions:**
```
PREPARE      -> INVOKE_MODEL
INVOKE_MODEL -> EVALUATE
EVALUATE     -> EXECUTE_TOOLS (if response has tool calls)
EVALUATE     -> REPAIR         (if final response or errors)
EXECUTE_TOOLS -> EVALUATE      (tool results available)
EXECUTE_TOOLS -> REPAIR        (ToolUseAbort detected)
EXECUTE_TOOLS -> FINALIZE      (plan/task completed mid-execution)
REPAIR       -> INVOKE_MODEL   (retry with control message)
REPAIR       -> FINALIZE       (repairs done or retries exhausted)
REPAIR       -> {throw}        (context too large)
FINALIZE     -> DONE
```

### 2. Single-Responsibility Handlers

Each handler is a private method returning a `TurnOutcome`:

```java
sealed interface TurnOutcome {
    record Continue(TurnPhase nextPhase, TurnContext ctx, ChatResponse modelResponse) {}
    record Retry(TurnPhase nextPhase, TurnContext ctx, Prompt retryPrompt,
                 Message controlMessage, ChatResponse newModelResponse, String reason) {}
    record Finalize(TurnPhase nextPhase, TurnContext ctx, AssistantMessage message,
                    String forcedQuestion) {}
    record TerminalFailure(String reason, Throwable cause) {}
}
```

Handler methods:
- `preparePhase()` — assemble prompt, set context
- `invokeModelPhase()` — call model, extract thinking
- `evaluatePhase()` — decide: tool calls → EXECUTE, empty → REPAIR, completion → FINALIZE
- `executeToolsPhase()` — one round: guard → execute → checkpoint → detect completion
- `repairPhase()` — try each repair in order: abort → empty response → plan turn → execution completion
- `finalizePhase()` — persist, audit, build response

### 3. Typed Outcome Model for Retries

Centralized in REPAIR handler with explicit precedence:
1. ToolUseAbort (highest priority)
2. Empty final response (thinking-only)
3. Plan turn repair (missing terminal tool call)
4. Execution completion repair (missing plan_complete/task_complete)
5. No more repairs → finalize (with forced planning question if needed)

Each retry has a finite counter (limit 2).

### 4. Diagnostic Markers

```java
record TurnDiagnostic(
    String conversationId, TurnPhase phase, long startNanos, Long durationNanos,
    OutcomeKind outcomeKind, Map<String, Object> details
)
```

Emitted at DEBUG level at each phase transition. Details include: toolCallCount, hasText, tokenUsage, contextCompact, guardErrorCount, repairReason, retryNumber. In production these are off unless DEBUG is enabled for `ChatService`.

---

## Implementation Steps

### Step 0: Create characterization test harness
**File:** `src/test/java/.../ToolLoopFlowTest.java`

Write tests locking every branch BEFORE extraction:
1. Normal multi-turn tool interaction (tool calls + final answer)
2. Each retry path when conditions persist across limits
3. ToolLoopGuard abort interleaving with retry paths
4. Post-loop fallback (forced planning question after repair exhaustion)
5. PlanToolExecutionContext cleared in finally

### Step 1: Extract `TurnContext` and `RepairState` records
**Files to create:**
- `src/main/java/.../ai/chat/service/turn/TurnContext.java`
- `src/main/java/.../ai/chat/service/turn/RepairState.java`

`RepairState` holds retry counters with `increment*()` and `canRetry*()` methods.

### Step 2: Mechanical method extraction (no behavioral change)
Extract `toolChat()` into these private methods, one at a time, testing after each:
1. `collectToolTranscripts()` — tool execution → transcript entries → messages
2. `processInterrupts()` — poll and process active turn interrupts
3. `executeOneToolRound()` — inner tool-call iteration body
4. `applyRepairs()` — four retry paths returning a RepairDecision
5. `finalizeTurn()` — all termination logic

### Step 3: Introduce the step model
Rewrite `toolChat()` with the phase dispatcher. The extracted methods from Step 2 become case bodies.

### Step 4: Make retry conditions explicit
In the REPAIR handler, retry logic is centralized with clear precedence (abort > empty > plan > execution).

### Step 5: Add diagnostic markers
Wire `TurnDiagnostic` throughout the dispatcher. Guard behind `logger.isDebugEnabled()`.

### Step 6: Verify ToolLoopGuard determinism
**File:** `src/test/java/.../ToolLoopGuardDeterminismTest.java`
Run 100 iterations with same input sequence, verify same abort index each time.

### Step 7: Integration verification
Run all tests and manual verification checklist (normal chat with tools, PLAN mode, TASK mode, all abort paths, streaming path).

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Tool-call ordering regression | HIGH | HIGH | Characterization tests verify exact message counts, types, order |
| Plan/task completion detection timing | HIGH | HIGH | TurnOutcome flag preserves the exact break-before-next-call timing |
| ThreadLocal leak | MEDIUM | HIGH | try/finally with PlanToolExecutionContext.clear() on all exit paths |
| Diagnostic overhead in production | LOW | LOW | DEBUG-level gated; off by default |

Rollback: All new files are additive. Refactored `toolChat()` is the only changed method. Keep original as comment during transition. All characterization tests pass before and after.

## Validation

- Execute branch-level characterization tests for all retry and termination paths.
- Verify determinism test for identical tool-call sequences.
- Run full suite: `mvn test`.

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `alpha-milestone-gate-summary.md`: "The 200+ line `toolChat` loop is a 'monster' method that is difficult to reason about and test."
- `alpha-milestone-gate-summary.md`: "Refactor the `toolChat` loop into a state-machine or chain-of-responsibility pattern."
- `code-quality-and-smells-report.md`: "`toolChat` ... handling tool execution, thinking extraction, repair logic, compaction notices, and multiple retry mechanisms."
- `code-quality-and-smells-report.md`: "`ChatService.java` | Cyclomatic Complexity (especially in `toolChat`) | Major."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, focused tool-loop test output, and proof of full-suite execution.

Validation sub-agent prompt:
```text
You are validating the Tool Loop Complexity remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/02-tool-loop-complexity.md`, then manually inspect the implementation diff around `ChatService.toolChat`, `ToolLoopGuard`, `ToolUseAbort`, and all new turn-loop classes. Do not trust the implementer's claimed phase model or test pass without checking the code.

Validation contract:
- Confirm the loop is expressed through explicit phases or equivalent typed outcomes with finite retry counters.
- Confirm retry precedence remains abort, empty final response, plan-turn repair, execution-completion repair.
- Confirm `ToolLoopGuard` behavior is unchanged and determinism is proven by tests.
- Confirm `PlanToolExecutionContext` is cleared on every normal, error, abort, and cancellation path.
- Confirm diagnostics are debug-gated and do not alter production behavior.

Return findings first, ordered by severity, with file/line references and any missing branch coverage.
```

Manual work proof to verify:
- Inspect the final loop control flow rather than trusting class names.
- Compare pre/post message ordering in characterization assertions.
- Verify focused `ChatServiceTest`, `ToolLoopGuardTest`, new tool-loop tests, `mvn test`, and startup smoke output.

---

## Exit Criteria

1. `ChatService.toolChat()` uses the phase dispatcher with explicit `TurnPhase` steps
2. All retry conditions handled by `TurnPhase.REPAIR` with typed `TurnOutcome.Retry` outcomes
3. Each phase handler is under 80 lines
4. Diagnostic markers emitted at each transition (DEBUG level)
5. All existing `ChatServiceTest` and `ToolLoopGuardTest` tests pass
6. All new `ToolLoopFlowTest` characterization tests pass
7. `ToolLoopGuard` determinism verified
8. Full test suite passes: `mvn test`
9. `ToolLoopGuard` behavior unchanged (no modifications to ToolLoopGuard.java or ToolUseAbort.java)
10. `PlanToolExecutionContext` cleared on all exit paths

## Critical Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — primary refactoring target
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ToolLoopGuard.java` — must remain deterministic, not modified
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ToolUseAbort.java` — not modified
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — existing tests must pass
