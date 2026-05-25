# Automated Validation Evidence

## Agent

- Agent: main Codex campaign coordinator
- Model / reasoning: current parent Codex session
- Scope: focused Maven tests, full Maven test suite, isolated startup, DB probes

## Commands and Results

| Probe | Command | Result |
| --- | --- | --- |
| Focused controller/UI tests | `timeout 180s mvn -q -Dtest=OperationalUiContractControllerTest,OrchestrationControllerTest,FrontendControllerTest test` | Passed. |
| Focused runtime/persistence tests | `timeout 180s mvn -q -Dtest=OrchestrationRuntimeTest,WorkflowRepositoryTest,WorkflowRunnerTest,WorkspaceLeaseServiceTest,PlanRepositoryTest,JobRepositoryTest,ProjectRepositoryTest test` | Passed. |
| Full Maven suite | `timeout 300s mvn test` | Passed: 444 tests, 0 failures, 0 errors, 0 skipped. |
| Clean SQLite startup | `timeout 35s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --spring.datasource.url=jdbc:sqlite:.../clean-startup.db?foreign_keys=true"` | Reached `Started Magenta2Application`; exited with code 124 due bounded timeout after graceful shutdown. |
| Warm SQLite startup | `cp chat-memory.db .../warm-db/chat-memory-warm-copy.db && timeout 35s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --spring.datasource.url=jdbc:sqlite:.../chat-memory-warm-copy.db?foreign_keys=true"` | Reached `Started Magenta2Application`; exited with code 124 due bounded timeout after graceful shutdown. |
| Clean DB table probe | `sqlite3 .../clean-startup.db ".tables"` | Clean startup produced current runtime tables plus `workspace_roots`/`inbox_messages`/`agent_inbox_messages` split. |
| Warm DB table probe | `sqlite3 .../chat-memory-warm-copy.db "select count(*) ..."` | Warm DB has 44 tables, including current tables and legacy `ai_*` tables. |
| Warm DB column probe | `pragma table_info(...)` for key tables | Warm DB has `work_assignments.last_progress_at/last_heartbeat_at`, workflow graph columns, workspace lease `release_requested`, and output attribution columns. |

## Evidence Notes

- Java baseline: OpenJDK `25.0.3`.
- Maven baseline: Apache Maven `3.9.11`.
- Playwright baseline: `npx playwright --version` returned `Version 1.59.1`.
- Startup smoke treated `124` as expected because the application reached healthy startup before the bounded timeout killed it.

## Findings

- Automated tests pass despite multiple review-discovered public-alpha blockers, which indicates coverage gaps around route-level direct-run semantics, workflow XSS, empty workflow submission, schema workspace-root migration behavior, and agent queue ownership checks.
