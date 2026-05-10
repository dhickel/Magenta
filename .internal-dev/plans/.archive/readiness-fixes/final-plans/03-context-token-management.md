# 03 -- Context and Token Management Reliability

## Context (What Is Broken, Why)

Token estimation in Magenta depends on a custom rendering pipeline that diverges from how providers account for tokens. The Spring AI `JTokkitTokenCountEstimator` (a single generic BPE tokenizer) is applied to Magenta-rendered text -- a flat string concatenation with role prefixes like `"user: ..."` and `"assistant: ..."` -- rather than to the structured message payload the provider actually receives. This creates systematic drift:

1. **Rendering divergence**: `ContextManagementAdvisor.renderConversation()` (line 596) produces text that does not match the Spring AI serialization sent to the provider API.

2. **Generic tokenizer for heterogeneous models**: The single `JTokkitTokenCountEstimator` is used for all model types -- Ollama-hosted (Qwen, Gemma, Llama), OpenAI-compatible, and DeepSeek. JTokkit diverges by 20-40% for Llama/Gemma models served through Ollama.

3. **Compaction is summary-heavy**: The `compact()` method (line 349) summarizes ALL older messages into a single monolithic summary. Over long sessions, critical context (plan state, task constraints, latest tool outputs) is treated identically to small-talk.

4. **No traceability**: When messages are retained, summarized, or dropped, there is no metadata recording the decision.

## Goal

Make token budgeting and context compaction reliable and explainable across providers without losing critical execution context in long-running conversations.

## In Scope

- Provider-aware token counting strategy with conservative fallbacks.
- Preflight budget guardrails and overflow protection.
- Deterministic compaction policy with trace metadata.

## Out of Scope

- New external chat APIs or UX behavior changes unrelated to compaction.
- Full long-term memory subsystem redesign.
- Non-chat persistence migrations beyond minimal trace additions.

## Current Architecture

### Token Counting Flow

```
ChatBeanConfig.tokenCountEstimator() → JTokkitTokenCountEstimator (generic BPE)
  → ContextManagementAdvisor.estimateTokens(messages) [line 625-627]
    → tokenCountEstimator.estimate(renderConversation(messages))
  → estimateUsage(messages, remoteModelName) [line 187-193]
  → estimateStoredUsage(conversationId, remoteModelName) [line 135-143]
```

### All Token Estimation Entry Points (13 call sites)

| Caller | File:Line | Purpose |
|--------|-----------|---------|
| `estimateTokens()` | CMA:625 | Core estimation |
| `estimateUsage()` | CMA:187 | Budget computation |
| `estimateStoredUsage()` | CMA:135 | Stored messages + system prompt |
| `preparePrompt()` | CMA:218,227,236 | Pre-send budget check |
| `maintainStoredContext()` | CMA:154,161,170 | Post-turn maintenance |
| `prepareToolLoopPrompt()` | CMA:275,285,295,304,313 | Tool loop checkpoints |
| `adviseCall()` | CMA:108 | Post-response audit |
| `compact()` | CMA:385 | Post-compaction audit |
| `trimToBudget()` | CMA:410 | Post-trim audit |
| `retainedTail()` | CMA:432 | Tail segment computation |

### Compaction Logic

**Trigger**: `usage.usedTokens() > usage.triggerTokens()` where `triggerTokens = maxTokens - (maxTokens * bufferPercent / 100)`

**Cascade** (in order):
1. `compact()` (line 349-389): Summary of older messages, keep tail
2. `trimToBudget()` (line 392-415): Remove from front iteratively
3. `IllegalStateException` (line 177-181): Hard failure if still over trigger

**What gets kept**: Hidden summary messages, compaction notices (preserved always). Tail segment (last N messages within maxTailTokens). First 6 messages if total ≤ 6.

**What gets summarized/dropped**: All messages before tail (except summaries/notices). Messages from front during trim (except summaries/notices).

---

## Target Architecture

### 1. Provider-Aware Token Counting Strategy

**Interface:**
```java
public interface TokenCountingStrategy {
    int estimate(List<Message> messages);
    boolean usesProviderTokenizer();
    double safetyMargin();
    String tokenizerName();
}
```

**Implementations:**
- **`OpenAiCompatibleTokenCountingStrategy`**: JTokkit with model-specific encoding (gpt-4 → cl100k_base, deepseek → cl100k_base). Safety margin: 1.0. Renders messages as JSON matching wire format.
- **`OllamaTokenCountingStrategy`**: Character-count heuristic `ceil(textChars / 3.5)`. Safety margin: 1.15.
- **`ConservativeFallbackTokenCountingStrategy`**: `ceil(textChars / 2.5)`. Safety margin: 1.30. Logs warning on first use.

