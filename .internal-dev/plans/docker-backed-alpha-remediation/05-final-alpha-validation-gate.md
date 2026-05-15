# Phase 05: Final Alpha Validation Gate

## Context

The original Docker-backed alpha validation was curl-heavy because Playwright MCP disconnected. After Phases 1-4, validation must prove not just endpoint presence but the end-user operational workflows that previously failed or could not be completed.

This phase is validation-only. The validator reports, blocks, and writes remediation handoff notes. It does not silently patch production code as the primary path.

## Goal

Produce a final alpha readiness decision backed by automated tests, startup smoke, live Podman/Docker execution, and Playwright MCP browser-origin evidence across the repaired workflow, output, editor, agent chat, and operational UI surfaces.

## In Scope

- Run all required automated tests.
- Run bounded Spring Boot startup.
- Run live Podman/Docker task/workflow/job execution against isolated SQLite.
- Run Playwright MCP browser-origin validation for all repaired surfaces.
- Update evidence files, final readiness review, bug statuses, changelog, and knowledge.

## Out of Scope

- Production fixes. If validation fails, create a remediation report and return to the relevant phase owner.
- Archiving plans or bugs unless every alpha blocker is fixed and the user approves archive.

## Implementation Steps

1. Read required docs:
   - `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
   - `.internal-dev/plans/docker-backed-alpha-remediation/README.md`
   - All completed phase reports from Phases 1-4.

2. Prepare isolated environment.
   - Use a fresh SQLite DB under `/tmp`.
   - Use Podman Docker socket:
     - `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`
   - Use `python:3.11` unless the phase evidence changed the required image.
   - Start on `http://localhost:18080` or the next free port and record the exact command.

3. Run automated validation.

```bash
mvn test
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-final-smoke.sqlite --magenta.docker.enabled=true --magenta.docker.agent-image=python:3.11 --magenta.executor.chat-threads=4'
```

4. Run Docker/Podman live execution.
   - Create/reuse an active agent.
   - Wake the agent container.
   - Prove writes inside the container:
     - `/home/agent/alpha-home.txt`
     - `/workspace/alpha-workspace.txt`
     - `/output/alpha-output.txt`
   - Submit a plan that creates `/output/hello.txt` and `/output/result.json`.
   - Confirm artifacts are registered, viewable, downloadable, and attributed to the selected agent.

5. Run workflow gate validation.
   - Create workflow with task -> user approval -> report.
   - Confirm task node performs real task execution and returns output values.
   - Reject the approval and verify resume does not continue.
   - Run again, approve, resume, and verify completion.
   - Confirm downstream report/output materialization.

6. Run job/project/status validation.
   - Create project.
   - Create job with a plan item and valid bindings.
   - Submit job to agent.
   - Verify job status transitions from `DRAFT` to `RUNNING` to `COMPLETED`, or `FAILED` on a negative binding test.
   - Confirm dashboard active work, agent jobs, agent queue, and agent history agree.

7. Run editor/model/output UI validation with Playwright MCP.
   - Plan editor: add fields of every type, steps, deliverables, save, reload, finalize.
   - Model dropdowns: save settings, plan, job, project, and agent profile with canonical aliases.
   - Outputs page: filter, open content, download file, negative missing artifact.
   - Schedules/reactions disabled states: verify copy says `=true`.
   - Agent chat: open panel/tab, send message, verify rendered response and no unexpected console/network errors.

8. Run chat/SSE regression from the knowledge workflow.
   - `/chat` page load.
   - Normal SSE stream.
   - Session mutation.
   - Planning question/approval path.
   - Active-stream conflict.
   - Interrupt/update behavior.
   - Persisted history reload after any timeout.

9. Record evidence.
   - Add or update files under `.internal-dev/reviews/docker-backed-alpha-remediation/`.
   - Update `.internal-dev/reviews/docker-backed-alpha-e2e-validation/final-alpha-e2e-readiness-review.md` only if the new evidence supersedes it.
   - Update each fixed `DEFECT-*` report status with commands and evidence.
   - Write `.internal-dev/changelogs/<date>-docker-backed-alpha-remediation.md`.
   - Write or update reusable knowledge for Docker-backed task execution and Playwright browser validation.

## Validation

Phase 5 passes only if all of these are true:

- `mvn test` passes.
- Bounded startup reaches healthy Spring Boot startup.
- Podman/Docker daemon and image are verified from the app.
- Agent container lifecycle works.
- Task execution uses container shell/tool execution for agent-context runs.
- Required files land in `/output` and are registered as artifacts.
- Output content is viewable and downloadable from the app.
- Workflow task nodes are real and approval rejection blocks continuation.
- Jobs update status and history surfaces agree.
- Plan editor and model routing contract defects are fixed.
- Agent chat is reachable from the operational UI.
- Playwright MCP completes browser-origin checks with no untriaged console/network failures.

If Playwright MCP is unavailable, stop and report the blocker. Do not replace this gate with curl-only signoff unless the user explicitly approves the blocked state.

## Exit Criteria

- Final readiness decision is one of:
  - `alpha ready`
  - `blocked with exact failing criteria`
- Every original defect is listed as fixed, still failing, or user-approved blocked.
- No item from the named validation and bug reports is silently deferred.
- Archive decision is explicit and evidence-based.
