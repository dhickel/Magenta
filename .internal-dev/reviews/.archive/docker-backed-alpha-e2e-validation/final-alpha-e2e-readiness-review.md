# Final Alpha E2E Readiness Review — Docker-Backed Validation Campaign

**Date**: 2026-05-13
**Branch**: `operational-ui-refactor`
**Validator**: dhickel via Claude (orchestrator + 7 phase-specific subagents)
**Method**: curl-based HTMX endpoint validation against running Spring Boot app with Podman Docker runtime
**Playwright MCP**: Unavailable — browser-click, SSE live-connect, visual layout checks deferred

---

## Alpha Readiness Decision: **BLOCKED**

Three high-severity defects prevent production acceptance. The operational UI surface, Docker lifecycle, chat, and metadata-level output tracking all work. However, actual task execution inside workflows (the core value proposition) is non-functional, and the output system has no content access surface.

---

## Contract Domain Coverage

| Domain | Phase | Result | Key Limitation |
|--------|-------|--------|----------------|
| Agents | 02 | ✅ PASS (14/14) | Create endpoint accepts no form params |
| Docker | 02 | ✅ PASS | Podman 5.8.2 transparent via DOCKER_HOST |
| Plans/Tasks | 03 | ⚠️ PASS core, 4 defects | Type persistence, steps/deliverables lost, output path, auto-approval |
| Workflows | 04 | ⚠️ PASS state machine, 2 blockers | Task nodes no-op, resume ignores rejection |
| Gates | 04 | ✅ PASS | Approval messages created in inbox, approve/reject API works |
| Inbox | 04 | ✅ PASS | User + agent inboxes render, messages linked to workflow runs |
| Jobs | 05 | ⚠️ PASS (37/37), 1 notable | Status stays DRAFT after COMPLETED runs |
| Projects | 05 | ✅ PASS | CRUD, membership, workspace sections work |
| Schedules | 05 | ⚠️ Feature-flagged off | Graceful disabled state, flag text says "=false" to enable |
| Outputs | 07 | ⚠️ PASS metadata, 1 blocker | No content viewing mechanism |
| Workspaces | 07 | ✅ PASS | Metadata visible, bind mounts correct, cleanup partial |
| Model Overrides | 06 | ⚠️ PASS core, 1 bug | Alias/raw-name confusion in dropdowns |
| Chat | 06 | ✅ PASS | SSE flow, sessions, planning mode, interrupt all work |
| UI Operations | all | ⚠️ PASS endpoints, BLOCKED browser | All HTMX endpoints verified; click/SSE/visual deferred |

---

## Defect Summary

### Alpha Blockers (must fix before production)

| ID | Phase | Summary | Fix Direction |
|----|-------|---------|---------------|
| **DEFECT-04-02** | 04 | Task nodes in workflows are no-ops — `taskNodeExecutor` is null, fallback creates instant-completing PlanRun with no Docker execution | Wire `taskNodeExecutor` from ChatService or route through the same execution path as direct plan submission |
| **DEFECT-04-01** | 04 | Resume ignores approval response — rejected workflows complete successfully. `WorkflowRunner.resumeRun()` never checks `InboxService.parseApprovalFromResponse()` | Check approval response before marking gate complete; on reject, transition to FAILED or NEEDS_REVIEW |
| **DEFECT-03-03** | 03 | Model writes outputs to host filesystem path instead of container `/output/` mount — files land at `.magenta/root/` not `.magenta/root/agents/{id}/outputs/` | Fix execution environment prompt to use container-relative paths; ensure working directory is the container mount |

### Alpha Should-Fix (strongly recommended before wider use)

| ID | Phase | Summary | Fix Direction |
|----|-------|---------|---------------|
| **DEFECT-07-01** | 07 | No output content viewing mechanism — outputs page is metadata-only, no `_read`/`_view`/`_content` endpoint | Add fragment endpoint that reads and renders output file content, or serves file download |
| **DEFECT-03-01** | 03 | PlanFieldType selection never persists — `valueOf(typeStr)` called with wireName ("user_message") instead of enum name ("USER_MESSAGE"), falls back to STRING | Replace `PlanFieldType.valueOf(typeStr)` with `PlanFieldType.fromWireName(typeStr)` at OrchestrationController line 828 |
| **DEFECT-03-02** | 03 | Steps/deliverables silently discarded — `addListItem` creates items with empty text, `cleanSteps()`/`cleanList()` filter them out | Either populate initial text from user input, or skip cleaning for newly-added items |
| **DEFECT-05** | 05 | Job status stays DRAFT after COMPLETED runs — `work_assignments.status` updates but `job_definitions.status` does not | Update job status on assignment terminal events; reconcile event-sourced state with entity state |
| **DEFECT-06** | 06 | Model dropdowns mix aliases and raw names — selecting "qwen3.6:35b" fails backend validation which only accepts "local-qwen" | Either show only aliases in dropdowns, or have backend reverse-resolve raw names to aliases |
| **DEFECT-07-05** | 07 | Unregistered loose files in workspace root — model-generated files not tracked in `run_output_artifacts` | Register all output artifacts at task completion, or at minimum track files written to known output paths |

### Low Priority / Cosmetic

