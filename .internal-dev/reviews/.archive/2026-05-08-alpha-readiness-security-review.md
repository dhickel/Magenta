# Scope

Alpha readiness and operational review synthesized from the specialist robustness/cohesion/refactor passes plus local inspection. Focused on whether backend core is MVP-complete enough for alpha, test gaps, startup/config readiness, schema/data safety, observability, API consistency, and security boundaries for model-accessible tools.

# Findings

## Validation Status

Reviewer E ran the current automated suite and a bounded startup smoke:

- `mvn test` passed: 171 tests, 0 failures.
- Bounded `spring-boot:run` reached healthy Tomcat startup on an isolated SQLite DB; timeout exit `124` was expected after startup.

These are encouraging readiness signals, but they do not cover the security and operational blockers below.

## Alpha Blocker: Tracked Default Example Config Contains A Secret And Wildcard Tool/Shell Access

`application.yml` loads `./config/ai-config.example.json` by default (`application.yml:4-6`). That tracked example config includes an apparent DeepSeek API key at `config/ai-config.example.json:56`, enables web search at `:9-13`, approves all tools through `"approvedTools": ["*"]` at `:63-65`, and allows all shell commands through `"allowedShellCommands": ["*"]` at `:66-68`.

This blocks alpha until the key is rotated/removed, the example file is made non-secret, and default tool/shell posture is least-privilege.

## Alpha Blocker: Remote-Control APIs Have No Application Security Boundary

The app exposes high-impact write and execution APIs without an apparent Spring Security dependency or `SecurityFilterChain`: chat execution/deletion/interrupts/plan execution, runtime settings writes, agent profile mutation, assignments, schedules, event reactions, jobs, workspace link writes, and tool-capable chat flows. Examples include `ChatController.java:69`, `RuntimeSettingsController.java:27`, `AgentProfileController.java:36`, and `AgentOrchestrationController.java:86`.

For a remotely hosted assistant with shell/file/web tools, unauthenticated remote-control APIs are alpha-blocking. Even simple single-user auth is materially safer than no boundary.

## Alpha Blocker: Web Fetch Redirect SSRF Boundary

The web tool blocks private/local hosts on the initial URL, but follows redirects automatically. This must be fixed before alpha if `web_fetch` is approved for any agent. The model can be induced to fetch attacker-controlled public URLs, and the current implementation can follow those URLs to internal services.

## Alpha Blocker: Shell Tool Cancellation Cleanup

Shell execution is bounded by timeout but not robust against thread interruption. Since Magenta has cancellation/interrupt pathways and an executor capable of interrupting work, shell processes must be cleaned up on interruption before alpha if shell tools are enabled.

## Alpha Blocker If UI Is Exposed: Task/Workflow Frontend Interpolation

The task/workflow UI injects API-provided values into `innerHTML` and attributes. If alpha users can create task/workflow titles, field names, descriptions, examples, or bindings, this is a realistic browser injection risk.

## High: Runtime Shell Allowlist Revocation May Not Take Effect

`AgentShellToolService` snapshots `allowedCommands` and `allowAllCommands` at bean construction (`AgentShellToolService.java:30-50`) and checks those immutable fields in `exec` (`AgentShellToolService.java:70-72`). Runtime settings and agent profiles can later resolve different allowed shell commands (`RuntimeSettingsService.java:115-124`), but the shell service does not re-read policy at execution time.

This weakens runtime security controls: revoking shell access in settings may not revoke already-constructed shell tool behavior.

## High: Agent Side-Panel Chat Does Not Use The Selected Agent's Prompt/Tool Policy

`AgentOrchestrationController.chat` loads the selected agent and uses its default model when no model is supplied (`AgentOrchestrationController.java:145-156`), but then calls generic `ChatService.chat` (`:156-158`). `ChatService` derives prompt and approved tools from runtime/default agent state, not the selected side-panel agent (`ChatService.java:1589`, `ChatService.java:1685`).

