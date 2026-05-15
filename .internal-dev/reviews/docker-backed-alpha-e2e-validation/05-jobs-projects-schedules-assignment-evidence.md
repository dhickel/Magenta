# 05 — Jobs, Projects, Schedules, and Agent Assignment Evidence

**Date**: 2026-05-13
**Branch**: `operational-ui-refactor`
**App**: http://localhost:18080 (running)
**Container runtime**: Podman at `unix:///run/user/1000/podman/podman.sock`
**Image**: `python:3.11`
**DB**: `/tmp/magenta2-alpha-e2e.sqlite`
**Test agent UUID**: `23579fcf-ca99-4862-a2fd-b8eb6073928c` ("magenta", model: local-qwen)
**Playwright MCP**: UNAVAILABLE — all tests via curl

---

## 1. Project Creation and Editing

### 1.1 Page Load

**Status**: PASS

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/projects
# 200
```

Page renders with "New Project" button (`hx-get="/projects/_editor/_new"`) and project list placeholder.

### 1.2 Project Editor — New

**Status**: PASS

```bash
curl -s http://localhost:18080/projects/_editor/_new
```

Returns form with fields:
- Name (text input)
- Description (textarea)
- Owner Agent ID (text input)
- Git Repo URL (text input)
- Worktype (select: CODING_CENTRIC, DATA_CENTRIC, RESEARCH_CENTRIC)
- Default Model (select: populated with available models + Default option)

Form submits via `hx-post="/projects/_editor"`.

### 1.3 Project Creation

**Status**: PASS

```bash
curl -s -X POST http://localhost:18080/projects/_editor \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=Phase05 Test Project&description=Test project&
      ownerAgentId=23579fcf-ca99-4862-a2fd-b8eb6073928c&
      workTypeProfile=CODING_CENTRIC&model="
```

Returns the full project editor with populated values. Auto-assigned ID: `fe59894d-97dc-4f98-908c-e784c18d62fe`.

Database row:
```
fe59894d-97dc-4f98-908c-e784c18d62fe|Phase05 Test Project|...|23579fcf-ca99...|CODING_CENTRIC
```

### 1.4 Project Editor Sections

**Status**: PASS

Editor shows these sections (after creation):
- **Workspace** section: shows owner, kind (PROJECT), path (`projects/{id}/workspace`), member count
- **Agents** section: loads via `hx-get="/projects/_detail/{id}/agents"`, shows owner as member
- **Active Jobs** section: loads via `hx-get="/projects/_detail/{id}/jobs"`
- **Recent Outputs** section: loads via `hx-get="/projects/_detail/{id}/outputs"`
- **Advanced** details: ID, created/updated timestamps
- **Delete** button: `hx-delete="/projects/{id}"` with confirmation

### 1.5 Project Editing (PUT)

**Status**: PASS

```bash
curl -s -X PUT http://localhost:18080/projects/_editor/fe59894d-... \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "name=Phase05 Test Project UPDATED&description=Updated&
      ownerAgentId=23579fcf-...&workTypeProfile=DATA_CENTRIC&model=qwen3.6:35b"
```

Name, description, worktype, and model were all persisted. Updated timestamp changes correctly. Worktype select correctly shows DATA_CENTRIC as selected after update.

### 1.6 Project Deletion

**Status**: PASS

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  http://localhost:18080/projects/fe59894d-...
# 200
```

Project disappears from list. `projects/_list` returns "No projects."

### 1.7 Agent Membership

**Status**: PASS

`/projects/_detail/{id}/agents` shows owner agent with "owner" role:
```html
<div class="orch-row"><strong>23579fcf-ca99-4862-a2fd-b8eb6073928c</strong><span>owner</span></div>
```

Database confirms in `project_agent_memberships` table.

---

## 2. Job Creation and Editing

### 2.1 Page Load

**Status**: PASS

```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:18080/jobs
# 200
```

Page renders with "New Job" button (`hx-get="/jobs/_editor/_new"`), agent filter select, and job list placeholder.

### 2.2 Job Editor — New

**Status**: PASS

```bash
curl -s http://localhost:18080/jobs/_editor/_new
```

Returns form with fields:
- Title (text input)
- Summary (textarea)
- Owner Agent ID (text input)
- Project ID (text input)
- Status (text input, default "DRAFT")
- Worktype (select)
- Default Model (select)

Form submits via `hx-post="/jobs/_editor"`.

### 2.3 Job Creation

**Status**: PASS

```bash
curl -s -X POST http://localhost:18080/jobs/_editor \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "title=Phase05 Test Job&summary=Test&
      ownerAgentId=23579fcf-ca99-4862-a2fd-b8eb6073928c&
      projectId=fe59894d-97dc-4f98-908c-e784c18d62fe&
      status=DRAFT&workTypeProfile=CODING_CENTRIC&model="
```

