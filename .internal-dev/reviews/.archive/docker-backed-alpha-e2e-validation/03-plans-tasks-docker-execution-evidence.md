# Phase 03 — Plan/Task Creation and Docker-Backed Execution Evidence

**Date:** 2026-05-13  
**App:** http://localhost:18080  
**Database:** /tmp/magenta2-alpha-e2e.sqlite  
**Agent:** 23579fcf-ca99-4862-a2fd-b8eb6073928c (magenta, model: local-qwen)  
**Docker:** unix:///run/user/1000/podman/podman.sock, image python:3.11  
**Browser:** None (Playwright MCP unavailable; curl-only HTMX endpoint validation)

---

## 1. Plan/Task Creation

### 1.1 GET /plans — Page Load
**Result: PASS**

```bash
curl -s http://localhost:18080/plans
```
The full page loads with:
- "New Plan" button: `hx-get="/plans/_editor/_new"`
- Plan filter input: `hx-get="/plans/_list" hx-trigger="keyup changed delay:300ms"`
- Plan list container: `hx-get="/plans/_list" hx-trigger="load"`
- JS include: `/js/orchestration/plans.js?v=2`

### 1.2 GET /plans/_list — Empty State
**Result: PASS**

```bash
curl -s http://localhost:18080/plans/_list
# Response: <div class="tool-item">No plans.</div>
```

### 1.3 GET /plans/_editor/_new — Editor Form
**Result: PASS**

```bash
curl -s http://localhost:18080/plans/_editor/_new
```
Returns a form `hx-post="/plans/_editor"` with fields:
- `kind` (hidden, "TASK_TEMPLATE")
- `title`, `summary`, `goal`, `notes`
- `workTypeProfile` (CODING_CENTRIC / DATA_CENTRIC / RESEARCH_CENTRIC)
- `planningModel`, `executionModel` dropdowns populated with models: qwen3.6:35b, granite4.1:8b, gemma4-fullctx:e4b, gemma4-e4b-UC:latest, gemma4-26b:32k, deepseek-v4-pro

### 1.4 POST /plans/_editor — Create Plan
**Result: PASS**

```bash
curl -s -X POST http://localhost:18080/plans/_editor \
  -d "kind=TASK_TEMPLATE" \
  -d "title=E2E Docker Validation Plan" \
  -d "summary=Validate Docker-backed plan execution end-to-end" \
  -d "goal=Create a Python script in /output that writes hello.txt and a JSON artifact" \
  -d "notes=Phase 03 validation plan" \
  -d "workTypeProfile=CODING_CENTRIC"
```

Returns the plan editor with plan UUID `5ccf00b0-f033-466c-8844-247e227e5f33`. Plan persisted and reloadable.

### 1.5 Plan List After Creation
**Result: PASS**

```bash
curl -s http://localhost:18080/plans/_list
# Response: E2E Docker Validation Plan — TASK_TEMPLATE - APPROVED
# (also shows a second plan "No-Input Test Plan")
```

---

## 2. Structured Inputs/Outputs

### 2.1 Input Field Types Available
**Result: PASS**

POST `/plans/_editor/{planId}/inputs` returns a field row with select options:
- `user_message`
- `string` (default)
- `file_path`
- `number`
- `json`

Each field row has: name input, type select, required checkbox, array checkbox, description textarea, schema textarea.

### 2.2 Output Field Types Available
**Result: PASS**

POST `/plans/_editor/{planId}/outputs` returns the same field types as inputs.

### 2.3 Adding an Input Field with Parameters
**Result: PARTIAL PASS (type bug)**

```bash
curl -s -X PUT http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/inputs \
  -d "inputsName0=task_description" \
  -d "inputsType0=user_message" \
  -d "inputsRequired0=on" \
  -d "inputsDesc0=The task the agent should perform"
```

- Name "task_description" persisted: PASS
- Required checkbox persisted: PASS
- Description persisted: PASS
- **Type remained "string" instead of "user_message": BUG** (see Defects section)

### 2.4 Adding Multiple Input Fields
**Result: PASS**

POST `/plans/_editor/{planId}/inputs` a second time adds a second field row (index 1, name "field_2"). Both fields render. DELETE on index 1 removes it, leaving only index 0.

### 2.5 Creating a Plan with No Inputs
**Result: PASS**

