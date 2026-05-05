# Changelog: Model Routing Contract & Threading Robustness

## Date
2026-05-04

## Change Summary
Fixed three buggy call sites that hardcoded `ollamaOptions()` instead of using the endpoint-polymorphic router method, which would crash for non-Ollama models (DeepSeek, OpenAI-compatible). Added `chatOptions()` to `ChatModelRouter` as the single safe code path for endpoint-agnostic option construction. Made executor thread pools configurable with higher production defaults. Fixed streaming to not block executor threads. Consolidated executor systems — all background work now routes exclusively through `MagentaWorkExecutor`.

## Files

| File | Change |
|---|---|
| `ChatModelRouter.java` | Added `chatOptions()` endpoint-polymorphic method; documented `ollamaOptions()` as Ollama-only |
| `ChatService.java` | Fixed `prompt()` options call (`ollamaOptions`→`chatOptions`); replaced streaming `Flux.create`+`blockLast()` with per-conversation `Semaphore` serialization |
| `PlanCompletionService.java` | Fixed plan validator options call (`ollamaOptions`→`chatOptions`) |
| `ContextManagementAdvisor.java` | Fixed compaction summarizer options call (`ollamaOptions`→`chatOptions`) |
| `MagentaWorkExecutor.java` | Externalized thread pool config via `magenta.executor.*` properties; defaults: chat 8-thread/200-queue, delegation 2/100, background 1/100; made test constructor and `LaneSettings` public |
| `AgentJobService.java` | Removed fallback `agentJobTaskExecutor`; all jobs now use `MagentaWorkExecutor.submitBackground()` |
| `AgentJobConfig.java` | Removed `agentJobTaskExecutor` bean |
| `AgentJobServiceTest.java` | Updated to use `MagentaWorkExecutor` constructor; removed `SyncTaskExecutor` usage |
| `ContextManagementAdvisorTest.java` | Added `chatOptions()` and `toolCallingOptions()` overrides to `SummaryRouter` mock |

## Behavioral Impact
- **Non-Ollama models (DeepSeek, OpenAI-compatible) no longer crash** on the plain-chat path, plan completion validation, or context compaction. These paths were all calling `ollamaOptions()` which throws for non-Ollama models.
- **All 125 tests pass** with zero failures.
- **Application context starts** successfully.
- Streaming chat turns no longer consume an executor thread for their entire duration — per-conversation serialization is preserved via `Semaphore`.
- Thread pool sizes are now tunable: set `magenta.executor.chat-threads` etc. in `application.yml` or system properties.

## Risks
- **Sync/stream serialization gap**: synchronous `chat()` calls use `ConversationTurnCoordinator`, streaming calls use the new per-conversation `Semaphore`. The two mechanisms don't coordinate, so a sync call and a stream for the same conversation could theoretically run concurrently. In practice the UI never issues both simultaneously, but this should be unified in a follow-up.
- Thread pool defaults were increased (chat: 2→8 threads). If the host has limited CPU, adjust via properties.

## Follow-up Items
- Unify sync `chat()` and `stream()` serialization under a single per-conversation gate.
- Consider making `ollamaOptions()` / `ollamaOptionsBuilder()` package-private once tests no longer depend on them.
- Remove unused `AiConfig` field from `AgentJobService` (cleanup).
