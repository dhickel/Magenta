# 06 -- Controller Boundary and Prompt Ownership

## Context (What Is Broken, Why)

Prompt composition logic for the agent chat feature lives inline in `AgentOrchestrationController` (web layer), rather than in a service within the chat/orchestration domain. This creates entry-point divergence risk: if a new agent chat entry point is added (webhook, CLI, scheduled task, programmatic API), the prompt construction must be duplicated, leading to inconsistent behavior.

Currently, this is the ONLY controller that assembles prompt text; all other controllers correctly delegate to services. The agent chat feature has a specific prompt format (`"Agent page context: <pageContext>\n\n<message>"`) that encodes important context about where in the UI the agent was invoked. This context resolution and prompt assembly is business logic, not web-layer concern.

**Risk of not fixing:** Future developers adding agent interaction paths would need to discover and replicate the prompt assembly convention. If missed, the agent would receive messages without the page context prefix, resulting in degraded response quality from the LLM.

## Goal

Move agent-chat prompt composition out of the web layer into a dedicated domain service so prompt behavior is consistent across entry points.

## In Scope

- Extracting prompt construction from `AgentOrchestrationController`.
- Adding a focused prompt-assembly service with parity tests.
- Preserving current prompt text format and defaults.

## Out of Scope

- Redesigning prompt content strategy for all chat modes.
- Refactoring unrelated controllers.
- Introducing generic prompt abstraction layers.

---

## Current Architecture

### Inventory: Every prompt construction location in the codebase

**Controller layer (web) -- THE PROBLEM:**

| File | Lines | What it does |
|------|-------|-------------|
| `api/web/AgentOrchestrationController.java` | 194-197 | Constructs agent chat prompt: `"Agent page context: " + pageContext + "\n\n" + message` with default pageContext of `"orchestration page"` |
| `api/web/AgentOrchestrationController.java` | 198 | Resolves model: `request.model() ?? agent.defaultModel()` |
| `api/web/AgentOrchestrationController.java` | 240-253 | `donePayload()` private method that creates `ChatRequest.MsgRequest` with the assembled prompt and calls `chatService.chat()` |

**Controller layer (web) -- NO prompt construction (correct):**

| File | Behavior |
|------|----------|
| `api/web/ChatController.java` | Passes message verbatim, delegates model resolution to `RequestResolver`, delegates all streaming to `ChatService` |
| `api/web/TaskController.java` | No prompt construction; delegates to `TaskStreamSupport` and `OrchestrationRunService` |
| `api/web/WorkflowController.java` | No prompt construction; delegates to `WorkflowStreamSupport` and `OrchestrationRunService` |
| Other controllers | Pure CRUD, no prompt logic |

**Service layer -- correct pattern (reference for new service):**

| File | Lines | What it does |
|------|-------|-------------|
| `ai/chat/service/ChatService.java` | 128-134 | Hardcoded prompt constants: `EXECUTE_PLAN_MESSAGE`, `EXECUTE_TASK_MESSAGE`, `BEGIN_PLAN_MESSAGE` |
| `ai/chat/service/ChatService.java` | 1203-1211 | `currentInstructions()` assembles system prompt + user message into `List<Message>` |
| `ai/chat/service/ChatService.java` | 1535-1559 | `effectiveSystemPrompt()` composes system prompt from runtime settings + plan/task instructions |

### Divergence Analysis

There are **four distinct prompt pathways** with intentionally different prompt structures:

| Entry Path | Endpoint | Prompt sent to LLM |
|-----------|----------|-------------------|
| Chat | `POST /api/chat/stream` | Raw user message (verbatim) |
| Agent chat | `POST /api/agents/{agentId}/chat/stream` | `"Agent page context: <context>\n\n<message>"` |
| Plan execution | `POST /api/chat/{id}/plan/execute/stream` | `"Execute the saved plan now..."` (constant) |
| Task execution | `POST /api/tasks/{id}/runs/stream` | `"Execute the reusable task now..."` (constant) |