Created plan `6e7dff60-93af-4ef7-9fc9-eba544751a92` ("No-Input Test Plan") with no inputs or outputs defined. The submit-to-agent form rendered correctly without a "Runtime Inputs" section.

### 2.6 No-Input Submit Form
**Result: PASS**

Submit form for the no-input plan showed only agent select, model override, priority, and workspace ID fields. No runtime input section.

---

## 3. Plan Editor Persistence

### 3.1 Full Editor Reload
**Result: PASS**

```bash
curl -s http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33
```

All fields persisted:
- title: "E2E Docker Validation Plan"
- summary: "Validate Docker-backed plan execution end-to-end"
- goal: "Create a Python script in /output that writes hello.txt and a JSON artifact"
- notes: "Phase 03 validation plan"
- workTypeProfile: CODING_CENTRIC (selected)
- input name: "task_description", required: checked
- output name: "hello_file", required: checked
- Status: APPROVED (auto-set by PlanService.saveTask line 480)

### 3.2 Full Editor Update (PUT)
**Result: PASS**

PUT `/plans/_editor/{planId}` with `title`, `summary`, `goal`, `notes`, `workTypeProfile`, `planningModel`, `executionModel` updates all fields and returns the re-rendered editor. Inputs/outputs are preserved via `current.inputs()` / `current.outputs()` (not overwritten by params).

### 3.3 Model Override Dropdowns
**Result: PASS**

Editor renders both planningModel and executionModel selects with "Default" (empty) and the same 6 model options each. Both default to empty string. These persist through save/reload.

### 3.4 Finalize Button
**Result: PASS**

`hx-post="/plans/_editor/{planId}/finalize"` is present. Calling it re-saves the plan and returns the editor. (Currently a no-op since all saved plans are already APPROVED.)

### 3.5 Continue in Chat
**Result: PASS**

`hx-get="/plans/_editor/{planId}/chat-prompt-fragment"` generates a structured prompt with title, goal, summary, and instructions. Includes a "Copy & Open Chat" button.

---

## 4. Submit to Agent and Docker Execution

### 4.1 Submit Form
**Result: PASS**

```bash
curl -s http://localhost:18080/plans/_submit-form/5ccf00b0-f033-466c-8844-247e227e5f33
```

