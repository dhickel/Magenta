---
schema_version: 1
document_type: service-graph-specification
status: active
owner: architecture
created: 2026-05-25
---

# Service Graph Specification

## Intended Contract

Services should depend on smaller domain services through explicit interfaces or simple Spring services. Controllers delegate to services; repositories do not leak persistence assumptions into callers; Avatar and web surfaces reuse existing runtime services rather than bypassing them.

## Service Graph Entries

| id | from | to | status | allowed_interaction | boundary_rule | validation | related_decisions |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SVC-20260525-01 | Web/API controllers | Domain services | active | Controllers map HTTP/HTMX/SSE requests into service calls and response payloads. | Controllers should not own filesystem, schema, or runtime policy. | Controller tests plus service tests for changed behavior. | `DECISION-20260524-03` |
| SVC-20260525-02 | Avatar dashboard | Existing chat/tool/runtime/workspace/output services | active | Avatar delegates operational behavior and stores Avatar-only user data separately. | Avatar must not create a second runtime. | Avatar controller tests, startup, browser checks. | `DECISION-20260522-05` |
| SVC-20260525-03 | Work Area explorer UI | `WorkAreaExplorerService` and workspace metadata services | active | UI fragments request service-owned listings, metadata, labels, copy/move/rename/delete/create, and viewer data. | Filesystem confinement and mutation validation stay service-owned. | Work Area service/controller tests. | `DECISION-20260524-03` |
| SVC-20260525-04 | Agent operational tools | Spring AI tool registry and current orchestration context | active | Agent tools resolve exact approved-tool names and current run context. | PLAN/TASK drafting modes exclude operational agent/avatar tools. | Tool registry and chat/orchestration tests. | `DECISION-20260522-06` |
| SVC-20260525-05 | Task/workflow execution | Assignment/runtime/workspace/output services | active | Executable work routes through bounded, observable runtime services. | Planner organizer recurrence is non-automated until separate automation is accepted. | Runtime tests and live validation when required. | `DECISION-20260523-02` |

## Ownership Boundary

This spec owns the allowed direction of dependencies. Individual service behavior belongs in `services.md`; HTTP contracts belong in `api.md`.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-03 | watching | Controller consolidation has been suggested for shell/page builders, but current split remains acceptable until concrete duplication creates maintenance risk. | Horizon idea, not current implementation. | 2026-06-24 |

## Validation Expectations

Changing a service edge requires focused tests for both caller and callee expectations, plus startup validation for application wiring.

## Related Decisions

See `DECISION-20260522-05`, `DECISION-20260522-06`, `DECISION-20260523-02`, and `DECISION-20260524-03`.

## Related Knowledge

Search knowledge filenames for `services`, `workspace`, `orchestration`, `avatar`, `tool`, and `workflow`.
