# Filesystem Agent Runtime Refactor

## Purpose

This suite replaces the Docker-backed execution model with a host-filesystem execution model rooted at the configured `AiConfig.dataRoot()`. It is a breaking refactor: containers, container lifecycle controls, Docker runtime APIs, Docker UI language, and Docker-specific validation are removed rather than maintained as a second mode.

The replacement contract is:

- every agent has one host-side workspace under `dataRoot`;
- shell execution runs in Bash on the host, confined to managed workspace paths;
- agent health is reported from workspace state rather than container state;
- deliverables are written under the agent workspace's `outputs/` tree;
- project work remains lease-governed and is surfaced inside the agent workspace through managed project links;
- the UI exposes workspace readiness, activity, leases, outputs, and shell execution, not Docker lifecycle controls.

## Source Inputs

- User direction from 2026-05-15: remove Docker, execute in Bash, operate from the configured root directory, give each agent its own workspace folder, monitor workspaces instead of containers, include `outputs/`, and remove Docker from the UI.
- Current implementation in:
  - `ai.orchestration.docker.*`
  - `ai.orchestration.workspaces.*`
  - `ai.chat.tool.shell.AgentShellToolService`
  - `api.web.OrchestrationController`
  - `api.web.RuntimeController`
  - `src/main/resources/application.yml`
- Existing workspace/project lease behavior and output materialization services.

## Target File Structure

Use `AiConfig.dataRoot()` as the absolute root. Do not introduce a second runtime root.

```text
<dataRoot>/
  agents/
    <agentId>/
      workspace/
        projects/
          <projectId> -> ../../../projects/<projectId>/workspace
        outputs/
          <run-slug>-<runId>/
        scratch/
  projects/
    <projectId>/
      workspace/
  jobs/
    <jobId>/
      workspace/
      outputs/
        <run-slug>-<runId>/
  runtime/
    task-runs/
      <runId>/
    workflow-runs/
      <runId>/
```

Rules:

- `agents/<agentId>/workspace/` is the agent execution root.
- `agents/<agentId>/workspace/outputs/` is the durable output root for agent-owned work.
- `agents/<agentId>/workspace/projects/<projectId>` is a managed link to the canonical project workspace while the agent has a valid project lease.
- `scratch/` is agent-private temporary working space; temp task/workflow directories remain under `runtime/` until the runtime cleanup path is intentionally changed.
- Project membership and lease rules stay in force; removal of Docker must not weaken project isolation semantics.

## Suite Order

Run these as serialized implementation packages. The user explicitly wants each package to block on the one before it and produce an implementation-specific handoff for the next agent.

1. `01-filesystem-layout-and-config-contract.md`
2. `02-bash-execution-runtime.md`
3. `03-workspace-monitoring-and-output-routing.md`
4. `04-ui-and-public-contract-removal.md`
5. `05-docker-deletion-and-migration-cleanup.md`
6. `06-final-validation-gate.md`

Use `00-orchestration-plan.md` as the binding dispatcher contract. Every phase must append to `phase_handoff_notes.md` before the next phase starts.

## Shared Rules

- Breaking refactor means no long-term dual Docker/filesystem mode.
- Controllers stay thin; services own execution, monitoring, validation, and persistence behavior.
- Host execution must remain path-confined under `dataRoot` and must not accept arbitrary absolute working directories.
- Preserve the existing project lease model and project membership guardrails.
- Keep SimplyPages/HTMX as the default for standard UI interactions; use JavaScript only where it is already justified or clearly simpler.
- Do not broaden scope into new orchestration features while removing Docker.
