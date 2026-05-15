# Final Validation Criteria

## Context

This file defines the acceptance suite for the full unified plan/task orchestration refactor.

## Goal

Prove that the refactor works end to end across schema reset, unified plan/task execution, Docker runtime, workflows/gates, jobs/projects, UI, and `.internal-dev` closeout.

## In Scope

- Automated tests.
- Docker-backed integration tests.
- Startup smoke.
- Live browser validation.
- Durable `.internal-dev` workflow closeout.

## Out of Scope

- Production deployment hardening.
- Multi-host Docker scheduling.
- Advanced project management features beyond this plan suite.

## Implementation Steps

1. Run focused tests for each phase as it lands.
2. Run full test suite:
   - `mvn test`
3. Run bounded startup smoke with Docker available:
   - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
   - Treat timeout exit `124` as acceptable only if logs prove healthy startup.
4. Run Docker integration scenario:
   - create test agent;
   - execute a finalized plan/task in Docker;
   - write a message output and a file output;
   - assert artifacts exist in output directory;
   - assert temp workspace is deleted.
5. Run workflow gate scenario:
   - task node produces output;
   - user approval node pauses;
   - user response resumes;
   - downstream task consumes prior output;
   - final workflow outputs are materialized.
6. Run job/project scenario:
   - create project with owner agent;
   - add second agent to project network;
   - create job attached to project;
   - run workflow through job;
   - assert outputs route to job output directory;
   - assert project-network agent messaging works.
7. Run browser validation:
   - `/chat`
   - `/dashboard`
   - `/plans`
   - `/workflows`
   - `/jobs`
   - `/projects`
   - `/inbox`
   - `/outputs`
   - `/agents`
   - `/settings`
8. Complete `.internal-dev` workflow:
   - changelog entry;
   - reusable knowledge entry;
   - bug reports for out-of-scope findings;
   - notes for deferred ideas after confirmation;
   - archive completed plan artifacts.

## Validation

The implementation passes only if:

- `mvn test` passes.
- Startup smoke reaches healthy Spring Boot startup.
- Docker runtime executes at least one test-agent assignment.
- No declared output can be skipped during completion.
- No-output plans can complete by validating deliverables only.
- Workflow gates persist `WAITING` and resume correctly.
- Job outputs route to job data space.
- Project agent network restrictions are enforced.
- Browser validation finds no blocking UI regressions.

## Exit Criteria

- The unified plan/task model is the only active task abstraction.
- Docker-backed execution is mandatory and working.
- Workflows, jobs, projects, inboxes, outputs, and dashboard UI are dogfoodable.
- The repo has durable `.internal-dev` evidence for what changed and what remains deferred.

