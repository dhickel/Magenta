# Scope

Consolidated alpha milestone review for Magenta. This report merges four completed specialist agent reviews plus local code inspection:

- robustness and gotchas
- quality, cohesion, and contracts
- simplification/refactor targets
- architecture flow mapping
- local alpha readiness/security pass

The focus is backend MVP completeness, with UI findings included where they affect alpha exposure risk.

# Findings

## Executive Assessment

Magenta's backend core is close to alpha-MVP shape. The core flows exist and have real persistence:

- chat sessions, memory, model routing, context usage, compaction, and audit
- model-backed plan mode and saved-plan execution
- reusable task definitions, drafts, runs, task completion enforcement, and run evidence
- linear workflow runs over task executions
- durable orchestration assignments, jobs, job items, inbox, schedules, reactions, events, leases, and checkpoints
- runtime settings, agent profiles, workspace metadata, file/shell/web tool plumbing

The main concern is not missing feature breadth. It is alpha hardening around operational edges and boundaries. The codebase is beginning to concentrate too much behavior in controllers and `ChatService`, and a few security/cancellation issues should block alpha exposure.

## Blockers Before Alpha Exposure

1. Tracked config secret/defaults: remove and rotate the apparent API key in `config/ai-config.example.json`, stop loading an example config as the default runtime config, and make tool/shell defaults least-privilege.
2. Authentication boundary: add mandatory auth/authorization before remote deployment; current write/execution APIs are exposed without an apparent Spring Security boundary.
3. `web_fetch` redirect SSRF: validate final redirect targets or disable redirects.
4. Shell security/cancellation: destroy child processes and reader tasks on interruption/cancel, and ensure runtime shell allowlist revocation takes effect.
5. Agent side-panel policy: selected-agent chat must use the selected agent's prompt, approved tools, and shell policy.
6. Task/workflow frontend interpolation: fix raw `innerHTML`/attribute injection before exposing those pages.
7. Runtime settings wiring: ensure context management honors runtime settings, especially compaction model and context buffer.
8. Streaming state semantics: distinguish client disconnect, model failure, timeout, user cancel, and execution validation failure.

## High-Value Hardening Before/Alongside Alpha

1. Add request validation and stable 400/404/409 responses across controllers.
2. Decide and test SQLite schema ownership and foreign-key behavior.
3. Prevent duplicate orchestration assignment submissions while queued work waits for executor slots.
4. Move workflow stream execution off the request thread.
5. Add tests for the high-risk edges listed in the readiness report.

## Architecture Risks If Not Addressed Soon

1. Controllers are no longer consistently thin, especially for stream orchestration.
2. `ChatService` is a broad coordinator for chat, plan, task, tool, audit, title job, and model policy.
3. `PlanMode` is now a shared chat interaction mode but remains in the plan package.
4. Public APIs expose internal domain/persistence records directly.
5. Schema creation is split between `schema.sql` and repository bootstrapping.

## Refactor Targets By Priority

`P0`: frontend interpolation fixes if UI ships.

`P1`: runtime settings context wiring; SSE support extraction; `ChatService` seam extraction; schema ownership; shell structured command input; workflow alpha decision.

`P2`: dead chat command handlers; unused `Option`/`DataService`; typed stream event payloads; audit sequence robustness.

`P3`: typed schedule/event reaction templates unless alpha-facing.

# Risk Assessment

Overall alpha risk is moderate but concentrated. The product flows are implemented enough to test with real usage, but alpha exposure should wait until the blocker list is handled. The most serious risks are security boundary issues in tools, resource cleanup during cancellation, and durable state inconsistency during streaming errors.

The system's architecture is still recoverable. The recommended cleanup is targeted, not a rewrite. The key is to stop adding new capabilities until stream semantics, tool boundaries, settings wiring, and schema policy are tightened.

Reviewer E validation found that `mvn test` currently passes with 171 tests and a bounded Spring Boot startup smoke reached healthy Tomcat startup on an isolated SQLite database. That supports continuing toward alpha after the blockers are fixed; it does not reduce the severity of the security/tooling blockers.

# Recommendations

1. Create an alpha blocker branch focused only on the blockers listed above.
2. Add regression tests as each blocker is fixed; do not rely only on manual smoke.
3. After blocker fixes, run `mvn verify` and a bounded Spring Boot startup smoke.
4. Use Playwright MCP for live chat/SSE/interrupt validation after stream changes.
5. Defer broad `ChatService` refactoring until blockers are fixed, then split it incrementally along current seams.
6. Decide whether workflows/schedules/reactions are alpha-facing or experimental. Hide experimental surfaces rather than half-hardening them.
7. Adopt a minimal migration/version ledger or a clear schema ownership policy before preserving alpha user data.

# Follow-ups

- Convert these review findings into an alpha checklist with owners/status.
- File individual bug artifacts for any blocker that will not be fixed immediately.
- Update package guides when ownership changes are made.
- Re-run this review after blocker fixes and before tagging alpha.
