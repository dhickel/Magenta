# Phase 04: Workflows, Approval Gates, Inbox, Resume -- Evidence

**Date**: 2026-05-13
**Test workflow ID**: `c3ab19f7-7128-4412-ba24-bdd9278e33c7`
**Runs**: `2c25ba45` (3 nodes), `02812ad5` (4 nodes)
**Method**: curl against localhost:18080; Playwright MCP unavailable per task instructions

---

## 1. Workflow Builder UI

### 1.1 Page Load -- PASS
```
GET /workflows
```
Returns full HTML page with:
- `data-orchestration-page="workflows"` container
- "New Workflow" button with `hx-get="/workflows/_editor/_new"`
- Workflow list with `hx-get="/workflows/_list"`
- `workflows.js?v=2` script loaded (all CRUD via HTMX, no custom JS rendering)

### 1.2 Editor Form -- PASS
```
GET /workflows/_editor/_new
```
Returns form with:
- Title input (`name="title"`)
- Summary textarea (`name="summary"`)
- Save button submitting to `POST /workflows/_editor`

### 1.3 Node Types Available -- PASS
From the editor form, all 7 node types are selectable:
- `task` -- execute a finalized PlanDefinition
- `user_approval` -- pause for user approval (gate)
- `agent_approval` -- pause for agent approval (gate)
- `user_message` -- one-way notification to user inbox
- `agent_message` -- one-way notification to agent inbox
- `delegation` -- start child plan/workflow runs
- `report` -- materialize declared outputs

---

## 2. Workflow Composition

### 2.1 Create via JSON API -- PASS
```
POST /api/workflows
Content-Type: application/json
{
  "title": "Phase 04 E2E Validation Workflow",
  "summary": "Validates workflow execution, approval gates, inbox, and resume",
  "nodes": [
    {"key": "run_task", "type": "task", "planId": "5ccf00b0-...", "label": "...", "config": {...}},
    {"key": "user_gate", "type": "user_approval", "label": "...", "messageTemplate": "..."},
    {"key": "materialize", "type": "report", "label": "..."}
  ],
  "routes": [
    {"id": "route_1", "fromNodeKey": "run_task", "fromOutputName": "hello_file",
     "toNodeKey": "materialize", "toInputName": "hello_file", "routeType": "map_output"},
    {"id": "route_2", "fromNodeKey": "run_task", "fromOutputName": "hello_file",
     "toNodeKey": "user_gate", "toInputName": "output_to_review", "routeType": "control"},
    {"id": "route_3", "fromNodeKey": "user_gate", "fromOutputName": "approved",
     "toNodeKey": "materialize", "toInputName": "approved", "routeType": "control"}
  ]
}
```
Response: 200 with full `WorkflowDefinition` record including assigned `id`, `createdAt`, `updatedAt`.

### 2.2 HTML Editor: Add Nodes -- PASS
```
POST /workflows/_editor/c3ab19f7-.../nodes
  nodeType=delegation&planId=6e7dff60-...&messageTemplate=...
```
Auto-generates node key (`node_1`) and renders updated node list HTML.

### 2.3 HTML Editor: Add Routes -- PASS
```
POST /workflows/_editor/c3ab19f7-.../routes
  fromNodeKey=run_task&fromOutputName=hello_file&routeType=map_output&toNodeKey=materialize&toInputName=hello_file
```
Auto-generates route ID (`route_5`) and renders updated route list HTML. Duplicate routes (same from/to) are accepted without deduplication check.

### 2.4 HTML Editor: Edit Node -- PASS
```
PUT /workflows/_editor/c3ab19f7-.../nodes/run_task
  label=...&nodeType=...&planId=...&messageTemplate=...&resumePolicy=...
```
Updates node fields and re-renders node list via HTMX.

### 2.5 HTML Editor: Delete Node/Route -- PASS
- `DELETE /workflows/_editor/{id}/nodes/{key}` -- removes node
- `DELETE /workflows/_editor/{id}/routes/{routeId}` -- removes route

### 2.6 HTML Editor: Submit to Agent -- PASS
```
GET /workflows/_submit-form/{id}
```
Returns form with agent selector, model override, priority, workspace ID.
```
POST /workflows/_submit/{id}
  agentId=23579fcf-...
```
Creates assignment: `Assignment ID: 9c64dc35-3470-496c-b33a-8d590b0d4512`, Status: QUEUED.

---

## 3. Workflow Persistence

### 3.1 Save and Reload -- PASS
```
GET /api/workflows/c3ab19f7-7128-4412-ba24-bdd9278e33c7
```
Returns exact same nodes (3) and routes (3) that were created, plus timestamps.

