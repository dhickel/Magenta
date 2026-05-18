# Phase 06 - Final Validation Gate

## Context

This is a validation-only phase. It runs after Phases 01-05 are implemented, merged, and handed off. The validator should not perform production-code remediation as the main path; failures go back to the owning phase agent unless the orchestrator explicitly reassigns them.

## Goal

Prove that Docker has been removed from the active application contract and that filesystem-backed agent execution works end to end.

## In Scope

- Focused phase tests.
- Full automated test suite.
- Bounded Spring Boot startup.
- Browser validation of the affected operator flows.
- Filesystem inspection of actual output placement.
- Final `.internal-dev` closeout recommendation.

## Out Of Scope

- Shipping new runtime behavior as part of validation.
- Silently fixing production code in the validation lane instead of returning failures to the owning phase.

## Implementation Steps

1. Read every completed entry in `phase_handoff_notes.md`; stop if any phase lacks a completed handoff or has an unresolved blocker.
2. Run focused tests from each implementation phase.
3. Run `mvn test`.
4. Run bounded startup without Docker flags or daemon assumptions:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

5. Before browser work that touches live agent/task/SSE behavior, read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` and use Playwright MCP first.
6. Browser flow:
   - open `/agents` and confirm no Docker column/actions remain;
   - open one agent detail page and confirm workspace status, outputs, and shell exec surfaces render;
   - execute `pwd` or another allowed bounded command through the shell exec form and confirm the shown path is the workspace contract;
   - submit or run a representative task that creates an artifact;
   - verify the artifact appears in UI and on disk under `agents/<agentId>/workspace/outputs/<run...>/`;
   - verify linked project workspace facts display when a project lease is active.
7. Sweep for stale active references:

```bash
rg -n 'magenta\.docker|/_docker/|Docker Runtime|Container Exec|AgentContainerRuntimeService|DockerRuntimeClient' \
  src/main src/test src/main/resources README.md .internal-dev/knowledge .internal-dev/scripts
```

8. Confirm JavaScript usage did not expand unnecessarily; ordinary refresh/CRUD behavior remains HTMX-first.

## Validation

- Every step above must pass or block with exact evidence.
- The validator must include command outputs, browser evidence, and filesystem observations in the handoff.

## Closeout

After validation passes:

- write a changelog entry;
- add reusable knowledge for the new filesystem runtime and workspace monitoring contract;
- add any user-approved deferrals to notes;
- archive the finalized plan suite only when all blockers are resolved and the user wants it archived.

## Exit Criteria

- Startup succeeds without Docker or Podman.
- Bash-backed agent execution works inside the configured filesystem workspace.
- Durable outputs land in the workspace `outputs/` tree.
- Workspace monitoring replaces Docker monitoring in operator-facing flows.
- No active source, config, or UI path still depends on Docker.