Returns full job editor. Auto-assigned ID: `4f9083a8-ac2e-4e4f-b5af-16a301d999d7`. Database confirms in `job_definitions` table with correct project_id linkage.

### 2.4 Job Editor Sections

**Status**: PASS

Editor includes:
- **Advanced** details: ID, status chip (DRAFT), workspace ID, created timestamp
- **Ordered Items** section: manages PLAN/WORKFLOW items in sequence
- **Add Item** button: `hx-post="/jobs/_editor/{id}/items"`
- **Submit to Agent** button: loads submit form via `hx-get="/jobs/_submit-form/{id}"`
- **Delete** button
- **Recent Outputs** panel: loads via `hx-get="/jobs/_detail/{id}/outputs"`
- **Run Events** panel: loads via `hx-get="/jobs/_detail/{id}/events"`

### 2.5 Job Editing (PUT)

**Status**: PASS

```bash
curl -s -X PUT http://localhost:18080/jobs/_editor/4f9083a8-... \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "title=Phase05 Test Job UPDATED&summary=Updated&..."
```

Title, summary, worktype persisted correctly.

### 2.6 Job Deletion

**Status**: PASS

```bash
curl -s -o /dev/null -w "%{http_code}" -X DELETE \
  http://localhost:18080/jobs/4f9083a8-...
# 200
```

Job disappears from list. `jobs/_list` returns "No jobs."

### 2.7 Job Items — Add/Remove

**Status**: PASS

Add item:
```bash
curl -s -X POST http://localhost:18080/jobs/_editor/{jobId}/items \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "key=test-item&itemType=PLAN&
      planId=5ccf00b0-f033-466c-8844-247e227e5f33&
      bindingsJson={}&priority=1"
```

Item appears in Ordered Items list with: order number, type chip (PLAN), key, plan ID, priority, and remove button. Remove button uses `hx-delete="/jobs/_editor/{jobId}/items/0"` and works correctly (item disappears from list).

---

## 3. Job Submission and Execution

### 3.1 Submit Form

**Status**: PASS

```bash
curl -s http://localhost:18080/jobs/_submit-form/4f9083a8-...
```

Returns form with:
- Agent selector (populated with available agents, shows "magenta (local-qwen)")
- Model Override (text input, optional)
- Priority (number input, 0-100)

Form submits via `hx-post="/jobs/_submit/{jobId}"`.

### 3.2 Submission (no items)

**Status**: PASS

```bash
curl -s -X POST http://localhost:18080/jobs/_submit/4f9083a8-... \
  -d "agentId=23579fcf-ca99-4862-a2fd-b8eb6073928c&priority=0"
```

Returns:
```html
<div class="orch-panel">
  <h3>Assignment Created</h3>
  <span>ID: d25e2bb3-0923-4b76-9a75-3f0027794066</span>
  <span>Status: QUEUED</span>
</div>
```

Assignment appeared in agent queue within seconds with status COMPLETED (job had no items, zero-work run).

### 3.3 Submission with Plan Item (Missing Inputs)

**Status**: PASS (execution engine correctly validates inputs)

After adding a plan item (E2E Docker Validation Plan, which requires `task_description` input), re-submitted:

```bash
curl -s -X POST http://localhost:18080/jobs/_submit/4f9083a8-... \
  -d "agentId=23579fcf-ca99-4862-a2fd-b8eb6073928c&priority=0"
```

Result: Assignment FAILED with clear error.

Agent queue shows:
```
Status: FAILED
Type: JOB_RUN
```

Database `job_runs` row confirms failure reason:
```json
{
  "test-item": {
    "error": "Missing required input(s): task_description",
    "failed": true
  }
}
```

### 3.4 Event Tracking

**Status**: PASS

`/jobs/_detail/{jobId}/events` shows:
```
JOB_RUN_FAILED | 2026-05-13T23:17:42.764250352Z
JOB_RUN_RUNNING | 2026-05-13T23:15:48.647477780Z
```

`orchestration_events` table includes JOB_STATUS_CHANGED event.

### 3.5 Job Output Directories

**Status**: PASS

Workspace/output directories created on disk:
```
~/.magenta/root/jobs/{jobId}/outputs/phase05-test-job-updated-{runId}/
```

Directories exist but empty (no output artifacts produced from zero-item or failed runs).

---

## 4. Agent Assignment and Queue

### 4.1 Agent Queue Tab

**Status**: PASS

