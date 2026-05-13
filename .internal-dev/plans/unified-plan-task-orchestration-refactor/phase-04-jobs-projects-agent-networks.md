# Phase 04 - Jobs, Projects, and Agent Networks

## Context

Jobs and projects should coordinate larger units of work around tasks and workflows. Existing jobs are durable but thin; projects and agent networks are not yet modeled.

## Goal

Build the durable management layer for agents, jobs, projects, schedules, and project-local agent networks.

## In Scope

- Replace MVP job schema with job/workload records that reference unified plan runs and workflow runs.
- Add project schema and service.
- Add project network membership and inbox routing.
- Add management prompt profiles and model/settings override precedence.
- Add repeated job support through schedules.

## Out of Scope

- Advanced sprint/milestone project management.
- External git automation beyond storing linked repository metadata and mounting project workspace.
- Rich UI beyond minimal APIs for Phase 05.

## Implementation Steps

1. Define job records:
   - `JobDefinition`
   - `JobRun`
   - `JobWorkItem`
   - `JobRunStatus`
   - `JobRecurrence`
2. Define project records:
   - `Project`
   - `ProjectAgentMembership`
   - `ProjectNetwork`
   - `ProjectEvent`
3. Replace job schema and add project schema:
   - `jobs`
   - `job_items`
   - `job_runs`
   - `projects`
   - `project_agent_memberships`
   - `project_network_messages`
4. Implement job service.
   - Jobs own persistent job workspace and job outputs.
   - Job items can run plans or workflows.
   - Child task/workflow outputs route to job output directory, not agent output directory.
   - Job run progress is computed from child work items.
5. Implement project service.
   - Every project has exactly one owner agent.
   - A project can link a git repository path or URL as metadata.
   - Project workspace is persistent and can be leased into agent containers.
6. Implement agent network behavior.
   - Agents assigned to the same project can message each other through inboxes.
   - Agents outside the project network cannot send project-scoped messages unless explicitly allowed by an admin route.
7. Implement prompt profiles:
   - `research`
   - `coding`
   - `writing`
   - `technical_writing`
   - `validation`
   - `management`
   - `general`
8. Implement settings precedence:
   - run override;
   - workflow node or job item override;
   - workflow or job default;
   - project default;
   - agent default;
   - runtime default.
9. Preserve durable runtime mechanics:
   - lease stale `RUNNING` assignments;
   - resume at job item or workflow node boundary;
   - do not attempt partial model response resume.

## Validation

- Repository tests for jobs, projects, memberships, and project network messages.
- Service tests:
  - project must have owner agent;
  - one agent can belong to multiple projects;
  - project network permits same-project messaging;
  - outside-project messaging is rejected;
  - job output routing writes to job output directory;
  - repeated job schedule creates new job run;
  - model precedence resolves in the documented order.
- Runtime tests for stale lease recovery and job-boundary resume.

## Exit Criteria

- Jobs coordinate task/workflow workloads and own persistent workspace/output routing.
- Projects are durable data-space and tracking wrappers with owner agents.
- Agent project networks gate agent-to-agent communication.
- Prompt profiles and model precedence are deterministic and tested.