These are NOT "same prompt built differently" -- they are legitimately different prompt patterns for different features. The problem is not divergence of prompt content but divergence of **where the prompt is assembled**:
- Plan/task prompts are assembled in `ChatService` (correct, domain layer)
- Agent chat prompt is assembled in `AgentOrchestrationController` (incorrect, web layer)

---

## Target Architecture

### New Service: `AgentChatPromptService`

**Package:** `io.mindspice.magenta2.ai.chat.service`

**Responsibility:** Owns prompt assembly for agent-initiated chat interactions.

**Design principles:**
- Keep it explicit and small -- a single strategy, not an abstract builder
- Own only the message wrapping, not model resolution or conversation management
- Mirror the pattern used by `ChatService` for plan/task prompt constants
- Place in `ai.chat.service` because it is a chat-domain concern

**Interface:**
```java
@Service
public class AgentChatPromptService {
    static final String DEFAULT_PAGE_CONTEXT = "orchestration page";

    public String buildPrompt(String pageContext, String message) {
        String effectiveContext = (pageContext == null || pageContext.isBlank())
            ? DEFAULT_PAGE_CONTEXT
            : pageContext;
        return "Agent page context: " + effectiveContext + "\n\n" + message;
    }
}
```

### Updated Controller: `AgentOrchestrationController`

**Before (current):**
```java
// Lines 194-197 (inline prompt construction)
String pageContext = request.pageContext() == null || request.pageContext().isBlank()
    ? "orchestration page"
    : request.pageContext();
String prompt = "Agent page context: " + pageContext + "\n\n" + message;
```

**After (delegated to service):**
```java
String prompt = agentChatPromptService.buildPrompt(request.pageContext(), message);
```

### Strategy Mapping

| Use Case | Prompt Strategy | Location |
|----------|----------------|----------|
| Agent chat (all orchestration pages) | `AgentChatPromptService.buildPrompt()` | `ai.chat.service` |
| Direct chat | Raw message (verbatim) | `ChatService` / `RequestResolver` |
| Plan execution | `EXECUTE_PLAN_MESSAGE` constant | `ChatService` |
| Task execution | `EXECUTE_TASK_MESSAGE` constant | `ChatService` |

---

## Implementation Steps

### Step 1: Create `AgentChatPromptService`

**File to create:** `src/main/java/io/mindspice/magenta2/ai/chat/service/AgentChatPromptService.java`

A Spring `@Service` with one method `buildPrompt(String pageContext, String message)`. Extract the exact prompt format from `AgentOrchestrationController` line 197 and the default context from lines 194-196.

### Step 2: Create `AgentChatPromptServiceTest`

**File to create:** `src/test/java/io/mindspice/magenta2/ai/chat/service/AgentChatPromptServiceTest.java`

| Test | Input | Expected Output |
|------|-------|----------------|
| Normal case | pageContext="task editor", message="review this" | `"Agent page context: task editor\n\nreview this"` |
| Null pageContext | pageContext=null, message="hello" | `"Agent page context: orchestration page\n\nhello"` |
| Blank pageContext | pageContext="  ", message="hello" | `"Agent page context: orchestration page\n\nhello"` |
| Multiline message | pageContext="agents", message="line1\nline2" | `"Agent page context: agents\n\nline1\nline2"` |
| Golden test | pageContext="workflow editor", message="Inspect this task" | Exact current output (character-by-character match) |

### Step 3: Update `AgentOrchestrationController`

**File to edit:** `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`

1. Add `AgentChatPromptService` as a constructor dependency
2. Replace inline prompt construction on lines 194-197 with `agentChatPromptService.buildPrompt()`

### Step 4: Update `AgentOrchestrationControllerTest`

**File to edit:** `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`

