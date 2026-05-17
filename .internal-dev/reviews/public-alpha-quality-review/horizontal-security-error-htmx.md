# Horizontal Security/Error/HTMX Review

## Agent

- Agent: horizontal-security-error-htmx
- Agent id: `019e3721-2ca9-7fe2-95c3-53113f123541`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed security/path confinement, auth/CSRF/public mutation exposure, error handling/logging, HTMX-vs-JS policy, SimplyPages style, and stale Docker references.

## Files and Routes Reviewed

- Files: `pom.xml`, `application.yml`, web controllers, `GlobalExceptionHandler`, `AgentProfileService`, `WorkspaceService`, `WorkspaceDirectoryService`, `OutputArtifactService`, `AgentShellToolService`, `RuntimeSettingsService`, orchestration JS/CSS, `docs/setup/container-runtime.md`.
- Routes: mutation endpoints, shell exec UI, hard delete, settings, agent/profile/job/project/workspace routes.

## Commands and Probes

- Static `rg` route/security/path/HTMX/Docker scans
- `rg --files -g AGENTS.md`
- Targeted `nl -ba` reads
- Mutation endpoint inventory via mapping annotations
- Stale Docker reference scans

## Findings

- Critical: public unauthenticated mutation/control surface includes shell execution and destructive lifecycle operations.
- Critical: agent IDs are path segments but accept traversal-like values, enabling data-root layout escape and possible durable deletion inside `dataRoot`.
- Medium: HTMX/SimplyPages policy is violated by the live workflow JS island that builds raw HTML and performs CRUD via `fetch`.
- Medium: HTMX fragment handlers often swallow failures as 200 OK HTML instead of using HTTP error semantics/logging.
- Medium: stale Docker/Podman surfaces remain even though filesystem runtime is authoritative.

## Explicitly Ruled Out

- Normal shell working-directory path resolution did not show a direct escape outside `dataRoot`.
- Output artifact read path escape was not found.
- Chat rendered HTML is sanitized server-side by the markdown renderer.
- Workflow list/draft entry itself is HTMX-backed; the issue is the additional JS graph composer.