`/agents/_detail/{agentId}/queue` shows assignment table with columns:
- Type (JOB_RUN)
- Status (COMPLETED / FAILED with appropriate chips)
- Priority
- Job (linked ID)
- Created (relative time)

Multiple assignments accumulate in the queue view.

### 4.2 Agent Jobs Tab

**Status**: PASS

`/agents/_detail/{agentId}/jobs` shows table:
- Title (linked to /jobs)
- Status (DRAFT chip)
- Project (ID reference)
- Updated (relative time)

### 4.3 Agent Submit Form

**Status**: PASS

`/agents/_submit-form/{agentId}` provides direct assignment form:
- Assignment Type: TASK_RUN, WORKFLOW_RUN, JOB_RUN
- Target ID (text input)
- Priority (0-9)
- Model Override (optional)

Submits via `hx-post="/agents/_submit/{agentId}"`.

### 4.4 Docker Container Status

**Status**: PASS

Container runs throughout testing:
```
CONTAINER ID  NAMES                       STATUS
a8bed6f492f3  magenta-agent-23579fcf-ca9  Up 4 minutes
```

Docker status fragment at `/agents/_detail/{agentId}/docker-status` returns:
```html
<span class="orch-chip">IDLE</span><span> ok</span>
Container: a8bed6f492f3...
Name: magenta-agent-23579fcf-ca9
Image: python:3.11
```

---

## 5. Dashboard Integration

### 5.1 Active Work Fragment

**Status**: PASS

`/dashboard/_active-work` shows jobs table with created test job:
```
| JOB | Phase05 Test Job UPDATED | 23579fcf-... | DRAFT | fe59894d-... |
```

After deletion, table clears.

### 5.2 Open Projects Fragment

**Status**: PASS

`/dashboard/_open-projects` shows project card grid:
```html
<h3><a href="/projects/{id}">Phase05 Test Project UPDATED</a></h3>
<span>Owner: 23579fcf-...</span>
<span>Updated: Xs ago</span>
```

After deletion, grid clears.

### 5.3 Agents Fragment

**Status**: PASS

`/dashboard/_agents` shows magenta agent:
```
| magenta | ACTIVE | local-qwen | — | — |
```

### 5.4 Side Inbox

**Status**: PASS

`/dashboard/_side-inbox` shows: `0 waiting approvals`

### 5.5 Side Outputs

**Status**: PASS

`/dashboard/_side-outputs` shows: `No recent outputs` (expected, no outputs produced)

---

## 6. Schedule and Reaction UI

### 6.1 Schedules Tab

**Status**: PASS (feature-flagged off)

`/agents/_detail/{agentId}/schedules`:
```html
<strong>Schedules are disabled.</strong>
<span>Enable with magenta.features.schedules-enabled=false.</span>
```

**Note**: The feature flag text says "Enable with ...=false" which is confusing — to enable, it should be `=true`. The message format matches what Phase 02 recorded.

### 6.2 Reactions Tab

**Status**: PASS (feature-flagged off)

`/agents/_detail/{agentId}/reactions`:
```html
<strong>Event reactions are disabled.</strong>
<span>Enable with magenta.features.reactions-enabled=false.</span>
```

Same flag text issue as schedules.

### 6.3 Feature Flag Messaging Bug

**Status**: MINOR FINDING

Both disabled messages say "Enable with `magenta.features.X-enabled=false`" — the value should be `=true` for enabling. This is a copy-paste bug in the feature flag condition text, not functional.

---

## 7. Agent Detail Tabs Summary

All 9 tabs on agent detail page return 200:

| Tab | Endpoint | Status | Content |
|-----|----------|--------|---------|
| Dashboard | `/agents/_detail/{id}/dashboard` | PASS | Agent metadata, counters (queue: 0, inbox: 0, jobs: 1), Docker status, lifecycle buttons |
| Queue | `/agents/_detail/{id}/queue` | PASS | Assignment table with status chips |
| Inbox | `/agents/_detail/{id}/inbox` | PASS | "No inbox messages." |
| Jobs | `/agents/_detail/{id}/jobs` | PASS | Jobs table with status and project ref |
| Schedules | `/agents/_detail/{id}/schedules` | PASS | Feature-flagged disabled message |
| Reactions | `/agents/_detail/{id}/reactions` | PASS | Feature-flagged disabled message |
| Workspace | `/agents/_detail/{id}/workspace` | PASS | Workspace metadata, active leases, links |
| Outputs | `/agents/_detail/{id}/outputs` | PASS | "No recent outputs." |
| History | `/agents/_detail/{id}/history` | PASS | Static message: "Run history appears as assignments and job events are persisted." |

---

## 8. Route Integrity

All navigation routes return 200:

