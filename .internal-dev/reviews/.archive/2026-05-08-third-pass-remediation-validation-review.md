# Third-Pass Remediation Validation Review

## Scope

Validation of `.internal-dev/plans/readiness-fixes/third-pass-remediation-plan.md` after implementation. Scope is limited to the third-pass contract: side-panel SSE first event timing, stream abort cleanup, deterministic task/workflow stream events, disabled alpha persistence assertions, full tests, startup smoke, and browser evidence.

## Findings

No high or medium findings remain open.

| Issue | Status | Evidence |
|---|---|---|
| Side-panel SSE emits `start` before model work blocks | closed | `AgentOrchestrationControllerTest.agentChatStreamFlushesStartBeforeChatServiceCompletes`; browser fallback saw `start -> done` for `/api/agents/{agentId}/chat/stream` |
| Side-panel blank/unsupported responses use SSE `error` | closed | `agentChatStreamEmitsErrorForBlankMessage`, `agentChatStreamEmitsErrorForUnsupportedChatResponse` |
| Task/workflow/side-panel send failures clean up without dropped Reactor errors | closed | `SseStreamLifecycle.trySendSseEvent`; `TaskControllerTest.streamRunCompletesQuietlyWhenClientSendFails`; `WorkflowControllerTest.streamRunCompletesQuietlyWhenClientSendFails`; server log grep found no `onErrorDropped`, `ResponseBodyEmitter has already completed`, `broken pipe`, or `AsyncRequestNotUsableException` |
| Browser-visible task stream starts promptly | closed | `TaskControllerTest.streamRunFlushesStartedBeforeTaskExecutionCompletes`; fallback browser probe saw first `started` and intentionally aborted after it |
| Browser-visible workflow stream starts promptly | closed | `WorkflowStreamSupport` emits pre-run events; `WorkflowControllerTest.streamRunReturnsBeforeWorkflowExecutionCompletesAndEmitsTerminalStatus`; fallback browser probe saw first `started` and intentionally aborted after it |
| Disabled schedules create no firing rows | closed | `OrchestrationDurableRuntimeTest.disabledSchedulesDoNotFireOrCreateAssignments` asserts zero `schedule_firings` rows for the disabled schedule |
| Disabled reactions mark events handled without enqueuing | closed | `OrchestrationDurableRuntimeTest.disabledReactionsDoNotEnqueueAssignments` asserts no matching assignment and non-null `handledAt` on the inbox event |
| Browser validation evidence | closed | Playwright MCP attempted first and blocked by `mcp-chrome-4e05678` profile lock. Isolated Chromium CDP fallback on `http://localhost:18081` passed `/chat`, chat SSE `start/context/chunk/done`, assignment path-agent behavior, side-panel SSE, task/workflow prompt events, disabled routes, and log cleanliness |

Validation commands:

- `mvn test -Dtest=AgentOrchestrationControllerTest,SseStreamLifecycleTest` - passed, 33 tests.
- `mvn test -Dtest=TaskControllerTest,WorkflowControllerTest` - passed, 23 tests.
- `mvn test -Dtest=OrchestrationDurableRuntimeTest` - passed, 10 tests.
- `mvn test` - passed, 312 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-third-pass-remediation-smoke.sqlite --app.ai.config-path=/tmp/magenta2-third-pass-config/ai-config.json'` - reached Tomcat on port `40333`, then exited `124` by timeout.

Browser fallback evidence:

- App: `http://localhost:18081`, isolated SQLite `/tmp/magenta2-third-pass-browser.sqlite`.
- Browser: isolated Chromium CDP profile `/tmp/magenta2-cdp-profile`.
- Model: local OpenAI-compatible stub on `127.0.0.1:19000`.
- Result file: `/tmp/magenta2-third-pass-browser-results.json`.
- Log check: `/tmp/magenta2-third-pass-browser.log` contained no matching dropped-error or broken transport stack traces after intentional aborts.

## Risk Assessment

Residual risk is low. The remaining difference from production is that browser model responses were served by a deterministic local OpenAI-compatible stub rather than a real local Ollama model. The validation still exercised the browser, same-origin fetch, Spring controllers, SSE parsing, database-backed agent/task/workflow setup, and client-abort transport cleanup.

The default startup command without an overridden AI config remains blocked in this worktree because `./config/ai-config.example.json` is malformed JSON. This is environmental/config state, not a regression from the third-pass changes.

## Recommendations

Keep `SseStreamLifecycle.trySendSseEvent()` as the standard for stream endpoints that may see client aborts. Avoid throwing from Reactor `onNext` for transport failures.

Use the incremental SSE browser parser for future stream validation. It avoids treating an intentionally long-lived SSE response as a timeout failure when the test only needs first-event visibility and clean abort behavior.

## Follow-ups

None required for the third-pass remediation contract.
