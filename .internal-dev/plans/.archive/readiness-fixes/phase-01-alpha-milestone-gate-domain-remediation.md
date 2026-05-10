# Context

This plan consolidates issues from:

- `.internal-dev/reviews/alpha-milestone-gate/alpha-milestone-gate-summary.md`
- `.internal-dev/reviews/alpha-milestone-gate/architectural-alignment-report.md`
- `.internal-dev/reviews/alpha-milestone-gate/code-quality-and-smells-report.md`
- `.internal-dev/reviews/alpha-milestone-gate/e2e-and-stability-report.md`
- `.internal-dev/reviews/alpha-milestone-gate/security-and-performance-report.md`

The most urgent remediation theme is `ChatService`: oversized ownership, complex tool loop flow, and helper methods with weak names that manipulate prompts/context in ways that are hard to trace safely.

# Goal

Create a high-confidence remediation roadmap grouped by domain/target that reduces regression risk, restores traceable chat-flow ownership, and hardens stability/performance concerns identified in the alpha gate reviews.

# In Scope

- Chat flow architecture and naming cleanup centered on `ChatService`.
- Tool-loop complexity reduction and testability improvements.
- Context/token management reliability.
- Streaming lifecycle consistency and async execution correctness.
- High-risk memory/performance hotspots in web/file tools.
- Controller/service boundary cleanup where prompt/context logic is misplaced.
- Durable runtime state planning for restart resilience.

# Out of Scope

- Net-new product features or major architecture rewrites outside reviewed findings.
- Authentication/authorization redesign.
- Broad security hardening beyond items explicitly surfaced in the alpha gate review set.
- Switching persistence technology or introducing a full migration framework in this phase.

# Implementation Steps

## 1. Domain: Chat Turn Orchestration (`ChatService` core)

Targets:
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- Adjacent collaborators in plan/task/context/audit/tool orchestration.

Cause:
- `ChatService` accumulated orchestration, transport-adjacent streaming behavior, prompt policy decisions, tool execution, repair logic, and persistence coordination.
- Many helper methods are behavior-heavy but weakly named, so intent is opaque and call chains are difficult to reason about.

Fix approach:
- Introduce an explicit turn pipeline with named stages and a shared `TurnContext` carrier (record/class) for stage inputs/outputs.
- Extract responsibility seams first by behavior, not by arbitrary file size:
  1. `PromptContextAssembler` (system prompt + mode-specific prompt composition)
  2. `ToolAccessPolicy` (approved tools + mode filters)
  3. `ToolTurnEngine` (tool call loop orchestration)
  4. `TerminalTurnRepair` (finalization guarantees for PLAN/TASK)
  5. `TurnAuditWriter` (audit/event persistence boundaries)
- Rename mini-functions around context/prompt mutation to intent-revealing verbs (`mergeModePrompt`, `applyCompactionNotice`, `enforceTerminalToolCall`, etc.) and require each to document pre/post-conditions.
- Keep existing external API/SSE contract stable while internals are split.

Gotchas / senior notes:
- Do not start by splitting into many classes at once. First lock behavior with characterization tests around the existing flow, then extract one seam at a time.
- Preserve current event semantics and planning/task terminal behavior exactly during refactor; this is where regressions hide.
- Avoid moving persistence logic into controllers while decomposing; keep service boundary intact.

## 2. Domain: Tool Loop Complexity and State Safety

Targets:
- `ChatService.toolChat` (or equivalent loop method after extraction)
- `ToolLoopGuard` interactions and retry/repair hooks

Cause:
- The loop currently handles unrelated concerns in one method (tool dispatch, thinking extraction, compaction signals, retries, terminal repair), which obscures state transitions.

Fix approach:
- Replace monolithic loop flow with an explicit step model (`PREPARE -> INVOKE_MODEL -> HANDLE_TOOLS -> APPLY_REPAIRS -> TERMINATE`) and single-responsibility handlers per step.
- Make retry conditions explicit and finite with typed outcomes (`RETRYABLE_TOOL_ERROR`, `VALIDATION_FAILURE`, `TERMINAL_OK`).
- Emit structured diagnostic markers per step for easier defect triage.

Gotchas / senior notes:
- Resist “generic state machine framework” scope creep. A small local enum + dispatcher is enough.
- Ensure loop guard decisions remain deterministic under identical tool-call sequences.

## 3. Domain: Context and Token Management Reliability

Targets:
- `src/main/java/io/mindspice/magenta2/ai/chat/context/ContextManagementAdvisor.java`
- Token estimation helpers and runtime-settings plumbing

