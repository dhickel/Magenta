# Baseline Command Log

## Agent

- Agent: main Codex campaign coordinator
- Model / reasoning: current parent Codex session
- Scope: setup, branch state, tool availability, route/package/schema inventory

## Commands Run

| Command | Result |
| --- | --- |
| `pwd` | `/home/hickelpickle/Code/Java/magenta2` |
| `git status --short --branch` | Branch is `public-alpha-quality-review`; worktree already contained many `.internal-dev` archive renames and `AM .internal-dev/notes/idea_drop.md` before campaign files were created. These are treated as unrelated existing changes. |
| `git branch --show-current` | Started on `agent-queue-delete-live-audit-transcript`, then created `public-alpha-quality-review`. |
| `java -version` | OpenJDK `25.0.3` 2026-04-21, Red Hat build. |
| `mvn -version` | Apache Maven `3.9.11`, Java `25.0.3`, Linux `6.18.10-200.fc43.x86_64`. |
| `npx playwright --version` | Playwright `1.59.1` available. |
| `rg -n "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)" ...` | Route inventory captured in `route-page-inventory.md`. |
| `find src/main/java/io/mindspice/magenta2 -maxdepth 5 -type d` | Package inventory captured in `package-inventory.md`. |
| `rg -n "CREATE TABLE|create table|ALTER TABLE|alter table" ...` | Table/schema inventory captured in `db-table-inventory.md`. |
| `rg --files src/test` | Test inventory captured in validation ledger scope. |
| `rg -n "Docker|docker|Podman|podman|filesystem|runtime" ...` | Found filesystem-runtime code comments plus stale container setup docs and several `agent-docker-status` DOM ids in the public UI controller. |

## Baseline Observations

- The active public route surface is concentrated in `FrontendController` (`/`, `/chat`) and `OrchestrationController` (`/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/inbox`, `/outputs`, `/agents`, `/agents/{agentId}`, `/settings`), with API controllers under `/api/**`.
- `src/main/resources/application.yml` documents Docker runtime removal in favor of filesystem-backed workspaces.
- `docs/setup/container-runtime.md` still describes Podman/Docker setup and appears stale against the plan assumption. This is a documentation quality target, not a runtime validation gate.
- `OrchestrationController` still contains `agent-docker-status` DOM target ids despite filesystem-backed runtime comments. This is a UI/docs quality target unless validation proves a broken interaction.
