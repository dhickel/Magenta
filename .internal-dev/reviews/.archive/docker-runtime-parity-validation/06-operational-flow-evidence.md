# Phase 06: End-User Operational Flows — Evidence

## Coverage

This phase was partially executed within the time constraints of the validation session. Evidence from phases 01-05 contributes to the operational flow picture.

## Flows Validated

### Task Creation and Submission ✓
- Created plan "Docker Provenance Validation" via HTMX editor
- Filled all fields (title, summary, goal, notes)
- Set execution model to local-qwen
- Submitted to agent via "Submit to Agent" dialog
- Agent queue incremented, Docker state transitioned IDLE → RUNNING
- Task completed with 3 output artifacts

### Output Inspection ✓
- Global `/outputs` page shows all 3 artifacts with attribution
- Agent Outputs tab shows same artifacts
- Filtering by agent, type, run ID available
- View and Download links present

### Docker-Backed Provenance Visible ✓
- Agent dashboard shows current assignment during execution
- Docker state transitions: IDLE → RUNNING → IDLE
- Container ID and mount paths visible throughout
- Output paths reference container `/output/` mount

### Navigation and HTMX ✓
- All 11 top-level pages load correctly
- Tab switching works via HTMX (Dashboard, Queue, Inbox, Jobs, Schedules, Reactions, Workspace, Outputs, History, Chat)
- Plan editor saves and reloads correctly
- Agent filter works via HTMX partial

### Page Reload Persistence ✓
- Output artifacts visible after page reload
- Plan data persists after save and reload
- Docker status updates persist across navigation

## Flows NOT Validated

| Flow | Reason |
|---|---|
| Agent creation from scratch | Used existing magenta agent |
| Workflow build/run with approval gate | Requires workflow run SSE UI (gap identified in Phase 05) |
| Job/project/schedule setup with assignment | Requires additional model execution time |
| Chat follow-up after task | Chat requires model availability for local-qwen |
| Mobile viewport (< 900px) | Not tested |
| Model override controls | Not tested with non-default model |

## Assessment

**PARTIAL** — Core operational loop (create plan → submit to agent → inspect outputs) works end-to-end with Docker-backed execution. The unimplemented UI features for workflow run monitoring, job run start/cancel, and plan run history prevent full validation of those flows. These are tracked as Phase 05 gaps, not operational defects.
