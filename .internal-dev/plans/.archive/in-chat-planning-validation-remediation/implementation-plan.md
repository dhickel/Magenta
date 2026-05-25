# In-Chat Planning Validation Remediation

## 1. Objective

Validate and harden the anonymous `/chat` planning flow end to end from the browser using Playwright. The target user workflow is: open `/chat`, enter `/plan`, answer planning questions for a detailed research/report task, approve the generated plan, execute it, and confirm validator/file evidence behavior through browser state, backend logs, and SQLite state.

The immediate goal is to fix any error that blocks planning question submission, then continue far enough to determine whether the previously reported validation/file-visibility failure still exists. If validation fails for a real product reason, iterate with focused fixes and validation gates until the flow reaches a terminal trusted state or an explicit, documented `NEEDS_REVIEW` state with correct evidence.

## 2. Inputs And Assumptions

Confirmed inputs:

- User reported console/backend errors when sending `/chat` planning question responses.
- User specifically requested Playwright browser validation with console and database monitoring.
- User requested subagent orchestration, separate validation/fix loops, commits, and final email.
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` is the binding browser workflow guide.
- `/chat` anonymous planning must remain session-local and separate from saved `/plans`.

Assumptions to verify:

- Local `config/ai-config.example.json` is present, ignored by git, valid JSON, and contains working credentials.
- The app can run on `http://localhost:18080` with an isolated SQLite database.
- Playwright MCP or an approved browser fallback can interact with the live app.
- The user-provided research topic is used as a planning/report scenario, not as a requirement to add cannabis-specific product behavior.

## 3. Scope

In scope:

- Browser-origin validation of `/chat` planning via `/plan`.
- Console/network capture during the planning flow.
- Backend log and SQLite inspection for plan state, chat memory, audit events, file rows/paths, and validation feedback.
- Fixes in chat planning, UI state, API error handling, execution validation, file visibility, or model-routing code when directly implicated.
- Focused tests, full `mvn test`, bounded Spring Boot startup, Playwright screenshots, `.internal-dev` closeout, and commits after completed phases.
- Final AgentMail summary to Dwight.

Out of scope:

- Saved `/plans` planning chat unless evidence shows the anonymous flow is accidentally crossing into saved-plan code.
- Broad operational UI sweeps beyond surfaces required to validate this incident.
- Generic cannabis research feature work. The scenario is a validation prompt for planning/report execution.
- New orchestration/job/workflow abstractions.

## 4. Current-State Analysis

Relevant code:

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/*`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/static/css/magenta.css`

Relevant data:

- `ai_chat_memory`
- `ai_chat_plan_definitions`
- `ai_chat_plan_steps`
- `audit_event`
- chat file directory under `<dataRoot>/chats/<conversationId>/files`

Known recent behavior:

- Planning answers now persist before a failed continuation model call returns a controlled response.
- The browser question card routes answers through the main chat input and posts to `/api/chat/{conversationId}/plan/answers`.
- Execution validation uses `plan_complete`, explicit evidence, artifact paths, and validator feedback.

## 5. Target Design

The final behavior should satisfy:

- `/plan` enters anonymous planning mode and queues a visible prompt card.
- Answer submission consumes exactly the intended question, persists the user answer, and either queues the next question, marks the draft ready for approval, or returns a controlled recoverable model failure.
- The browser must not emit unexpected JavaScript exceptions, duplicate submissions, stale-question races, or invisible 400/500 failures during the flow.
- Approval and execution must preserve visible history and persisted plan state.
- Execution validation must see reported artifacts/files when the model provides valid paths and must fail with clear validation feedback when evidence is incomplete.
- Any validator failure must be visible in `ChatPlanState`, audit events, and persisted history without reverting the UI to draft planning.

## 6. Implementation Plan

### Phase 0: Orchestration Setup

- Create shared notes at `.codex-orchestration/in-chat-planning-validation/notes.md`.
- Create this plan suite.
- Start validation from a clean branch and clean worktree.

### Phase 1: Browser Reproduction Agent

Launch one non-mutating validation agent with Playwright. It owns:

- Starting or using a live app on `http://localhost:18080` with an isolated SQLite database.
- Running `/chat` planning flow with the research/report scenario.
- Capturing console messages, network failures, screenshots, server output tail, and database state.
- Reporting the first blocker with exact reproduction steps and evidence.

The validation prompt should use this scenario text, adjusted only to avoid policy or model refusal loops:

```text
Research Blue Star Seed Co F13 backcross as a historical/community report. Focus on forum user experiences, consensus reports about growing characteristics, and the older F13 lineage context, including parent-line reputation and differences from newer stock. Produce a deep report with cited forum/community evidence and avoid generic seed-bank marketing copy.
```

### Phase 2: First Blocking Fix

If Phase 1 finds a blocking UI/API/backend/database error:

- Reproduce locally in the smallest possible focused test.
- Patch only the implicated layer.
- Add focused regression coverage.
- Run the focused test and commit the fix.

Likely targets:

- `chat-client.js` question state, error parsing, stale `questionIndex`, or double-submit controls.
- `ChatController.answerPlanPrompt(...)` status mapping.
- `ChatService.submitPlanAnswer(...)` model failure or plan-state handling.
- `PlanService.recordPromptAnswer(...)` stale/no-active-question semantics.

### Phase 3: Full Planning And Execution Validation

Run validation again after the blocking fix:

- `/plan`
- answer all queued questions
- approve
- execute through the browser stream path
- reload history and inspect persisted state
- inspect database rows and chat file directory

Acceptance states:

- `COMPLETED` with trusted final message and expected evidence/artifacts, or
- `NEEDS_REVIEW` with clear validation feedback and no draft-planning regression.

`EXECUTING` stuck state, missing file evidence after valid `plan_report`, hidden 500s, or UI reset are failures.

### Phase 4: Validation/File-Visibility Remediation

If execution validation cannot see files that were created or reported:

- Inspect `plan_report` audit/tool rows, chat file directory, artifact path normalization, `PlanCompletionService` artifact carry-forward, and validator prompt assembly.
- Add a regression test that creates/reports the relevant artifact path and proves validator input receives it.
- Patch the narrow path handling or prompt assembly bug.
- Re-run focused tests, full chat planning tests, and browser validation.
- Commit the fix.

### Phase 5: Closeout And Final Validation

- Run `mvn test`.
- Run bounded Spring Boot startup smoke.
- Run final Playwright validation with screenshots of the changed/relevant `/chat` surfaces.
- Update docs for any changed behavior.
- Add `.internal-dev/changelogs/` entry and any bug/knowledge/focus updates required by findings.
- Commit closeout artifacts.
- Send AgentMail summary to Dwight.

## 7. Validation Plan

Required validation gates:

- Focused unit/integration tests for every code fix.
- `mvn test`.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.
- Browser validation through Playwright or an explicitly documented browser fallback if MCP is blocked.
- Console/network capture must show no unexpected JavaScript exceptions or 500 responses.
- SQLite inspection must verify plan rows, chat memory, audit events, validation feedback, and file/artifact state for the tested conversation.
- Screenshots of `/chat` prompt card, approval/execution state, and terminal result/review state.

## 8. Handoff Checklist

- [ ] Shared notes updated after every agent pass.
- [ ] Browser reproduction evidence captured.
- [ ] First blocker fixed or documented as external configuration.
- [ ] Validation/file visibility issue reproduced or cleared.
- [ ] All code fixes covered by tests.
- [ ] Docs and `.internal-dev` closeout completed.
- [ ] Each completed phase committed separately.
- [ ] Final email sent.