| Route | HTTP Status |
|-------|-------------|
| `/` | 200 |
| `/dashboard` | 200 |
| `/plans` | 200 |
| `/workflows` | 200 |
| `/jobs` | 200 |
| `/projects` | 200 |
| `/inbox` | 200 |
| `/agents` | 200 |
| `/outputs` | 200 |
| `/settings` | 200 |
| `/chat` | 200 |
| `/agents/{id}` | 200 |
| `/nonexistent` | 404 (correct) |

No 405 or 500 errors detected on expected navigation paths.

---

## 9. Findings and Defects

### 9.1 Docker Status Endpoint Path Mismatch (MINOR)

`/agents/_docker/{agentId}/docker-status` returns 404. The correct endpoint is `/agents/_detail/{agentId}/docker-status`. The dashboard tab and agent list refresh correctly use the `/_detail/` path, so this only matters if external tools or other code paths reference the `/_docker/` variant.

### 9.2 Job Status Not Updated After Submission (NOTABLE)

Job status remains `DRAFT` even after successful submission and COMPLETED runs. The `job_definitions.status` column stays `DRAFT` while `work_assignments.status` correctly shows `COMPLETED`/`FAILED`. The orchestration events table records a JOB_STATUS_CHANGED event claiming COMPLETED, but the job's own status field is not updated in the database.

### 9.3 History Tab Shows Empty Despite Executed Runs (NOTABLE)

Agent history tab at `/agents/_detail/{id}/history` shows a static placeholder: "Run history appears as assignments and job events are persisted." even though 2 assignments were completed/failed. The queue tab correctly shows assignment history, but the History tab does not aggregate this data.

### 9.4 Job Items Bindings UX Gap (UX NOTE)

The job item add form has a `bindingsJson` text field but provides no inline guidance about what inputs the selected plan requires. When a plan has required inputs (e.g., `task_description`) and bindings are empty/missing, the execution engine correctly fails with a clear error — but this could be caught at item-add time or submission time rather than at runtime.

### 9.5 Feature Flag Messaging (COSMETIC)

Schedule and reaction disabled messages say "Enable with `magenta.features.X-enabled=false`" — should be `=true` to enable. Copy-paste issue in disabled-state messages.

---

## 10. Checklist Summary

| Check | Status |
|-------|--------|
| 1.1 Projects page loads | PASS |
| 1.2 Project creation (POST /projects/_editor) | PASS |
| 1.3 Project editing (PUT /projects/_editor/{id}) | PASS |
| 1.4 Project deletion | PASS |
| 1.5 Project agent membership shown | PASS |
| 1.6 Project workspace/agents/jobs/outputs sections | PASS |
| 2.1 Jobs page loads | PASS |
| 2.2 Job creation (POST /jobs/_editor) | PASS |
| 2.3 Job editing (PUT /jobs/_editor/{id}) | PASS |
| 2.4 Job deletion | PASS |
| 2.5 Job items add/remove | PASS |
| 2.6 Job items ordered list rendering | PASS |
| 3.1 Job submit form renders | PASS |
| 3.2 Job submission to agent | PASS |
| 3.3 Assignment appears in agent queue | PASS |
| 3.4 Assignment status tracking (COMPLETED/FAILED) | PASS |
| 3.5 Input validation at execution time | PASS |
| 3.6 Job run events tracked | PASS |
| 3.7 Output directories created on disk | PASS |
| 4.1 Agent queue table correct | PASS |
| 4.2 Agent jobs table correct | PASS |
| 4.3 Agent submit form correct | PASS |
| 4.4 Docker container handles execution | PASS |
| 4.5 Docker container remains running | PASS |
| 5.1 Dashboard _active-work fragment | PASS |
| 5.2 Dashboard _open-projects fragment | PASS |
| 5.3 Dashboard _agents fragment | PASS |
| 5.4 Dashboard _side-inbox fragment | PASS |
| 5.5 Dashboard _side-outputs fragment | PASS |
| 6.1 Schedules tab disabled state | PASS |
| 6.2 Reactions tab disabled state | PASS |
| 7.1 All 9 agent detail tabs return 200 | PASS |
| 7.2 All nav route links return 200 | PASS |
| 7.3 No 405/500 errors on nav paths | PASS |

---

## 11. Notes

- All tests performed via curl (Playwright MCP unavailable). Browser-only interactive flows (submit confirmation dialogs, tab switching animation, sidebar collapse toggles) could not be validated but the underlying HTMX endpoints all return correct HTML.
- Test data (project, job) was deleted after validation to restore clean state.
- The plan "E2E Docker Validation Plan" (ID: `5ccf00b0-f033-466c-8844-247e227e5f33`, status: APPROVED) from Phase 02 was used as a job item for submission testing.
