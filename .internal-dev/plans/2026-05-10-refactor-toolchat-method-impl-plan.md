---
title: "Refactor toolChat method Implementation Plan"
design_ref: "docs/maestro/plans/2026-05-10-refactor-toolchat-method-design.md"
created: "2026-05-10T12:00:00Z"
status: "approved"
total_phases: 2
estimated_files: 2
task_complexity: "complex"
---

# Refactor toolChat method Implementation Plan

## Plan Overview
- **Total phases**: 2
- **Agents involved**: `refactor`, `code_reviewer`
- **Estimated effort**: High. Extracting deeply nested logic into a state machine dispatcher while maintaining exact feature parity.

## Dependency Graph
```text
[Phase 1: Refactor toolChat implementation]
          |
          v
[Phase 2: Code review of new dispatcher]
```

## Execution Strategy
| Stage | Phases | Execution | Agent Count | Notes |
|-------|--------|-----------|-------------|-------|
| 1     | Phase 1 | Sequential | 1 | Core dispatcher implementation |
| 2     | Phase 2 | Sequential | 1 | Architectural and safety review |

## Phase 1: Refactor toolChat implementation
### Objective
Implement the `ToolLoopState` object and `LoopPhase` dispatcher to replace the `toolChat` monolithic loop.

### Agent: refactor
### Parallel: No

### Files to Modify
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — Rename existing `toolChat` to `legacyToolChat`. Implement `ToolLoopState` (inner class), `LoopPhase` enum, and the new `toolChat` dispatcher method with `handleModelCall`, `handleToolExecution`, etc.
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — Add specific unit tests to exercise the dispatcher phases if applicable, or ensure existing tests pass against the new structure.

### Implementation Details
1. Create `private static final class ToolLoopState` to hold mutable context (`continueModelLoop`, `emptyFinalResponseRetries`, `messagesToPersist`, etc.).
2. Create `private enum LoopPhase { MODEL_CALL, TOOL_CALL, TOOL_CHECKPOINT, ABORT_RECOVERY, COMPLETION }`.
3. Create `toolChat` dispatcher with a `while (continueLoop)` containing a `switch (currentPhase)`.
4. Migrate code segments from the legacy loop into separate methods for each phase.

### Validation
- Run existing unit tests: `./mvnw test -Dtest=ChatServiceTest`
- Ensure compilation succeeds: `./mvnw clean compile`

### Dependencies
- Blocked by: None
- Blocks: 2

---

## Phase 2: Code review of new dispatcher
### Objective
Perform an architectural and security review of the newly implemented dispatcher against the design requirements and the legacy method to ensure zero logic drift.

### Agent: code_reviewer
### Parallel: No

### Files to Modify
None

### Implementation Details
Analyze the changes in `ChatService.java`. Compare the transitions in the new dispatcher to the edge cases in `legacyToolChat`.
Check specifically for:
- Missing updates to the audit service.
- Differences in retry logic.
- Leaked context between phases.

### Validation
- Present findings report.

### Dependencies
- Blocked by: [1]
- Blocks: None

---

## File Inventory
| # | File | Phase | Purpose |
|---|------|-------|---------|
| 1 | `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` | 1 | Dispatcher implementation |
| 2 | `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` | 1 | Unit tests for dispatcher |

## Risk Classification
| Phase | Risk | Rationale |
|-------|------|-----------|
| 1     | HIGH | Core chat loop. High cyclomatic complexity. |
| 2     | LOW | Read-only analysis phase. |

## Execution Profile
```text
Execution Profile:
- Total phases: 2
- Parallelizable phases: 0 (in 0 batches)
- Sequential-only phases: 2
- Estimated parallel wall time: N/A
- Estimated sequential wall time: 10-15 minutes

Note: Native subagents currently run without user approval gates.
All tool calls are auto-approved without user confirmation.
```