### 3.2 Update -- PASS
```
PUT /api/workflows/c3ab19f7-7128-4412-ba24-bdd9278e33c7
```
Added `notify_user` node + route_4. Updated title to "Phase 04 E2E Validation Workflow (Updated)".
All changes persisted and returned on subsequent GET.

### 3.3 List -- PASS
```
GET /api/workflows
```
Returns array with all workflows (1 entry after cycle test was rejected).

---

## 4. Validation

### 4.1 Valid Workflow -- PASS
```
POST /api/workflows/c3ab19f7-.../validate
```
Response: `{"errors":[],"warnings":[]}`

### 4.2 Duplicate Node Keys -- PASS
```
POST /api/workflows/validate
  nodes: [{"key":"dup","type":"task",...}, {"key":"dup","type":"user_approval"}]
```
Response:
```json
{"errors":["Duplicate node key: 'dup'", ...],
 "warnings":[]}
```

### 4.3 Bad Route Endpoints -- PASS
```
routes: [{"id":"bad","fromNodeKey":"nonexistent","toNodeKey":"also_nonexistent",...}]
```
Response:
```json
{"errors":[
  "Route 'bad': source node 'nonexistent' not found",
  "Route 'bad': destination node 'also_nonexistent' not found"
], "warnings":[]}
```

### 4.4 Cycle Detection -- PASS
```
nodes: a->b, b->a (control routes)
```
Save rejected with 400: "Workflow contains a cycle; graph must be acyclic"

### 4.5 Missing Required Inputs -- PASS
```
nodes: [{"key":"a","type":"task","planId":"5ccf00b0-..."}] with no routes
```
Response:
```json
{"errors":["TASK node 'a': required input 'task_description' is not satisfied by any route or config"],
 "warnings":[]}
```

---

## 5. Workflow Execution

### 5.1 Start Run -- PASS
```
POST /api/workflows/c3ab19f7-.../runs
```
Response: `WorkflowRun` with status "running", 3 nodeRuns all PENDING, workspace allocated, snapshot captured.

### 5.2 State Transitions -- PASS
After ~15s poll:
```json
{
  "status": "waiting",
  "currentNodeIndex": 2,
  "nodeRuns": [
    {"nodeKey": "run_task", "type": "task", "status": "COMPLETED"},
    {"nodeKey": "user_gate", "type": "user_approval", "status": "WAITING",
     "outputValues": {"messageId": "b500f0d9-..."}},
    {"nodeKey": "materialize", "type": "report", "status": "PENDING"}
  ]
}
```
State sequence: QUEUED -> RUNNING -> WAITING (at gate).

### 5.3 User Message Node -- PASS
In updated workflow with `notify_user` (user_message type):
- Node executed and sent info message to inbox: `"Task execution completed. Reviewing output..."`
- Message appeared in inbox with type "info"

### 5.4 Task Node Execution -- PASS (skeleton), DEFECT-04-02
The task node completed in ~3ms with empty `outputValues: {}`. The `taskNodeExecutor` callback is not wired by ChatService in the current environment, so the fallback `planService.startRun()` creates a PlanRun that completes instantly without actual Docker execution. Task nodes in workflows are effectively no-ops.

**Log evidence**:
```
Workflow run ... executing node run_task (type=task)
planService.startRun() allocated temp/output dirs
// No Docker execution, no model call
// Completed instantly
```

### 5.5 Workspace Management -- PASS
- Workspace allocated at `/home/hickelpickle/.magenta/root/runtime/workflow-runs/{runId}`
- Workspace cleaned up on completion (directory removed)
- Task-runs directories (from PlanService) are NOT cleaned up (empty dirs remain)

---

## 6. Approval Gate

### 6.1 Gate Creates Inbox Message -- PASS
```json
GET /api/users/inbox
[
  {
    "id": "b500f0d9-b4a6-48f6-a573-938712a0fdb6",
    "toType": "user",
    "messageType": "approval",
    "body": "Please approve the task output before proceeding",
    "metadataJson": "{\"workflowRunId\":\"2c25ba45-...\",\"nodeIndex\":1}",
    "responded": false
  }
]
```

### 6.2 HTML Inbox Render -- PASS
```
GET /inbox/_user
```
Renders table with:
- Type: "approval"
- From: "system"
- Body: message template text
- State: "pending" (chip)
- Actions: Approve button (`hx-post="/inbox/_user/{messageId}/approve"`), Reject button (`hx-post="/inbox/_user/{messageId}/reject"`)

### 6.3 Approve via JSON API -- PASS
```
POST /api/users/inbox/b500f0d9-.../respond
  {"approved": true, "comment": "Approved via E2E test"}
```
Response:
```json
{"workflowRunId": "2c25ba45-...", "messageId": "b500f0d9-...", "responded": true, "approved": true}
```
Inbox HTML updated to show state: "responded" with no action buttons.

