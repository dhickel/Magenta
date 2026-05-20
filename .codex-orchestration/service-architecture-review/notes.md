# Service Architecture Review Orchestration Notes

## Global Assumptions
- Review scope is non-mutating analysis of the current Magenta codebase and existing docs.
- Subagents may read relevant code, docs, SQL, tests, and package AGENTS.md files, but should not edit files unless explicitly assigned.
- Final artifacts should be deeply informative architecture/review documents under `.internal-dev/reviews/`.
- Existing unrelated worktree changes must be preserved.

## Active Agents
- Pending planning agent.

## Completed Work
- Created dedicated branch `architecture-service-review-orchestration`.
- Created shared orchestration notes.
- A1 Chat Context Audit completed: traced chat request/stream paths, prompt/context assembly, memory/audit/session persistence, active turn interrupts, terminal repair, model routing, title jobs, and rolling-summary storage. Detailed findings are in the A1 final response.

## Validation Results
- None yet.

## Remediation Notes
- None yet.

## Blockers
- None yet.

## Closeout Work
- Write final review artifact(s) under `.internal-dev/reviews/`.
- Record changelog/knowledge/notes only if the review workflow produces applicable finalized changes, reusable insights, or explicitly deferred ideas.
- Commit only intended orchestration/review artifacts if the final workflow requires it and the user has not redirected.

## Final Validation Status
- Not started.
- A11 Editorial Finalizer completed read-only review. Required review headings are present across the five service-architecture artifacts; suite topic coverage and diagrams are adequate. Caveat: worktree contains tracked edits to `AGENTS.md` and `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`, so final signoff should attribute or resolve those non-review-document changes before claiming the suite was fully non-mutating outside `.internal-dev/reviews/`.

## Handoff Notes
- Use this file for concise coordination only; detailed findings belong in subagent final outputs and final review docs.
- Planning inventory identified review domains: API/web controllers and SSE, chat turns/context/audit, chat tools and tool transcript retention, plans/tasks/saved plan chat, runtime assignments/jobs/schedules/reactions/projects, workflow engine, workspaces/outputs/filesystem confinement, AI/runtime config/model routing, persistence/schema, frontend contracts, and cross-cutting concurrency/security.
- Key source anchors for review agents: package `AGENTS.md` files, `docs/technical/*.md`, `docs/api/00-index.md`, `src/main/resources/schema.sql`, `src/main/resources/application.yml`, `pom.xml`, production packages under `src/main/java/io/mindspice/magenta2`, and focused tests under matching `src/test/java/...` packages.
- A0 Domain Inventory completed: mapped major packages, repository/table ownership, controller/API surfaces, focused test coverage, package guide constraints, and cross-domain integration edges. Main inventory gaps for later agents: `TaskService` persists through plan tables rather than a task repository, `OrchestrationController` is a very large multi-domain HTMX surface, and repository bootstrap DDL duplicates `schema.sql` and should be checked for drift.
- A4 Runtime Orchestration Review completed: main risks found in job-run lifecycle finalization on failed/cancelled/empty jobs, job recurrence firing only `job_runs` without assignment execution or next-fire advancement, WAITING assignments being reacquired by normal polling, event reaction idempotence, and public job retry/continue-on-failure doc/API drift.
- A2 Tool Execution Audit completed: tool registry/transcripts/file/shell/web/plan/task/question tools reviewed. Main findings: system chat approved tool settings are persisted but not validated or consumed by `ChatService`; task-mode question tool checks `TaskService` but calls `PlanService`; ordinary chat installs chat-file context for tools, which may conflict with docs expecting planning shell research against wider workspace/schema context.

- A5 Workflow Engine Review completed: found high-risk synchronous execution duplication, missing workflow cancellation/cancel propagation, workspace/output attribution gaps, inbox/resume policy contract gaps, and node-run snapshot/schema mismatches. Detailed findings are in the A5 final output.

- A3 Plans Tasks Review completed read-only review of ai.chat.plan, ai.chat.task, plan/task controllers, related schema/docs/tests. Detailed findings returned in agent final output.

- A8 Config Integration Review completed: reviewed AI config loading, model routing, runtime settings, feature flags, prompts, and config consumers. Main risks found in system chat settings being persisted/UI-exposed but unused by chat execution, shell command allowlists being snapshotted at startup instead of reflecting runtime agent/settings changes, runtime summary model being stored/documented without a current consumer, and local example config containing a real-looking provider key despite the existing bug record saying it was sanitized.

- A7 API Frontend Surface Review completed read-only review of `api.web`, selectors, route docs, HTMX/SimplyPages surfaces, and relevant static JS. Main findings: package-level alpha auth/CSRF guidance conflicts with current open-alpha implementation/docs; `OrchestrationController` drops advanced workflow fields during HTMX edits and concentrates too much domain mutation/rendering logic; job item retry/continue-on-failure/config docs do not match current public payload/domain records.
- A6 Workspaces Outputs Review completed read-only review of workspace/output services, controllers, schema, docs, tests, and runtime/tool integrations. Main risks: job workspace records use `jobs/<id>` while filesystem workspace APIs use `jobs/<id>/workspace`; `/api/outputs` omits the workspace filter despite service/schema support; workspace IDs accepted by jobs/assignments are not consistently validated before attribution; workflow output artifacts are materialized under workflow temp paths without direct attribution and with lifecycle ambiguity; active lease/status reads can report expired unreconciled leases.
- A9 Persistence Schema Review completed read-only review. Main risks: workspace deletion does not delete `workspace_leases`, so FK-enabled deletes can fail or FK-disabled warm DBs can retain orphan leases; agent hard-delete/purge paths delete assignments without purging `assignment_conversation_links`; saved plan chat rows can orphan through task/orchestration delete paths because `plan_chat_messages` has no FK and only `PlanController` deletes them; workflow definition deletes rely on FK cascade for `workflow_node_runs` but do not explicitly purge node rows; repository bootstrap omits several schema.sql indexes, especially workflow run/node indexes and chat memory conversation index.

- A10 Cross-Integration Synthesis completed read-only synthesis. Produced executive architecture summary, cross-domain maps/diagrams, deduplicated top risks, preserved/broken integration contracts, final artifact structure, and open architectural questions. Main cross-domain themes: runtime assignment/workflow/job execution needs one lifecycle contract; workspace/output attribution needs one canonical workspace path/id contract; chat/tool/audit persistence needs a durable streaming/audit contract; UI/API/docs need to preserve full domain payloads and align security/job contracts; delete/purge ownership must be made explicit across chat, plan, workflow, runtime, workspace, and bootstrap schemas.
