# No-Action Registry

## Purpose

This file records review observations that do not need remediation plans because the review explicitly ruled them out as non-findings. It must not be used to skip addressable findings. If the team later chooses to skip or defer an addressable item, record the explicit user decision here and update `finding-inventory.md` and `progress.md`.

## Explicitly Ruled Out By Review

| Source | Ruled-Out Item | Reason |
| --- | --- | --- |
| `domain-api-web.md` | HTMX WebJar shadow route | No active shadow route was found. |
| `domain-api-web.md`, `domain-workspaces-tools-outputs.md` | Output download/content read path escape | Output reads perform path-confinement and realpath checks before reads. |
| `domain-api-web.md` | Chat title/favorite/archive route mismatch | Client routes matched controller mappings. |
| `domain-chat-plan-task.md` | Core chat SSE overlap protection | `/api/chat/stream` retained same-conversation overlap protection. |
| `domain-chat-plan-task.md` | `TaskStreamSupport` named event mapping | Helper itself had correctly shaped event mapping and focused tests. |
| `domain-orchestration-runtime.md` | Queue delete history loss | Terminal queue delete preserving history appeared fixed; purge is explicit. |
| `domain-orchestration-runtime.md` | Stale lease interruption/cancel lifecycle | Stale lease interruption and cancel-request handling were present. |
| `domain-orchestration-runtime.md` | Force-interrupt late completion overwrite | Lease-owner guarded save protection was present. |
| `domain-orchestration-runtime.md` | Durable transcript links missing | Transcript links existed and were used with fallback paths. |
| `domain-persistence-schema.md` | Chat memory/session warm-DB compatibility | Guarded add-column paths existed. |
| `domain-persistence-schema.md` | Workflow graph-column migration missing | Repository code/tests covered `nodes_json` and `routes_json`. |
| `domain-test-harness.md` | Existing disabled JUnit tests | No disabled tests were found by annotation scan. |
| `domain-frontend-static.md` | Viewport-scaled fonts or negative letter spacing | Reviewed CSS did not show those issues. |
| `playwright-public-pages-evidence.md` | Public pages blank/404 | Requested public routes returned 200 and rendered content. |

## Approved Skips

None.
