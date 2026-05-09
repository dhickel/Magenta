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


### Frontend (SimplyPages)
The frontend makes use of SimplyPages, SimplyPages is our on self maintained frontend library for server side rendering it includes/focuses on:
- HTMX simplicity over JS
- Modularity via Components which can be composed into modules
- A simple opinioned grid based layout
- A default/exam chat implementation
- A default forum implementation
- Demos showing how to use components and do advanced operations

You can gain alot about the frontend library from the demos, complex views like the forum and and editing menus give examples on how to do modal outputs and node/card rendering
This is the style we want to take with our UI.

#### SimplyPages Links
- Documentation `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`
- Demo `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo`

*Always use the libraries coding style and practices, do not try to shoehorn functionality or use raw html strings, raw html is a fallback for advanced cases most functionality from css, js, htmx
can be done via functions. The library has a vast set of components and ways to make your own, search the well formated documentation for your operation and read it before any edits, if
still faced with ambiguity, or needing context refer to the demos, if still confused DO NOT DO AD-HOC HACKISH WORKAROUND CONSULT THE USER.*

*Given the modularity of the library, much of our ui can be reused components across pages, always try to reuse and generalize when similar functionalities exit*

*Keep in mind threading, we have slot keys that allow us to re use the same instance of components/modules where static data is pre-rendered, then we can render with dynamic data. While this isn't a performance
bottleneck for us, it is one of the library patterns to keep in mind, and can avoid instancing multiple objects that will be converted to strings anwy-ways. Remember though you must use slot keys when sharing render objects between requests.*

#### When encountering bug/issues with SimplyPages
SimplyPages is develop and maintained by use, any issues and/or bug that are found can be directly address by use, if you find much needed missing functionality we can add it, if there is a bug we can fix it directly.
If you find yourself in a situation where the library cannot do something you reasonably expect decide if it is worth raising to an issue and file and document the issue on our github repository, you dont need to make a PR, but well document your ideas around addressing the issue, your justification, targets and scope.
If you find a bug pull the recent version of the library and directly implement a fix and file a pr.

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