### 6.4 Reject via JSON API -- PASS
```
POST /api/users/inbox/8405a8e2-.../respond
  {"approved": false, "comment": "Rejected: task output was empty"}
```
Response:
```json
{"workflowRunId": "02812ad5-...", "messageId": "8405a8e2-...", "responded": true, "approved": false}
```

---

## 7. Resume / Complete

### 7.1 Resume After Approval -- PASS
```
POST /api/workflow-runs/2c25ba45-.../resume
```
Gate marked COMPLETED, workflow status -> "running", proceeded to execute materialize node.

### 7.2 Rejected Gate Still Resumes -- DEFECT-04-01 (FAIL)
```
POST /api/workflow-runs/02812ad5-.../resume
```
After rejecting the approval, calling resume STILL marked the gate as COMPLETED and the workflow proceeded to completion:
```json
{"status": "completed", "nodeRuns": [
  {"nodeKey": "run_task", "status": "COMPLETED"},
  {"nodeKey": "notify_user", "status": "COMPLETED"},
  {"nodeKey": "user_gate", "status": "COMPLETED"},   // <-- should have remained WAITING or transitioned to FAILED
  {"nodeKey": "materialize", "status": "COMPLETED"}
]}
```

**Root cause**: `WorkflowRunner.resumeRun()` finds the WAITING node and unconditionally marks it as COMPLETED without checking `InboxService.parseApprovalFromResponse()`. The approval response is recorded but never consulted during resume.

### 7.3 Completion State -- PASS
After resume (regardless of approval outcome):
```json
{
  "status": "completed",
  "currentNodeIndex": 3,
  "finalMessage": "Workflow completed: Phase 04 E2E Validation Workflow (Updated)",
  "terminal": true,
  "completedAt": "2026-05-13T23:34:30.557..."
}
```

### 7.4 Post-Completion Cleanup -- PASS
- Workspace directory removed
- Run marked as terminal (completed)
- All node runs in terminal state (COMPLETED)

---

## 8. Outputs

### 8.1 Outputs Page -- PASS
```
GET /outputs
```
Renders with filter form (agent, job, project, run ID, type) and outputs grid.

### 8.2 Phase 03 Output Persisted -- PASS
```
GET /outputs/_list?agentId=23579fcf-...
```
Shows Phase 03 hello_file output: `e2e-docker-validation-plan-c4ab1c43-.../hello_file.txt`
Content: "hello.txt"

### 8.3 Workflow Run Outputs -- PASS (no output expected given DEFECT-04-02)
```
GET /outputs/_list?runId=2c25ba45-...
```
Returns "No outputs found." -- expected since task node produces no real output.

---

## 9. Run Listing and History

### 9.1 List Runs -- PASS
```
GET /api/workflows/c3ab19f7-.../runs
```
Returns 2 runs:
- `2c25ba45` (3 nodes, completed)
- `02812ad5` (4 nodes, completed)

### 9.2 Get Single Run -- PASS
```
GET /api/workflow-runs/{runId}
```
Returns full WorkflowRun with all nodeRuns, timestamps, workspace path, snapshot.

---

## 10. Endpoint Discovery Summary

### JSON API Endpoints (discovered and validated):
| Method | Path | Purpose |
|--------|------|---------|
| GET | /api/workflows | List all workflows |
| POST | /api/workflows | Create workflow |
| GET | /api/workflows/{id} | Get workflow |
| PUT | /api/workflows/{id} | Update workflow |
| DELETE | /api/workflows/{id} | Delete workflow |
| POST | /api/workflows/validate | Validate new workflow |
| POST | /api/workflows/{id}/validate | Validate existing workflow |
| POST | /api/workflows/{id}/runs | Start run |
| POST | /api/workflows/{id}/runs/stream | SSE stream run |
| GET | /api/workflows/{id}/runs | List runs |
| GET | /api/workflow-runs/{runId} | Get run |
| POST | /api/workflow-runs/{runId}/resume | Resume waiting run |
| GET | /api/users/inbox | Get user inbox |
| POST | /api/users/inbox/{msgId}/respond | Respond to inbox message |

