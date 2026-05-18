# Phase 05 - Docker Deletion And Migration Cleanup

## Context

After earlier phases migrate active consumers, Docker should become dead code. Leaving it compiled, configured, or documented would create a misleading dual-mode system and future regression risk.

## Goal

Remove Docker-specific implementation and dead migration scaffolding so the codebase has one runtime story.

## In Scope

- Delete `ai.orchestration.docker.*` and all surviving production references.
- Delete obsolete Docker tests, scripts, config, docs, and knowledge pages or move historical-only materials out of active guidance.
- Remove stale migration compatibility after the new workspace layout is proven.
- Remove Docker-specific live-validation gates from current plans/knowledge that future agents would otherwise follow.

## Out Of Scope

- Rewriting historical review artifacts that should remain as history.
- Deleting archived evidence simply because it mentions Docker.

## Implementation Steps

1. Delete the Docker runtime package once `rg` shows no active dependency remains.
2. Remove active config, scripts, endpoint docs, CSS, and tests that only exist for Docker.
3. Update or retire active knowledge files such as Docker host setup guidance so future agents are not instructed to install Podman for normal operation.
4. Keep historical `.internal-dev/reviews/` and archived plans intact unless repo policy says to archive them; they are evidence, not active instructions.
5. Remove temporary migration branches only after tests prove old layouts were migrated or intentionally unsupported.
6. Perform a full stale-reference sweep across active source, active docs, tests, scripts, and current plans.

## Validation

- `rg -n 'docker|podman|container' src/main src/test src/main/resources README.md .internal-dev/knowledge .internal-dev/scripts` leaves only explicitly documented historical or third-party references approved in the handoff.
- Clean compile after deleting the package.
- Focused tests plus `mvn test` before handing to the final validator.

## Exit Criteria

- The application has one active runtime implementation.
- Future maintainers are not pointed back toward Docker by active docs or scripts.
- The final validator receives a codebase that is ready for end-to-end proof, not a hybrid cleanup state.
