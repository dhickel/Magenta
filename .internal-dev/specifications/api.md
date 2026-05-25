---
schema_version: 1
document_type: api-specification
status: active
owner: api
created: 2026-05-25
---

# API Specification

## Intended Contract

API routes should expose stable request/response shapes, controlled status codes, and compatibility behavior. Controllers should stay thin and delegate use-case behavior to services.

## API Entries

| id | route_or_surface | status | intended_contract | payload_or_status | compatibility_rule | validation | related_decisions | related_knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| API-20260525-01 | Work Area file APIs | active | File create, move, copy, delete preflight/execute, labels, recent action rows, preview, save, and image view return controlled responses. | `400` validation, `404` missing, `409` collision where distinguishable. | Preserve compatibility delete endpoint until deliberate removal. | Controller tests and API docs updates. | `DECISION-20260524-03` | `workspace-api-list-and-agent-tab-operational-pattern.md` |
| API-20260525-02 | Chat surface values | active | Known chat surface enum values accept case-insensitive names and reject blank/unknown values. | JSON boundary normalization. | Keep normalization narrow at the JSON boundary. | Controller/API tests. | `DECISION-20260523-05` | `api-boundary-enums-and-empty-job-run-completion.md` |
| API-20260525-03 | Empty submitted jobs | active | Empty submitted jobs complete as no-op job runs unless product policy later changes to reject them at submission. | Terminal job run instead of `RUNNING`. | Do not change without explicit product decision. | Assignment/job tests. | `DECISION-20260523-05` | `api-boundary-enums-and-empty-job-run-completion.md` |
| API-20260525-04 | Direct task run SSE | deferred | Consider native reactive SSE or explicit executor-backed execution if concurrency requirements grow. | SSE stream behavior. | Current blocking bridge is acceptable for current scope. | SSE tests and startup. | none | `plan-execution-stream-finalization.md` |
| API-20260525-05 | Workspace list endpoint | deferred | Add or stabilize workspace listing APIs before relying on dashboard-level workspace browser panels. | `GET /api/workspaces` or equivalent. | Avoid inventing UI around missing route contracts. | Controller/API tests and docs. | none | `workspace-api-list-and-agent-tab-operational-pattern.md` |

## Ownership Boundary

This file owns route and payload contracts. Service behavior belongs in `services.md`; page/fragment behavior belongs in `web.md`.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-05 | open | Workspace file explorer status mapping is partly message based. | Deferred typed domain errors before expanding compatibility claims. | 2026-06-23 |

## Validation Expectations

API changes require focused controller/API tests, docs updates for route or payload changes, and bounded startup when Spring wiring changes.

## Related Decisions

`DECISION-20260523-05`, `DECISION-20260524-03`.

## Related Knowledge

Search knowledge filenames for `api`, `workspace`, `enum`, `empty-job`, `sse`, and `workflow`.