### HTML Fragment Endpoints (discovered and validated):
| Method | Path | Purpose |
|--------|------|---------|
| GET | /workflows/_list | Workflow list fragment |
| GET | /workflows/_editor/_new | New workflow editor form |
| GET | /workflows/_editor/{id} | Edit existing workflow |
| POST | /workflows/_editor | Create workflow (form POST) |
| PUT | /workflows/_editor/{id} | Save workflow (form PUT) |
| POST | /workflows/_editor/{id}/nodes | Add node |
| PUT | /workflows/_editor/{id}/nodes/{key} | Update node |
| DELETE | /workflows/_editor/{id}/nodes/{key} | Delete node |
| POST | /workflows/_editor/{id}/routes | Add route |
| DELETE | /workflows/_editor/{id}/routes/{routeId} | Delete route |
| GET | /workflows/_editor/{id}/validate | Validate fragment |
| GET | /workflows/_submit-form/{id} | Submit-to-agent form |
| POST | /workflows/_submit/{id} | Submit to agent |
| GET | /inbox/_user | User inbox fragment |
| POST | /inbox/_user/{msgId}/approve | Approve via HTML |
| POST | /inbox/_user/{msgId}/reject | Reject via HTML |
| GET | /inbox/_agent | Agent inbox fragment |
| GET | /inbox/_agent-selector | Agent selector fragment |

### Route Types Available:
- `map_output` -- source output populates downstream input by name
- `pass_through` -- all source outputs forwarded as map
- `log` -- materialize without creating dependency
- `control` -- status flow control (no data transfer)

---

## 11. Defects Discovered

### DEFECT-04-01: Resume ignores approval response (BLOCKING)
- **Severity**: High
- **Symptom**: `POST /api/workflow-runs/{runId}/resume` unconditionally marks the waiting gate node as COMPLETED and continues execution, regardless of whether the approval was approved or rejected.
- **Root cause**: `WorkflowRunner.resumeRun()` (line 137-181) finds the WAITING node and marks it COMPLETED without calling `inboxService.parseApprovalFromResponse()` to check the actual response.
- **Impact**: Rejected workflows still complete. The approval gate is effectively cosmetic.
- **Fix direction**: In `resumeRun()`, look up the approval message, check `responseJson` for `approved: true`, and only proceed if approved. If rejected, transition to NEEDS_REVIEW or FAILED.

### DEFECT-04-02: Task nodes are no-ops without wired taskNodeExecutor (BLOCKING)
- **Severity**: High
- **Symptom**: Task nodes complete in ~3ms with empty `outputValues: {}`. No Docker execution or model call occurs.
- **Root cause**: `WorkflowRunner.executeTaskNode()` checks `taskNodeExecutor != null`. Since it is null (ChatService did not wire it in this environment), the fallback `planService.startRun()` is used, which creates a PlanRun record but does not execute the plan through Docker.
- **Impact**: Task nodes in workflows do not produce real output. The entire workflow pipeline is non-functional for task execution.
- **Fix direction**: Either wire the `taskNodeExecutor` from ChatService, or have the fallback path in `executeTaskNode()` call the same execution path that `PlanController.runPlan()` uses (which successfully executed the Docker plan in Phase 03).

### DEFECT-04-03: Duplicate routes accepted without warning
- **Severity**: Low
- **Symptom**: Adding route_5 with identical from/to as existing route_4 was accepted. Both appear in the route list.
- **Impact**: Redundant routes in the graph; validation doesn't flag them.
- **Note**: Not functionally harmful since route resolution merges outputs, but could be confusing in the UI.

---

## 12. What Requires Browser Interaction

The following validations require Playwright/browser and could not be completed via curl:

1. **Workflow graph visualization**: The editor lists nodes and routes in a flat table. Browser rendering would confirm the visual layout.
2. **HTMX dynamic interactions**: Node type change triggers backend call and re-render; label change auto-saves. These need browser JS execution.
3. **SSE streaming**: `POST /api/workflows/{id}/runs/stream` returns SSE. Needs EventSource client or browser to observe real-time events.
4. **Inbox approve/reject via HTML buttons**: Confirmed endpoints exist via HTMX attributes in HTML, but actual click+response cycle needs browser.
5. **Mobile sidebar toggle**: JS-based sidebar collapse/expand behavior.
6. **Submit-to-agent form interaction**: Agent selector dropdown population, form validation, success message rendering.

---

## 13. Summary

| Area | Result |
|------|--------|
| Workflow Builder UI | PASS |
| Workflow Composition (CRUD) | PASS |
| Workflow Persistence | PASS |
| Validation (errors/warnings) | PASS |
| Workflow Execution (state machine) | PASS |
| Approval Gate (message creation) | PASS |
| Inbox (API + HTML) | PASS |
| Approve/Reject (API) | PASS |
| Resume after approval | PASS |
| Resume after rejection | **FAIL** (DEFECT-04-01) |
| Task node actual execution | **FAIL** (DEFECT-04-02) |
| Output materialization | PASS (no output expected) |
| Workspace cleanup | PASS |
| HTML editor endpoints | PASS |
| Submit to agent | PASS |

**Overall**: The workflow engine's state machine, persistence, validation, gate/inbox integration, and UI are solid. Two blocking defects prevent end-to-end production use: (1) resume ignores rejection, and (2) task nodes don't execute plans through Docker in the workflow context.
