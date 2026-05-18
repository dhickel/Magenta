# 2026-05-18 - Stale Runtime Labels

## Date

2026-05-18

## Change Summary

Removed stale active Docker/Podman runtime wording from public-alpha filesystem-runtime surfaces. Active code, tests, config comments, and dependency declarations now describe the filesystem-backed workspace runtime.

## Files

- `pom.xml`
- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AgentWorkspaceStatus.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`

## Behavioral Impact

The application no longer declares unused container-runtime client dependencies or stale test runtime disablement properties. Operator-facing runtime docs/comments now point to filesystem-backed workspace behavior.

## Validation

- Active stale-label scan returned no matches: `rg -n -i "docker|podman|container-runtime|docker-java|magenta\\.docker|agent-docker-status" src/main src/test pom.xml docs README.md --glob '!**/target/**'`.
- `mvn -Dtest=OrchestrationControllerTest,AgentShellToolServiceTest,OutputArtifactServiceAttributionTest test` passed with 125 tests.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite DB `/tmp/domain06-subplan04-parent.sqlite`; log: `/tmp/domain06-subplan04-parent-startup.log`.
- Validation agent passed commit `3a41d0b`: required active-surface scan returned no matches, extra scan including `config/` returned no matches, `pom.xml` had no container-runtime/testcontainers hits, focused tests passed with 125 tests, `git diff --check` and `git show --check 3a41d0b` passed, and bounded startup reached `Started Magenta2Application`. Evidence: `/tmp/domain06-subplan04-startup-pEMW4A/startup.log`.
- Playwright was skipped by validation because no active UI/static stale label hits remained and the subplan made no interaction behavior changes.
- Domain validation later broadened the stale-wording scan and failed commit `9800a0a` on active runtime uses of `container` in source/test comments, parameter names, and fixture names. The serial gate-fix pass removed those flagged active terms while leaving generic HTMX DOM container terminology intact.

## Risks

Historical `.internal-dev` Docker/Podman evidence and explicit remediation-plan text were intentionally left intact.

## Follow-up Items

None.
