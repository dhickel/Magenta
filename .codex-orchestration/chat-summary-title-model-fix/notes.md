# Chat Summary And Title Model Fix Notes

## Global Assumptions
- User wants implementation performed by subagents.
- Keep code-editing work serial.
- Report concurrency/name-request findings directly in chat; no durable review artifact requested for that investigation.

## Active Agents
- Concurrency review subagent: inspecting whether CONVERSATION_TITLE jobs run asynchronously relative to chat requests and later session use.
- Implementation subagent: serial code-editing owner for summary/compaction naming config and title model routing.

## Completed Work
- Implemented `summaryModel` with legacy `summeryModel` fallback.
- Routed compaction through the effective compaction model, falling back to summary model.
- Added `deepseek-flash-v4-zero` DeepSeek model with `thinkLevel: 0` and made it the example `summaryModel`.
- Routed newly enqueued `CONVERSATION_TITLE` jobs to the effective summary model instead of the selected chat model.
- Updated docs, tests, changelog, reusable knowledge, and focus decision.

## Validation Results
- `mvn -q -DskipTests compile` passed.
- `mvn -q -Dtest=RuntimeSettingsServiceTest,ExternalAiConfigLoaderTest,AiUserConfigConfigurationTest,ContextManagementAdvisorTest,AgentJobServiceTest test` passed.
- `mvn -q -Dtest=ExternalAiConfigLoaderTest,ContextManagementAdvisorTest,AgentJobServiceTest,AiUserConfigConfigurationTest,MagentaRootConfigurationTest,RuntimeSettingsServiceTest test` passed.
- `mvn test` passed with 691 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached started Spring Boot context on an ephemeral port, then exited via timeout 124 after graceful shutdown.

## Remediation Notes
- Concurrency review finding preserved below: title job work is asynchronous on the background lane after the chat turn completes, but `submitBackground` can still throw synchronously if the background lane is full.
- Main integration pass corrected runtime compaction fallback so blank runtime compaction follows the effective runtime summary model.

## Blockers
- None.

## Closeout Work
- Added `.internal-dev/changelogs/2026-05-22-summary-title-model-routing.md`.
- Added `.internal-dev/knowledge/summary-title-model-selection.md`.
- Updated `.internal-dev/focus/decisions.md`.

## Final Validation Status
- Passed.

## Handoff Notes
- Concurrency review: title jobs are created only for first-turn/new conversations (`RequestResolver.resolve` sets `newConversation=true`; `AuditService.enqueueTitleJobIfFirstTurn` gates on `newConversation && titleJobEligible`) and are submitted at end-of-turn (`ChatService` plain stream `doOnComplete`, tool path completion, and plain chat path).
- Execution mode: `AgentJobService.submitConversationTitle` enqueues DB row then calls `MagentaWorkExecutor.submitBackground(...BACKGROUND_JOB...)`; `runConversationTitleJob` executes on background lane and updates metadata/status, so title generation is not awaited by chat response generation.
- Isolation/limits: chat turns run through `ConversationTurnCoordinator` + `submitChat` lane; title jobs use separate background lane (`magenta.executor.background-threads` default 1, queue 100). So no per-conversation turn lock coupling between chat turns and title jobs.
- Constraints: partial unique index `idx_agent_jobs_conversation_title_active` prevents >1 active/succeeded CONVERSATION_TITLE per conversation; failed jobs can be retried. `saveTitleIfAbsent` prevents overwriting manual/existing title.
- Caveat: `submitBackground` can throw `RejectedExecutionException` synchronously when background lane capacity is full; `submitConversationTitle` does not catch it, so enqueue side-effect can fail request completion path even though job work itself is async.
- Frontend: client polls session list for `titleJobStatus`/title after stream completion (up to 8 attempts x 750ms) and does not block chat interaction beyond normal in-flight request handling.

- Implemented: summaryModel typo fix with legacy summeryModel compatibility, compaction model selection fallback behavior, summary-model-only conversation title generation, and DeepSeek Flash v4 zero-thinking example config as summary model.
- Validation: Focused tests passed: ExternalAiConfigLoaderTest, ContextManagementAdvisorTest, AgentJobServiceTest, AiUserConfigConfigurationTest, MagentaRootConfigurationTest.
- Notes: Adjusted impacted test fixture constructor usage for AiConfig compatibility.