**Strategy Registry:**
```java
@Component
public class TokenCountingStrategyRegistry {
    private final Map<EndpointType, TokenCountingStrategy> strategies;
    public TokenCountingStrategy forModel(String remoteModelName);
}
```

### 2. Safety Margin and Overflow Guards

**`BudgetGuard` component:**
```java
public class BudgetGuard {
    enum OverflowStatus { OK, NEEDS_COMPACTION, WOULD_OVERFLOW }
    record OverflowCheck(OverflowStatus status, int estimatedTokens, int maxTokens,
                         int triggerTokens, int hardLimit, String detail) {}
    
    OverflowCheck preflightCheck(List<Message> messages, String remoteModelName);
    void assertWithinBudget(List<Message> messages, String remoteModelName);
}
```

Safety margin applied: `adjustedEstimate = rawEstimate * strategy.safetyMargin()`. Hard limit: 95% of maxTokens.

### 3. Improved Compaction Policy

**Message Priority Tiers:**
```java
enum MessagePriority {
    CRITICAL, // Active plan state, task instructions, unresolved constraints, safety guards
    HIGH,     // Latest 2 turns of tool results, assistant decisions, explicit user instructions
    MEDIUM,   // Regular conversation (eligible for summarization)
    LOW       // Simple acknowledgments, small-talk (first to drop)
}
```

**`MessageClassifier`**: Deterministic classification based on content, role, position.

**Revised compact() flow:**
1. Classify all messages (deterministic)
2. Extract and preserve previous summaries (CRITICAL)
3. Select tail: last N messages, biased toward CRITICAL/HIGH
4. Group older messages by priority: CRITICAL retained in full, HIGH included with emphasis markers, MEDIUM summarized, LOW dropped entirely
5. Generate structured summary: narrative + key decisions + active constraints + unresolved items + tool result digest
6. Assemble: [CRITICAL retained] + [structured summary] + [compaction notice] + [tail]

**Revised trimToBudget():** Remove in priority order: LOW first, then MEDIUM from front. Never remove CRITICAL or HIGH. If only protected messages remain, throw `TokenBudgetExceededException`.

### 4. Compaction Trace Metadata

```java
record CompactionMetadata(
    String conversationId, Instant timestamp,
    List<MessageTrace> messageTraces,
    int preCompactionTokenCount, int postCompactionTokenCount,
    String compactionModelUsed, String tokenizerUsed
) {
    record MessageTrace(int messageIndex, String role, MessagePriority priority,
                        CompactionAction action, String reason) {}
}
```

Stored in audit repository as JSON, logged at DEBUG level.

### 5. Deterministic Compaction

Compaction decisions are deterministic; only summary text varies:
```
classify(messages)          // deterministic
selectTail(messages)        // deterministic  
identifyToDrop(messages)    // deterministic
identifyToSummarize(messages) // deterministic
summary = summarize(toSummarize) // non-deterministic (but doesn't affect what's kept)
assemble(kept, summary, tail)    // deterministic assembly
```

All deterministic steps are unit-testable with fixed inputs.

---

## Implementation Steps

### Phase 1: Token Counting Strategy (foundation)
- **Step 1.1**: Create `TokenCountingStrategy` interface and three implementations
- **Step 1.2**: Create `ModelWireFormatRenderer` for wire-format message rendering
- **Step 1.3**: Create `TokenCountingStrategyRegistry` mapping EndpointType → Strategy
- **Step 1.4**: Update `ChatBeanConfig` to wire new beans

### Phase 2: Safety Guards
- **Step 2.1**: Create `BudgetGuard` with `preflightCheck()` and `assertWithinBudget()`
- **Step 2.2**: Create `TokenBudgetExceededException`
- **Step 2.3**: Integrate `BudgetGuard` into `ContextManagementAdvisor` (replace all direct `estimateTokens()` calls)
- **Step 2.4**: Update `ChatBeanConfig` to wire `BudgetGuard`

### Phase 3: Compaction Policy Upgrade
- **Step 3.1**: Create `MessagePriority` enum
- **Step 3.2**: Create `MessageClassifier` with deterministic classification rules
- **Step 3.3**: Create `CompactionMetadata` and `MessageTrace` records
- **Step 3.4**: Revise `compact()` to use priority tiers and structured summary
- **Step 3.5**: Create structured summary prompt (format: Summary, Key Decisions, Active Constraints, Unresolved Items, Tool Result Digest)
- **Step 3.6**: Revise `trimToBudget()` to respect priority tiers
- **Step 3.7**: Revise `retainedTail()` to use `BudgetGuard` and respect CRITICAL messages
- **Step 3.8**: Update tool loop compaction methods
- **Step 3.9**: Update `AuditRepository` for structured compaction traces (new `compaction_trace_json` column or separate table)

