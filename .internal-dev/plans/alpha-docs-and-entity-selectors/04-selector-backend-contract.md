# Selector Backend Contract

## Context

Current operational UI has repeated ad hoc entity selection:

- Some fields use static server-rendered selects, such as `agentSelect(...)` and model selects.
- Some fields require manual IDs, such as workspace IDs, project IDs, plan/workflow IDs in job items, schedule job IDs, and the agent submit target ID.
- Validation happens late in service calls or guidance endpoints rather than consistently at selection time.

The fix should not scatter one-off search endpoints across every page. Add a shared lookup contract in the web layer backed by existing services.

## Goal

Create a reusable backend option read model and HTMX fragment/API endpoints for selecting existing Magenta entities.

## In Scope

- Entity kinds: `agent`, `plan`, `task`, `workflow`, `job`, `project`, `workspace`, `model`.
- Optional later kind if cheap and needed by outputs filters: `run`.
- Search by id, title/name/label, model key, status, and common detail fields.
- Validate that a manually entered ID exists.
- Preserve missing current values when editing saved records.
- Keep controllers thin.

## Out of Scope

- New database indexes unless performance proves necessary.
- Fuzzy search libraries.
- Security bypasses around unsafe mutations.
- Replacing existing JSON REST list endpoints.

## Target Design

Add a small selector package under the web layer, for example:

```text
src/main/java/io/mindspice/magenta2/api/web/selector/
  EntityKind.java
  EntityOption.java
  EntityLookupService.java
  EntitySelectorController.java
  EntitySelectorComponents.java
```

Records:

```java
public enum EntityKind {
    AGENT, PLAN, TASK, WORKFLOW, JOB, PROJECT, WORKSPACE, MODEL
}

public record EntityOption(
    String kind,
    String id,
    String label,
    String detail,
    String status,
    boolean available
) {}

public record EntityValidation(
    String kind,
    String id,
    boolean exists,
    String label,
    String message
) {}
```

Suggested endpoints:

- `GET /selectors/{kind}/options?q=&limit=&current=&agentId=&projectId=&assignmentType=&itemType=`
  - Returns HTML option rows for HTMX.
- `GET /selectors/{kind}/validate?id=`
  - Returns a compact validation fragment.
- `GET /api/selectors/{kind}?q=&limit=`
  - Optional JSON endpoint only if tests or non-HTMX clients need it.

Manual validation behavior:

- Blank optional values return neutral status.
- Blank required values return "required".
- Unknown nonblank values return "not found".
- Known values return "selected" with label/detail.

## Implementation Steps

1. Read closest guides.
   - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/chat/plan/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

2. Implement `EntityKind`.
   - Parse path values case-insensitively.
   - Use simple wire names like `agent`, `plan`, `task`, `workflow`, `job`, `project`, `workspace`, `model`.

3. Implement `EntityLookupService`.
   - Inject existing services:
     - `AgentProfileService`
     - `PlanService`
     - `TaskService` or `PlanService.listTasks()` depending current task/plan boundary
     - `WorkflowService`
     - `JobService`
     - `ProjectService`
     - `WorkspaceService`
     - `ChatService` for models
   - Provide:
     - `List<EntityOption> search(EntityKind kind, SelectorQuery query)`
     - `EntityValidation validate(EntityKind kind, String id, boolean required)`
   - Keep matching simple:
     - lower-case contains against id, label, detail, status.
     - default limit 20, maximum 50.
   - For unavailable records:
     - disabled agents should appear only if current value equals the disabled agent or when `includeUnavailable=true`.
     - missing current values should be rendered as `available=false`.

4. Implement `EntitySelectorController`.
   - Return fragments for HTMX, built with SimplyPages components.
   - Keep unsafe operations out of this controller.
   - Ensure public GET behavior is acceptable under alpha security because it exposes only already visible list data.

5. Add focused tests.
   - Search each kind with empty query.
   - Search filters by title/name/id.
   - Validate existing and missing IDs.
   - Preserve current missing value in option rows where applicable.
   - Limit behavior caps large lists.

## Validation

- `mvn -Dtest=*Entity*Selector* test` or the exact focused test class.
- Existing controller tests still pass.
- Manual curl/browser GET to `/selectors/agent/options?q=` returns an HTML fragment without stack trace.
- Unknown kind returns a controlled 400/404, not a 500.

## Exit Criteria

- Selector UI work can consume one shared backend contract for all major entity kinds.