Cause:
- Token estimation depends on custom rendering that may diverge from provider accounting.
- Compaction is summary-heavy and can drop high-value constraints over long sessions.

Fix approach:
- Introduce provider-aware token counting strategy (provider tokenizer where available, conservative fallback otherwise).
- Add safety margin thresholds and overflow guards before provider call.
- Shift compaction policy to preserve pinned/high-signal artifacts (active plan/task state, unresolved user constraints, latest tool outputs) while summarizing low-signal history.
- Add trace metadata for why each message was retained, summarized, or dropped.

Gotchas / senior notes:
- Token drift never goes fully to zero; design for bounded error and predictable fallback behavior.
- Keep compaction deterministic for testability; nondeterministic summarization should not alter control flow.

## 4. Domain: Streaming Lifecycle and Async Boundaries

Targets:
- Chat/task/workflow/orchestration SSE endpoints and stream lifecycle helpers

Cause:
- Streaming paths are generally functional but lifecycle/error behavior is uneven and timeout assumptions are implicit.
- Workflow/task streaming can regress into request-thread coupling if not consistently handled.

Fix approach:
- Standardize stream outcome contract (`progress`, `complete`, `failed`, `cancelled`, `timeout`, `disconnect`) and apply across all SSE endpoints.
- Ensure emitter/subscription lifecycle cleanup is centralized and always reached on terminal paths.
- Guarantee long-running execution is async relative to servlet request threads.
- Encode known LLM startup latency allowances into tests (30s-class timeout where required by model behavior).

Gotchas / senior notes:
- Keep transport disconnect separate from domain failure in persisted run status.
- Confirm no endpoint blocks waiting for stream completion before returning emitter.

## 5. Domain: Resource Safety in Tool I/O (OOM hot spots)

Targets:
- `AgentWebToolService.fetch`
- `AgentFileToolService.replace` and large-content paths

Cause:
- Current implementations read full payloads into memory before truncation/replacement decisions.

Fix approach:
- Add pre-read size guards (when `Content-Length` is present) and stream with bounded buffers.
- Truncate during read rather than after full allocation.
- For file replace flows, gate full-buffer operations by file size and use streaming transforms for large files.

Gotchas / senior notes:
- Preserve existing user-visible truncation semantics and encoding behavior.
- Ensure partial-read paths are clearly labeled in tool output so users know content was bounded.

## 6. Domain: Controller Boundary and Prompt Ownership

Targets:
- `AgentOrchestrationController` chat prompt assembly
- Other controllers with embedded prompt composition logic

Cause:
- Prompt composition logic appears in controller flow, creating entry-point divergence risk and inconsistent behavior across interfaces.

Fix approach:
- Move prompt construction into dedicated service(s) owned by chat/orchestration domain.
- Keep controllers thin: validate request, call service, return DTO/stream.
- Add tests that prove equivalent prompt context regardless of entry path.

Gotchas / senior notes:
- Avoid over-abstracting prompt builders; keep a small number of explicit prompt strategies mapped to concrete use cases.

## 7. Domain: Durable Runtime State for Orchestration

Targets:
- Assignment/run state handling in orchestration runtime services/repositories

Cause:
- Current behavior is operationally strong but restart resilience is partially dependent on in-memory state assumptions.

Fix approach:
- Define minimum durable checkpoints for assignment progression and recovery.
- Ensure lease/checkpoint transitions are idempotent and restart-safe.
- Add restart simulation tests validating resume semantics.

Gotchas / senior notes:
- Favor small, explicit persistence transitions over complex recovery orchestrators.
- Validate behavior on both clean startup and mid-run restart scenarios.

# Validation

- Characterization tests for existing `ChatService` behavior before decomposition.
- Focused unit/integration tests per extracted component in Domains 1-3.
- SSE endpoint tests for standardized outcome semantics and non-blocking return behavior.
- Performance safety tests for bounded web/file reads and truncation correctness.
- Startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.
- Full regression pass: `mvn test`.

# Exit Criteria

- `ChatService` no longer owns unrelated responsibilities directly; orchestration seams are extracted and named by intent.
- Tool-loop control flow is stage-based, finite, and covered by targeted tests.
- Context/token behavior has provider-aware counting or conservative bounded fallback with explicit safeguards.
- SSE lifecycle outcomes are consistent across chat/task/workflow/orchestration surfaces.
- Web/file tool paths cannot allocate unbounded payloads in normal fetch/replace operations.
- Controller prompt assembly logic is centralized in service-level ownership.
- Durable orchestration recovery expectations are defined and validated by restart-focused tests.