| ID | Phase | Summary |
|----|-------|---------|
| DEFECT-03-04 | 03 | All plans auto-approved on save (hardcoded `PlanStatus.APPROVED` in `PlanService.saveTask` line 480) |
| DEFECT-03-05 | 03 | Output file naming mismatch — model reports "hello.txt" but file saved as `hello_file.txt` |
| DEFECT-04-03 | 04 | Duplicate routes accepted without deduplication warning |
| DEFECT-07-03 | 07 | Runtime task-run parent directories not cleaned after completion |
| DEFECT-07-04 | 07 | Duplicate `workspaces`/`workspace_roots` tables — `workspace_roots` empty, UI reads from `workspaces` |
| — | 05 | Feature flag messages say "Enable with ...=false" (should be `=true`) |
| — | 05 | Agent history tab shows static placeholder despite executed runs |
| — | 05 | Job items bindingsJson field has no inline guidance about required plan inputs |
| — | 06 | Agent side-panel chat JS implemented but never imported — intentionally deferred per test assertion |

---

## Test Harness Blockers

| Blocker | Impact |
|---------|--------|
| Playwright MCP server disconnected mid-session | Browser-click validation, SSE live-connect, visual layout checks, console error capture all deferred. All phases used curl-based HTMX endpoint validation as fallback. |
| No `docker` binary on host | Used Podman 5.8.2 via `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock` — transparent drop-in replacement. Not a blocker. |

---

## Environment

| Item | Value |
|------|-------|
| App port | 18080 |
| Database | SQLite at `/tmp/magenta2-alpha-e2e.sqlite` |
| Container runtime | Podman 5.8.2 (Docker API v1.44) |
| Agent image | `python:3.11` (docker.io/library/python:3.11) |
| Java | 25.0.3 |
| Spring Boot | 3.4.4 |
| Docker Java client | `docker-java` + `docker-java-transport-httpclient5` |
| Agent UUID (test) | `23579fcf-ca99-4862-a2fd-b8eb6073928c` ("magenta", model: local-qwen) |

---

## What Works Well

1. **Docker/Podman integration** is seamless — `DOCKER_HOST` is the only bridge needed. Container lifecycle (start/stop/restart) works end-to-end with both HTMX UI and actual container state in sync.
2. **Web surface** — all 12 pages return proper HTML, no raw JSON fragments. HTMX assets load correctly, no compat-noop stubs active.
3. **Agent lifecycle** — create, enable, disable, archive, hard delete all work. Two-step delete confirmation required. No clone path exposed.
4. **Chat SSE flow** — messages stream correctly, model overrides respected, planning mode initiates and cancels properly.
5. **Workflow state machine** — validation (duplicate keys, bad routes, cycles, missing inputs) works comprehensively. State transitions (QUEUED→RUNNING→WAITING→COMPLETED) are correct. Only the task node executor wiring and resume-approval-check are missing.
6. **Inbox/approval integration** — gates create inbox messages with proper workflow/run metadata. Approve/reject API and HTML endpoints both work.
7. **Output metadata** — attribution chain (agent→plan→run→runType) is intact. Filtering by agent, type, and runId all work correctly.

---

## Remediation Order Recommendation

```
1. DEFECT-04-02 (task node executor wiring)        ← unblocks workflow execution
2. DEFECT-03-03 (output path in container)           ← unblocks output collection
3. DEFECT-04-01 (resume approval check)              ← unblocks gate integrity
4. DEFECT-03-01 (PlanFieldType persistence)          ← unblocks typed plans
5. DEFECT-07-01 (output content viewing)             ← unblocks output UX
6. DEFECT-03-02 (steps/deliverables)                 ← unblocks plan editing
7. DEFECT-05 (job status sync)                       ← data consistency
8. DEFECT-06 (model alias/raw-name confusion)        ← settings UX
```

Steps 1-3 are the alpha gate. Steps 4-8 are should-fix before wider use.

---

## Post-Alpha Deferral Candidates

- Agent side-panel chat (JS exists, not wired — intentional)
- Schedules feature (feature-flagged off)
- Reactions feature (feature-flagged off)
- Agent history tab (static placeholder)
- Clone agent (intentionally absent)
- Dedicated Stop/Cancel button during streaming (design choice: interrupt-by-new-message pattern)
- Job items bindings guidance (UX enhancement)
- Duplicate workspace tables consolidation (DEFECT-07-04)
- Runtime task-run directory cleanup (DEFECT-07-03)

---

## Evidence Files

| Phase | Evidence File |
|-------|---------------|
| 01 | `01-harness-and-docker-preflight-evidence.md` |
| 02 | `02-agent-docker-lifecycle-evidence.md` |
| 03 | `03-plans-tasks-docker-execution-evidence.md` |
| 04 | `04-workflows-gates-inbox-resume-evidence.md` |
| 05 | `05-jobs-projects-schedules-assignment-evidence.md` |
| 06 | `06-chat-model-overrides-agent-surfaces-evidence.md` |
| 07 | `07-outputs-workspaces-artifact-contract-evidence.md` |

---

## Conclusion

The operational UI surface, Docker runtime, agent lifecycle, chat, plan/task editing, workflow builder, approval gates, inbox integration, jobs, and projects are all functional at the HTTP/HTMX endpoint level. The blocking defects are confined to three specific areas: workflow task execution wiring, resume approval gating, and output path/access. All three have clear fix directions. Once these are resolved and validated through browser-origin Playwright testing, the alpha contract is ready for production acceptance.