### Phase 4: Backward Compatibility and Wiring
- **Step 4.1**: Update all callers, verify no breaking changes
- **Step 4.2**: Finalize `ChatBeanConfig` wiring
- **Step 4.3**: Verify advisor contract (`CallAdvisor`/`StreamAdvisor`) unchanged

---

## Validation

### Token Budget Tests
- `BudgetGuard.preflightCheck` with safety margin application
- Token estimation per endpoint type
- Wire format renderer determinism
- Budget integration: compaction triggers at right threshold, post-compaction below trigger

### Compaction Determinism Tests
- `MessageClassifier` determinism (same input → same classifications)
- Compaction control flow determinism (classification + tail + trim order identical)
- Tail selection respects CRITICAL messages and budget
- Trim ordering: LOW before MEDIUM, CRITICAL/HIGH never removed

### Regression Tests
- All existing `ContextManagementAdvisorTest` tests pass
- Compaction notice format unchanged
- Summary prefix format unchanged
- ChatService integration unchanged

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `alpha-milestone-gate-summary.md`: "Custom token estimation may diverge from model provider reality, leading to context overflows."
- `alpha-milestone-gate-summary.md`: "Summarization-based context compaction may lose critical technical details over long sessions."
- `code-quality-and-smells-report.md`: "`ContextManagementAdvisor.estimateTokens` relies on a custom string representation of the conversation (`renderConversation`)."
- `code-quality-and-smells-report.md`: "`ContextManagementAdvisor` uses summarization to compact context" and "critical technical details ... may be lost."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, token-budget test output, compaction test output, and any audit/schema changes.

Validation sub-agent prompt:
```text
You are validating the Context and Token Management remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/03-context-token-management.md`, then manually inspect `ContextManagementAdvisor`, token strategy classes, budget guard code, compaction logic, audit/schema changes, and tests. Do not trust the implementer's summary of provider-aware behavior without verifying call sites.

Validation contract:
- Confirm token counting is provider-aware or uses documented conservative fallbacks with margins.
- Confirm all model-call preflight paths use the budget guard before sending prompts.
- Confirm compaction preserves active plan/task state, unresolved constraints, recent tool outputs, summaries, and notices as specified.
- Confirm classification, tail selection, and trim order are deterministic and unit-tested.
- Confirm compaction trace metadata is persisted or explicitly recorded in audit with no breaking public advisor API.

Return findings first, ordered by severity, with file/line references and any unguarded token-estimation call site.
```

Manual work proof to verify:
- Inspect all `estimateTokens`, `preparePrompt`, `prepareToolLoopPrompt`, and `maintainStoredContext` call sites.
- Verify tests cover OpenAI-compatible, Ollama, unknown-model fallback, hard-limit overflow, and protected-message compaction.
- Verify focused context tests, `mvn test`, and startup smoke output.

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| JTokkit encoding mismatch for new models | Medium | Medium | Fallback to generic cl100k_base with safety margin |
| Structured summary parse failure | Low | Medium | Include raw text alongside structured; fallback if parse fails |
| Safety margin causes premature compaction (Ollama) | Medium | Low | Tunable margin; start at 1.15, adjust with data |
| MessageClassifier misclassifies critical context | Medium | High | Exhaustive unit tests; start conservative (more MEDIUM) |
| Audit table schema migration conflict | Low | Medium | Additive schema (new column, not restructure) |

Rollback: Feature toggle `useProviderAwareTokenCounting: boolean` defaults to `false`. Gradual rollout: deploy off → enable for OpenAI → enable for all → remove fallback.

---

## Exit Criteria

1. All existing tests pass: `mvn test`
2. Provider-aware counting operational (OpenAI → JTokkit with model-specific encoding, Ollama → character heuristic with 1.15 margin, Unknown → conservative fallback with 1.30 margin)
3. Feature toggle works: can disable provider-aware counting
4. `BudgetGuard.preflightCheck()` runs before every model call, hard limit (95%) never exceeded
5. Compaction preserves high-signal context: CRITICAL messages never summarized/dropped, previous summaries always carried forward
6. CompactionMetadata trace records all decisions
7. Structured summary includes key decisions, constraints, unresolved items, tool result digest
8. Compaction deterministic: classification, tail selection, trim ordering are pure functions
9. No breaking API changes to `ContextManagementAdvisor` public methods
10. Audit trail covers new behavior: trace queryable by conversation ID

## Critical Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java` — Primary target
- `src/main/java/io/mindspice/magenta2/ai/chat/config/ChatBeanConfig.java` — Wiring for all new beans
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java` — Tool result rendering consistency
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java` — Existing tests
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java` — Schema extension
