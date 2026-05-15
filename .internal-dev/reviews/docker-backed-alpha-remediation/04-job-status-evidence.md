# 04: Job and Project Status Evidence

## Scope
Validate project creation, job creation with plan items, job submission to agent, and job status transitions.

## Project Creation

### Project: "Alpha Validation Project"
- ID: `add59203-1047-48f5-b6c7-e219075c8b0f`
- Owner agent: `9d948907-7ce1-4621-ade8-662dcb1db129` (magenta)
- Created via `POST /projects/_editor` with required `ownerAgentId`
- PASS

## Job Creation

### Job: "Alpha Validation Job"
- ID: `81f1083f-eb96-4839-bc4a-daa0bd730054`
- Owner agent: `9d948907-7ce1-4621-ade8-662dcb1db129`
- Project: `add59203-1047-48f5-b6c7-e219075c8b0f`
- Status: DRAFT (initial)
- Created via `POST /jobs/_editor` with required `title`, `ownerAgentId`, `projectId`
- PASS

### Job Item
- Item key: `e3941b1f-ba22-47da-b54f-4de5fc132a69`
- Item type: PLAN
- Plan ID: `522e31b4-aced-4d60-b21d-a0ecd92ab44e`
- Added via `POST /jobs/_editor/{jobId}/items`
- PASS

## Job Submission and Status Transitions

### Submit to Agent
- `POST /jobs/_submit/{jobId}` with `agentId=9d948907...`
- Assignment ID: `af45bf44-0cf5-4d9d-81b0-6a26db7e3bdc`
- Initial status: QUEUED

### Status Transitions Observed
```
DRAFT -> QUEUED -> RUNNING -> COMPLETED
```

- QUEUED: immediate after submission
- RUNNING: picked up by OrchestrationRunnerService after ~1s
- COMPLETED: after plan execution completed (~93s total from submission)

### Completion Details
- currentItemIndex: 1 (all items completed)
- Checkpoint: `completedItemKey: e3941b1f-ba22-47da-b54f-4de5fc132a69`, `model: qwen3.6:35b`
- Output values: field_1 and field_2 with expected content
- Evidence: item completion recorded with itemKey and completedAt timestamp
- errorText: null (no errors)

### Dashboard Agreement
Agent dashboard shows:
- Queue: 4 (pending assignments, correct)
- Inbox: 0
- Jobs: 1 (correct)

## Verdict
PASS:
- Project creation with agent ownership: PASS
- Job creation with plan items: PASS
- Status transitions DRAFT -> QUEUED -> RUNNING -> COMPLETED: PASS
- Output values produced for job items: PASS
- Checkpoint and evidence recorded: PASS
- Dashboard stats agree: PASS

Note: Plan execution within the job ran through agent=system (same non-Docker path as Step 3).
