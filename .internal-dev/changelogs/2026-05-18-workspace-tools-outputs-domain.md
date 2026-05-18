# Workspace Tools Outputs Domain

## Date

2026-05-18

## Change Summary

Completed public alpha remediation domain 02. Workspace-backed shell, file, web fetch, project workspace, allocation, output materialization, and output attribution behavior now match the filesystem runtime contract.

## Scope

- Shell wildcard defaults are non-effective unless the unsafe override is explicitly enabled.
- File and shell tools resolve active assignment workspace, output, and current-project scopes from `OrchestrationTaskContext`.
- `web_fetch` follows redirects manually and validates each hop before sending the next request.
- Project workspaces are materialized into assignment temp workspaces while the project lease is held.
- Workspace/output allocation failures persist terminal failed runs instead of continuing with null paths.
- `file_path` output materialization uses realpath data-root confinement.
- Output attribution recognizes the current `agents/{agentId}/workspace/outputs/...` layout.

## Validation

- Focused domain tests passed: `mvn -Dtest=AgentShellToolServiceTest,ExternalAiConfigLoaderTest,OrchestrationRuntimeTest,ChatToolRegistryTest,AgentFileToolServiceTest,AgentWebToolServiceTest,WorkspacePathSegmentValidationTest,PlanServiceTest,WorkflowRunnerTest,TaskStreamSupportTest,OutputArtifactServiceAttributionTest,WorkspaceRepositoryAttributionTest test` (`169` tests).
- Full `mvn test` passed (`499` tests).
- `git diff --check` passed.
- Bounded Spring Boot startup passed on ephemeral port `37111`.

## Evidence

- `/tmp/domain02-focused-tests.log`
- `/tmp/domain02-full-mvn-test.log`
- `/tmp/domain02-git-diff-check.log`
- `/tmp/domain02-bounded-startup.log`

## Follow-up Items

- Domain 03 execution/history/streams starts after this branch merges into integration.