Renders a panel with:
- Agent select (magenta / local-qwen)
- Model Override text input
- Priority number input
- Workspace ID text input
- Runtime Inputs section with `input_task_description` (matching the plan's input field, showing type "string" and description)

### 4.2 Submit to Agent
**Result: PASS**

```bash
curl -s -X POST http://localhost:18080/plans/_submit/5ccf00b0-f033-466c-8844-247e227e5f33 \
  -d "agentId=23579fcf-ca99-4862-a2fd-b8eb6073928c" \
  -d "input_task_description=Write a Python script that creates /output/hello.txt..." \
  -d "priority=0"
```

Returns: Assignment ID `43560731-794d-4ebb-946b-132b55a9416b`, Status QUEUED, Agent linked.

### 4.3 Execution Monitoring — podman
**Result: PASS**

Before execution, the agent container was in `exited` state (ExitCode 137). After waking via `POST /agents/_docker/{id}/start`, container entered `running` state.

During execution:
- `podman ps` showed container UP
- `podman top` showed the idle-loop process (container runs a persistent shell)
- Agent queue tab showed TASK_RUN as RUNNING

### 4.4 Run Status Transitions
**Result: PASS**

```
POST submit → QUEUED → RUNNING → COMPLETED
```

Run ID: `c4ab1c43-da3c-4377-98e6-9b00f2157f93`
- Created: 23:19:28
- Started: 23:19:28
- Completed: 23:20:53 (~85 seconds)
- Status: COMPLETED
- No error text

### 4.5 Output Values in Run Data
**Result: PASS**

```json
"outputValues": {
  "hello_file": "hello.txt"
}
```

The model returned "hello.txt" as the output field value.

### 4.6 Output Files on Disk
**Result: PARTIAL PASS (path issue)**

Framework output directory:
```
/home/hickelpickle/.magenta/root/agents/system/outputs/e2e-docker-validation-plan-c4ab1c43-da3c-4377-98e6-9b00f2157f93/
```

Contains: `hello_file.txt` (9 bytes, content: "hello.txt")

The file is named `<fieldname>.txt` (hello_file.txt), NOT the model-reported name (hello.txt). The content "hello.txt" appears to be the model returning the file name as the value rather than actual file content.

### 4.7 Actual Output Content (Written to Wrong Path)
**Result: FAIL — Wrong output path**

The model wrote files to `/home/hickelpickle/.magenta/root/` instead of `/output/` inside the container:

```bash
cat /home/hickelpickle/.magenta/root/hello.txt
# Hello from Docker-backed Magenta validation!

cat /home/hickelpickle/.magenta/root/result.json
# {"status": "success", "message": "E2E validation passed"}
```

The files have CORRECT content, but were written to the wrong directory. The container's `/output` mount (at `/home/hickelpickle/.magenta/root/agents/23579fcf-ca99-4862-a2fd-b8eb6073928c/outputs/`) is EMPTY.

Root cause: The model received the output directory as `/output` in the plan but wrote to the host's filesystem root path `/home/hickelpickle/.magenta/root/`. This suggests the container's working directory or the model's path resolution was incorrect.

### 4.8 Agent Outputs Tab
**Result: PASS**

```
GET /agents/_detail/23579fcf-ca99-4862-a2fd-b8eb6073928c/outputs
```

Shows: hello_file, type: text, plan: 5ccf00b0, run: c4ab1c43, created: 2m ago

### 4.9 Container Post-Execution
**Result: PASS**

Container remained running after execution (UP ~9 minutes). No crashes or exits.

---

## 5. Endpoint Discovery

### 5.1 Discovered Plan Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/plans` | Full plans page |
| GET | `/plans/_list` | Plan list fragment (filtered) |
| GET | `/plans/_editor/_new` | New plan editor form |
| POST | `/plans/_editor` | Create new plan |
| GET | `/plans/_editor/{planId}` | Load existing plan editor |
| PUT | `/plans/_editor/{planId}` | Update plan editor |
| POST | `/plans/_editor/{planId}/finalize` | Finalize task |
| POST | `/plans/_editor/{planId}/inputs` | Add input field |
| PUT | `/plans/_editor/{planId}/inputs` | Update input fields |
| DELETE | `/plans/_editor/{planId}/inputs/{index}` | Remove input field |
| POST | `/plans/_editor/{planId}/outputs` | Add output field |
| PUT | `/plans/_editor/{planId}/outputs` | Update output fields |
| DELETE | `/plans/_editor/{planId}/outputs/{index}` | Remove output field |
| POST | `/plans/_editor/{planId}/steps` | Add step |
| PUT | `/plans/_editor/{planId}/steps` | Update steps |
| DELETE | `/plans/_editor/{planId}/steps/{index}` | Remove step |
| POST | `/plans/_editor/{planId}/deliverables` | Add deliverable |
| PUT | `/plans/_editor/{planId}/deliverables` | Update deliverables |
| DELETE | `/plans/_editor/{planId}/deliverables/{index}` | Remove deliverable |
| POST | `/plans/_editor/{planId}/validation` | Add validation criterion |
| PUT | `/plans/_editor/{planId}/validation` | Update validation criteria |
| DELETE | `/plans/_editor/{planId}/validation/{index}` | Remove criterion |
| POST | `/plans/_editor/{planId}/assumptions` | Add assumption |
| PUT | `/plans/_editor/{planId}/assumptions` | Update assumptions |
| DELETE | `/plans/_editor/{planId}/assumptions/{index}` | Remove assumption |
| GET | `/plans/_submit-form/{planId}` | Submit-to-agent form |
| POST | `/plans/_submit/{planId}` | Execute submit-to-agent |
| GET | `/plans/_editor/{planId}/chat-prompt-fragment` | Chat prompt generator |

### 5.2 JSON API Endpoints (used internally)

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/plans` | List all plans (JSON) |
| GET | `/api/plans/{planId}` | Get plan (JSON) |
| POST | `/api/plans` | Create plan via JSON |
| PUT | `/api/plans/{planId}` | Update plan via JSON |
| POST | `/api/plans/{planId}/submit` | Submit to agent (JSON) |
| GET | `/api/plans/{planId}/runs` | List runs |
| GET | `/api/plans/runs/{runId}` | Get run detail |

---

## 6. Defects Found

### DEFECT-03-01: PlanFieldType selection not persisted (valueOf wireName mismatch)

**Severity:** Medium  
**File:** `OrchestrationController.java`, line 828  
**Root Cause:** `PlanFieldType.valueOf(typeStr)` is called with the HTML select value (wireName like "user_message"), but `valueOf()` expects the Java enum constant name ("USER_MESSAGE"). The `IllegalArgumentException` is caught and falls back to `existing.type()`, which defaults to STRING.

Also affects line 819 where `String typeStr = params.getOrDefault(kind + "Type" + index, existing.type().name())` — the fallback uses `existing.type().name()` (e.g., "STRING") but the select values use wireNames (e.g., "string"). The fallback value would also fail `valueOf` matching.

**Fix:** Replace `PlanFieldType.valueOf(typeStr)` with `PlanFieldType.fromWireName(typeStr)`. The `fromWireName` method already handles both wireName and enum name matching.

**Evidence:**
```bash
# PUT with inputsType0=user_message, response still shows:
<option value="string" selected>string</option>
# API confirms:
"type": "string"
```

### DEFECT-03-02: Steps and deliverables silently discarded when added with empty text

**Severity:** Medium  
**File:** `OrchestrationController.java` (addListItem + PlanService.cleanSteps/cleanList)  
**Root Cause:** `addListItem` creates items with empty text (`new PlanStep(items.size() + 1, "")` for steps, `items.add("")` for deliverables). `PlanService.saveTask` calls `cleanSteps()` which filters out steps where `!StringUtils.hasText(step.text())`, and `cleanList()` which filters out null/blank strings. The items are added but immediately removed on save.

**Evidence:**
```bash
curl -s -X POST http://localhost:18080/plans/_editor/{id}/steps
# Response: "None defined." — step created with empty text, filtered by cleanSteps
curl -s http://localhost:18080/api/plans/{id} | jq '.steps'
# []  — empty
```

### DEFECT-03-03: Model wrote outputs to wrong filesystem path

**Severity:** High (blocks output collection)  
**Root Cause:** Model wrote to `/home/hickelpickle/.magenta/root/` instead of `/output/` inside the container. The plan defined the output directory as `/output` (the container mount point), but the model used the host filesystem path. This may be a model instruction issue, a working-directory issue in the execution context, or a prompt construction issue.

**Evidence:**
- Expected files at `/output/hello.txt` and `/output/result.json` (inside container)
- Actual files at `/home/hickelpickle/.magenta/root/hello.txt` and `/home/hickelpickle/.magenta/root/result.json`
- Container's `/output` mount at `/home/hickelpickle/.magenta/root/agents/23579fcf-ca99-4862-a2fd-b8eb6073928c/outputs/` is EMPTY
- Files have correct content, just wrong location

### DEFECT-03-04: All plans auto-approved on save

**Severity:** Low (by design?)  
**File:** `PlanService.java`, line 480  
**Root Cause:** `saveTask` hardcodes `PlanStatus.APPROVED`. Plans created as `PlanStatus.DRAFT` in the controller are overridden to APPROVED on save.

```java
return planRepository.saveDefinition(new PlanDefinition(
    id,
    PlanKind.TASK_TEMPLATE,
    PlanStatus.APPROVED,  // hardcoded
    ...
));
```

### DEFECT-03-05: Output file naming mismatch

**Severity:** Low  
**Root Cause:** The output value stored in run data uses the model-reported name (`"hello.txt"`), but the actual file on disk uses the field name prefix (`hello_file.txt`). The framework saves output as `<fieldname>.txt` regardless of what the model reports. This creates confusion when the model output value doesn't match the actual filename.

---

## 7. Requires Browser Interaction

The following checks require Playwright MCP (unavailable this run):

1. JavaScript module loading of `/js/orchestration/plans.js?v=2` — verify in console
2. HTMX event handling (afterSwap, afterSettle) for sidebar/nav updates
3. Real-time SSE subscriptions for run execution streaming
4. Drag-and-drop step reordering (planned JS affordance per plans.js comment)
5. Mobile sidebar toggle behavior
6. "Copy & Open Chat" button functionality (clipboard + new tab)
7. Visual layout verification of the browser-layout-wide grid

---

## 8. curl Command Log

### Pre-flight
```bash
# Check plans page
curl -s http://localhost:18080/plans

# Check container state
curl -s --unix-socket /run/user/1000/podman/podman.sock http://d/v5.0.0/libpod/containers/json?all=true

# List agents
curl -s http://localhost:18080/agents/_list
```

### Plan Creation
```bash
# New plan editor
curl -s http://localhost:18080/plans/_editor/_new

# Create plan
curl -s -X POST http://localhost:18080/plans/_editor -d "kind=TASK_TEMPLATE" -d "title=E2E Docker Validation Plan" -d "summary=..." -d "goal=..." -d "notes=..." -d "workTypeProfile=CODING_CENTRIC"

# Load persisted editor
curl -s http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33

# Plan list
curl -s http://localhost:18080/plans/_list
```

### Input/Output Fields
```bash
# Add input
curl -s -X POST http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/inputs

# Update input (type bug)
curl -s -X PUT http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/inputs -d "inputsName0=task_description" -d "inputsType0=user_message" -d "inputsRequired0=on" -d "inputsDesc0=The task the agent should perform"

# Add output
curl -s -X POST http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/outputs

# Update output
curl -s -X PUT http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/outputs -d "outputsName0=hello_file" -d "outputsType0=file_path" -d "outputsRequired0=on" -d "outputsDesc0=Path to the generated hello.txt file"

# Delete field
curl -s -X DELETE http://localhost:18080/plans/_editor/5ccf00b0-f033-466c-8844-247e227e5f33/inputs/1
```

### Submit and Execute
```bash
# Wake container
curl -s -X POST http://localhost:18080/agents/_docker/23579fcf-ca99-4862-a2fd-b8eb6073928c/start?view=list

# Submit form
curl -s http://localhost:18080/plans/_submit-form/5ccf00b0-f033-466c-8844-247e227e5f33

# Submit to agent
curl -s -X POST http://localhost:18080/plans/_submit/5ccf00b0-f033-466c-8844-247e227e5f33 -d "agentId=23579fcf-ca99-4862-a2fd-b8eb6073928c" -d "input_task_description=Write a Python script..." -d "priority=0"

# Monitor run
curl -s http://localhost:18080/api/plans/runs/c4ab1c43-da3c-4377-98e6-9b00f2157f93
```

### Verification
```bash
# Agent queue
curl -s http://localhost:18080/agents/_detail/23579fcf-ca99-4862-a2fd-b8eb6073928c/queue

# Agent outputs
curl -s http://localhost:18080/agents/_detail/23579fcf-ca99-4862-a2fd-b8eb6073928c/outputs

# Output files
ls -la /home/hickelpickle/.magenta/root/agents/system/outputs/e2e-docker-validation-plan-*/
cat /home/hickelpickle/.magenta/root/hello.txt
cat /home/hickelpickle/.magenta/root/result.json

# Container state
podman ps
podman top magenta-agent-23579fcf-ca9
```

---

## 9. Summary

| Check | Result |
|-------|--------|
| Plan page loads | PASS |
| New plan editor renders | PASS |
| Plan creation via POST | PASS |
| Plan list shows created plans | PASS |
| Plan editor reload persists fields | PASS |
| Input field add/update/delete | PASS (type bug) |
| Output field add/update/delete | PASS (type bug) |
| Model override dropdowns populated | PASS |
| Submit-to-agent form with runtime inputs | PASS |
| Container wake from stopped state | PASS |
| Run lifecycle QUEUED->RUNNING->COMPLETED | PASS |
| Output values recorded in run data | PASS |
| Agent outputs tab shows run outputs | PASS |
| Container remains running post-execution | PASS |
| Chat prompt fragment generation | PASS |
| Finalize button present | PASS |
| PlanFieldType selection persists | FAIL (DEFECT-03-01) |
| Steps POST creates step | FAIL (DEFECT-03-02) |
| Deliverables POST creates deliverable | FAIL (DEFECT-03-02) |
| Model writes to /output (container mount) | FAIL (DEFECT-03-03) |
| Output files contain requested content | PASS (wrong path) |
| result.json created alongside hello.txt | PASS (wrong path) |
| No-input plan creation and submit | PASS |

**Status:** 20 PASS, 4 FAIL (3 code defects + 1 model path issue), 7 browser-required
