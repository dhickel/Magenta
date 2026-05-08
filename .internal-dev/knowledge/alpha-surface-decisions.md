# Alpha Surface Decisions

Date: 2026-05-08

## Overview

This document records the alpha-facing decisions for workflow, schedule, and event reaction surfaces. These decisions follow the principle from remediation 5.4: "Make the smallest honest alpha decision. Hiding an experimental surface is better than hardening a feature that is not part of the alpha promise."

## Decision Table

| Surface | Decision | Rationale |
|---------|----------|-----------|
| Workflow | Productize as alpha-facing | Proper domain objects, streaming, controller tests, UI integration. Hardened in remediations 2.1, 2.2, 4.1. |
| Schedules | Hidden behind feature flag | Generic map DSL (`Map<String, Object> assignmentTemplate`), no individual CRUD (no PUT/DELETE), template-based approach is prototype-shaped. Default disabled via `magenta.features.schedules-enabled=false`. |
| Event Reactions | Hidden behind feature flag | Generic map DSL (`Map<String, Object> filter`, `Map<String, Object> assignmentTemplate`), only supports `ENQUEUE_ASSIGNMENT` action type, no individual CRUD. Default disabled via `magenta.features.reactions-enabled=false`. |

## Feature Flag Implementation

Feature flags are defined in `application.yml`:

```yaml
magenta:
  features:
    schedules-enabled: false
    reactions-enabled: false
```

The `AgentOrchestrationController` checks these flags before serving schedule and event reaction endpoints. When disabled, endpoints return HTTP 404 with a message explaining the feature is not available.

The `FrontendController` agent detail page removes "Schedules" and "Event Reactions" tab buttons and their dashboard counts from the UI.

## What Remains Active

The following surfaces are intentionally kept active during alpha:
- **Inbox** (`/api/agents/{agentId}/inbox`) — Core messaging capability
- **Assignments/Queue** (`/api/agents/{agentId}/assignments`) — Core work dispatch
- **Agent chat** (`/api/agents/{agentId}/chat/stream`) — Core agent interaction
- **Jobs** (`/api/agents/{agentId}/jobs`, `/api/jobs`) — Core orchestration
- **Workflows** (`/api/workflows`) — Productized
- **Workspace** (`/api/agents/{agentId}/workspace`) — Core agent workspace
- **Runtime settings** (`/api/settings/runtime`) — Core configuration

## Future Considerations

- **Schedules**: If re-enabled for v1, the `Map<String, Object> assignmentTemplate` should be replaced with a typed DTO. Individual PUT/DELETE endpoints should be added.
- **Event Reactions**: If re-enabled for v1, the `Map<String, Object> filter` and `Map<String, Object> assignmentTemplate` should be replaced with typed DTOs. Additional action types may be needed. Individual PUT/DELETE endpoints should be added.
- Both surfaces can be re-enabled at any time by setting their feature flag to `true` in `application.yml`, but doing so without hardening would expose alpha users to the prototype-shaped API described above.