This makes selected-agent boundaries misleading: the UI can appear to chat as one agent while executing with another prompt/tool policy.

## High: Streaming State Semantics Are Not Alpha-Crisp

Chat, plan execution, task execution, workflow execution, and agent chat streaming each have slightly different timeout, cancellation, and error behavior. The most important issue is not code duplication; it is ambiguous durable state after disconnects and transport errors.

## High: Runtime Settings May Not Be Fully Honored

`ChatBeanConfig` constructs `ContextManagementAdvisor` without `RuntimeSettingsService`, despite the advisor supporting it. Alpha users changing runtime settings may believe compaction/context behavior changed when it did not.

## Medium: Clean Install Versus Upgrade Schema Drift

`schema.sql` and repository `ensureSchema()` code diverge. Alpha deployment should have a clear story for clean database creation and upgrades. Today, core chat/task/workflow tables are in `schema.sql`; orchestration/settings/workspace/agent tables are repository-created; some columns are only repository-created.

## Medium: API Error Contracts Need Hardening

Several endpoints can still turn malformed input into 500s or inconsistent behavior. This is mostly controller validation and DTO design, not deep backend logic.

## Medium: Orchestration Cancellation Is Boundary-Based Only

Durable orchestration correctly checkpoints at assignment/job-item boundaries, but running model-backed task/workflow execution is not preempted by assignment pause/cancel except at coarse boundaries. This is acceptable for alpha only if documented and reflected in UI status wording.

## Medium: Test Coverage Is Good For Units, Thin For Live System Edges

There are focused tests for many services and controllers, but the high-risk alpha edges need additional tests:

- SSE disconnect/failure
- shell cancellation cleanup
- web fetch redirects
- runtime settings affecting context management
- clean database schema creation
- orchestration duplicate scheduling/backlog
- workflow stream cancellation
- invalid request bodies

# Risk Assessment

Backend core is approaching MVP completeness in the sense that the main product flows exist and are integrated:

- chat with model routing and memory
- plan mode and saved-plan execution
- reusable task definitions/drafts/runs
- linear workflows over tasks
- durable assignments/jobs/inbox/schedules/events
- runtime settings and agent profiles
- file/shell/web tools with confinement/allowlist concepts

The system is not yet alpha-safe by default. The blockers are specific and fixable, but important: remove/rotate tracked secrets, add an auth boundary, make high-risk tool defaults least-privilege, fix web fetch redirect validation, clean up shell interruption behavior, fix frontend interpolation if exposed, wire runtime settings correctly, and clarify stream cancellation/failure semantics.

# Recommendations

1. Remove and rotate the apparent tracked API key, split example config from local config, and default high-risk tools off.
2. Add mandatory auth before remote deployment, even if initially single-user.
3. Treat web fetch redirect validation and shell cancellation cleanup as backend alpha blockers.
4. Treat frontend task/workflow escaping as an exposure blocker if those pages ship in alpha.
5. Wire runtime settings into context management and shell allowlist evaluation before asking alpha users to trust runtime settings.
6. Add a selected-agent chat execution path that uses the selected agent's prompt, tools, and shell policy.
7. Add a clean-database startup/smoke test and decide schema ownership.
8. Document orchestration cancellation boundaries in the UI/API until stronger cancellation exists.
9. Add contract tests for malformed requests and consistent HTTP status mapping.
10. Keep workflows/schedules/reactions either explicitly experimental or hidden until their UX/API is intentionally alpha-ready.

# Follow-ups

- Run `mvn verify` and a bounded Spring Boot startup smoke after the blocker fixes land.
- Use the documented Playwright MCP live-chat workflow for SSE/chat/interrupt validation after stream semantics are changed.
- Create an alpha checklist that separates blockers, hardening tasks, and explicitly deferred experimental capabilities.
- Add security regression tests for unauthenticated write rejection, redirect-to-private-host rejection, shell allowlist revocation, selected-agent prompt/tool isolation, and SQLite upgrade from an older file.
