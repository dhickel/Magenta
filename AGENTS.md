## `.internal-dev` Development Document Store

`.internal-dev/` is the persistent engineering document store for plans, bugs, changelogs, reviews, notes, and reusable knowledge.

### Required workflow
- After each feature implementation or non-trivial fix, complete the full `.internal-dev` workflow: write a changelog entry, record any out-of-scope bugs discovered, capture reusable knowledge, and note deferred ideas.
- Plans and reviews are written to `.internal-dev/plans/` and `.internal-dev/reviews/`.
- Out-of-scope bugs found during work are logged immediately in `.internal-dev/bugs/`.
- Finalized work gets a changelog entry in `.internal-dev/changelogs/`.
- Reusable insights go to `.internal-dev/knowledge/`.
- Deferred future ideas go to `.internal-dev/notes/` after confirming they are out of scope.
- Move finalized bug/plan artifacts to sibling `.archive/` directories.

### Controlled access
- Do not read `.internal-dev` broadly by default.
- Read only the files required for the active task.

### Reference guide
- Process and templates: `.internal-dev/AGENTS.md`




## Magenta Project Guide

Magenta is an assistant agent and manager life-helper. It is intended to run on a remote host, expose APIs and a web portal, and help users through chat, notes, reminders, task handoff, tool-assisted work, and eventually coordinated subagents.

This project is currently a Spring Boot and Spring AI application with SQLite-backed chat memory, REST/SSE chat endpoints, a simple browser chat surface, and external AI/agent configuration.

### Engineering style
- Keep code straightforward, simple, readable, and focused on the domain problem at hand.
- Prefer lean Spring and modern Java patterns over broad enterprise layering.
- Do not add features, abstractions, tools, API surface, queues, schedulers, or orchestration machinery only because they may be useful later.
- Build the smallest complete thing that solves the current task cleanly.
- If a scope expansion or future-facing capability seems important, pause and raise it with the user before adding it.
- Keep names plain and behavior easy to trace from controller to service to repository.

### Current architecture expectations
- Web/API entry points live under `io.mindspice.magenta2.api`.
- AI chat behavior lives under `io.mindspice.magenta2.ai.chat`.
- User-editable AI and agent configuration lives under `io.mindspice.magenta2.ai.config.user`.
- Shared core utilities live under `io.mindspice.magenta2.core`.
- Controllers should stay thin and delegate behavior to services.
- Services should own use-case behavior and avoid persistence or transport details leaking into callers.
- Repositories should own persistence details and keep schema assumptions localized.
- Request/response payloads and internal data carriers should use Java records where practical.

### Agent and tool direction
- Treat Magenta as an operational assistant, not a generic framework.
- Add tool, reminder, note, file, calendar, automation, or subagent behavior only for a concrete user-facing workflow.
- Keep agent orchestration bounded, observable, cancellable, and easy to reason about when it is introduced.
- Prefer explicit configuration and small interfaces over hidden global behavior.

### Package guides
- Production Java packages with an `AGENTS.md` define local ownership and conventions for that package.
- Read the closest package guide before changing code in that package.
- Keep package guides updated whenever a change alters that package's domain responsibility, public surface, or local conventions.

### Validation expectations
- After nontrivial code changes, run the relevant automated tests.
- Before considering backend or application-wiring work complete, smoke test that the Spring Boot application context starts successfully. Prefer a bounded startup command such as `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` unless the task has a more specific startup path.
- For live chat, browser, SSE, agent/model routing, planning, interruption, chat switching, or concurrent-interaction validation, use the Playwright MCP workflow documented in `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`. Read that file before running this class of test, follow its MCP-first approach, and update it when you discover better methods, new gotchas, or changed endpoint behavior.
- If startup cannot be run because required local services or secrets are unavailable, report that explicitly with the blocking dependency.