Add `AgentChatPromptService` dependency to all test constructor calls. For the `agentChatStreamEmitsStartAndDone` test: the assertion `chatService.request.message().contains("Agent page context: task editor")` still works because the controller passes the service-produced prompt to `ChatService`.

### Step 5: Verify no other code paths affected

- `AgentChatPromptService` is only used by `AgentOrchestrationController`
- No other controller, service, or test references page context or agent chat prompt construction

---

## Validation

### Unit Tests for `AgentChatPromptService`
7+ unit tests covering null/blank defaults, multiline messages, special characters, and a golden test for exact output preservation.

### Prompt Equivalence Tests (cross-entry-path)
Parameterized test covering all 8 known pageContext values from `FrontendController`: `"task editor"`, `"workflow editor"`, `"dashboard"`, `"agents"`, `"agent-detail"`, `"jobs"`, `"job-detail"`, null (defaults to `"orchestration page"`).

### Controller-Level Tests (existing tests updated)
All existing `AgentOrchestrationControllerTest` tests pass with the new dependency.

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `architectural-alignment-report.md`: "A minor concern is the presence of prompt-building logic within `AgentOrchestrationController.chat()`."
- `architectural-alignment-report.md`: "This logic should ideally reside in a `PromptService` or within the `AgentProfile` domain."
- `architectural-alignment-report.md`: "Refactor Prompt Construction: Move prompt assembly logic out of `AgentOrchestrationController` and into a service."
- `alpha-milestone-gate-summary.md`: "Prompt Construction: Logic is currently leaked into controllers; should be moved to a dedicated service."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, prompt service tests, and controller test output.

Validation sub-agent prompt:
```text
You are validating the Controller Boundary and Prompt Ownership remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/06-controller-boundary-prompt-ownership.md`, then manually inspect `AgentOrchestrationController`, the new prompt service, and tests. Do not trust the implementer's summary that prompt format was preserved without checking exact strings.

Validation contract:
- Confirm `AgentOrchestrationController` no longer assembles agent chat prompt text inline.
- Confirm prompt construction lives in a domain service with no web transport dependencies.
- Confirm exact current prompt output is preserved for null, blank, known page contexts, multiline messages, and special characters.
- Confirm direct chat, plan execution, and task execution prompt paths are not accidentally changed.
- Confirm no broad prompt abstraction was introduced beyond the stated small service.

Return findings first, ordered by severity, with file/line references and any prompt-equivalence gap.
```

Manual work proof to verify:
- Inspect controller diff for removal of inline string assembly.
- Verify golden tests compare exact prompt strings character-for-character.
- Verify focused prompt tests, `AgentOrchestrationControllerTest`, `mvn test`, and startup smoke output.

---

## Risk Assessment and Rollback Strategy

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| Prompt format changes inadvertently | Low | High | Golden test captures exact output; parameterized test covers all page contexts |
| Test breakage from constructor signature change | Medium | Low | Tests updated in same commit; `AgentChatPromptService` has no-arg constructor for stubs |
| Over-abstraction | Low | Low | Single-method service with no interface/strategy pattern; explicit and concrete |

Rollback: Fully reversible -- revert commit. Only `AgentOrchestrationController.java` modified (adding dependency, changing 4 lines). Two new files can be deleted or left in place. No API contract, database schema, or configuration changes.

---

## Exit Criteria

1. `AgentChatPromptService.java` created in `ai.chat.service` package with single `buildPrompt` method
2. `AgentChatPromptServiceTest.java` created with 7+ unit tests including golden test and parameterized context test
3. `AgentOrchestrationController.java` updated: inline prompt construction replaced with service call
4. `AgentOrchestrationControllerTest.java` updated: all constructor calls pass `AgentChatPromptService` dependency
5. All existing tests pass (`mvn test`)
6. Prompt equivalence proven: golden test output matches current inline prompt output character-for-character
7. Parameterized test covers all 8 known pageContext values

## Critical Files for Implementation

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/RequestResolver.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
