# DEFECT-03-03: Model Writes Outputs to Host Path Instead of Container /output Mount

## Summary
When executing a plan through a Docker-backed agent, the model writes output files to the host filesystem path (`/home/hickelpickle/.magenta/root/`) instead of the container's `/output/` mount point. Files exist on disk but are not registered as output artifacts and are inaccessible through the output collection mechanism.

## Scope
- Plan execution environment provides output directory as container path `/output`
- Model receives this path but uses host filesystem resolution
- Files land at `.magenta/root/hello.txt`, `.magenta/root/result.json` (workspace root) instead of `.magenta/root/agents/{id}/outputs/`
- Container `/output` mount (bound to agent outputs dir) remains empty
- Affects all Docker-backed plan execution

## Reproduction
1. Create a plan requiring file outputs
2. Submit plan to Docker-backed agent: `POST /plans/_submit/{planId}`
3. Wait for COMPLETED status
4. Check container output mount: empty
5. Check host `.magenta/root/`: files present

## Expected
Model writes to `/output/` (container path), which maps to agent's outputs directory on host. Output collection registers files in `run_output_artifacts` with correct paths.

## Actual
Model writes to host workspace root. Files have correct content (verified: hello.txt contains "Hello from Docker-backed Magenta validation!", result.json contains valid JSON) but are in the wrong location and not registered.

## Evidence
- Phase 03 evidence file: `.internal-dev/reviews/docker-backed-alpha-e2e-validation/03-plans-tasks-docker-execution-evidence.md`
- `cat /home/hickelpickle/.magenta/root/hello.txt` → correct content
- `cat /home/hickelpickle/.magenta/root/result.json` → correct content
- Container `/output` mount at `.magenta/root/agents/23579fcf-.../outputs/` is EMPTY
- Framework output collection captured only a stub (hello_file.txt, 9 bytes, content: "hello.txt" — the model-reported filename)

## Impact
**Alpha blocker.** Output collection is broken — real artifacts exist on disk but are not registered, and registered outputs may be stubs. The output system cannot deliver end-user value.

## Status
Fixed — live model-backed validation passed (2026-05-14)

## Phase 5 Validation Update
Plan execution submitted to Docker-backed agent "magenta" (ID: `9d948907`) completed with status COMPLETED, but the execution ran through `agent=system` instead of the Docker container. Evidence:

```
PlanService: Allocated temp=... output=/home/hickelpickle/.magenta/root/agents/system/outputs/... agent=system for run=9b5213f6-...
```

- Files landed at `.magenta/root/hello.txt` and `.magenta/root/result.json` (host root, same old bug path)
- Container `/output` had only our manually-written `alpha-output.txt` — no plan output files
- Container `/workspace` and `/home/agent` showed no plan execution artifacts
- Output artifacts registered under `agents/system/outputs/` instead of the agent's outputs directory

The OrchestrationRunnerService dispatches task execution through PlanService which uses the system agent path. The Docker container execution path is not connected.

## Next Action
None — archive with the completed alpha validation defects.

## 2026-05-14 Remediation Update
Implemented the missing execution-context bridge and run-scoped output contract:
- `ChatService -> TaskService -> PlanService` now carries `OrchestrationTaskContext` into chat task run creation.
- `PlanService.startRun(...)` allocates agent-scoped output directories for orchestration-backed task runs and refreshes the thread-local context with host workspace, host output, and container output path.
- Docker runtime instructions now identify `/output` as the agent output root and instruct model-backed task runs to write to `/output/<run-output-dir>`.
- `OutputArtifactService` resolves `/output/<run-output-dir>/<file>`, `/output/<file>`, bare filenames, and relative paths back to the active run output directory.
- `AgentShellToolService` validates container working directories, passes per-call timeout to the container runtime, and records the container id in shell results.

Validation completed:
- `mvn test` passed: 430 tests, 0 failures, 0 errors.
- Docker-disabled bounded startup succeeded.
- Docker-enabled bounded startup succeeded against `unix:///run/user/1000/podman/podman.sock` with image `python:3.11`.
- Live Docker-enabled app on port `18080` created agent `b8d77b75-1c7e-4994-bf22-894a16b12675`.
- Starting that agent's managed container returned status `IDLE`, image `python:3.11`, and mounted `/output` to `/home/hickelpickle/.magenta/root/agents/b8d77b75-1c7e-4994-bf22-894a16b12675/outputs`.
- Manual container write proof created `/output/live-proof/hello.txt`; host readback returned `podman-live-proof`.

Final validation completed:
- Corrected non-DeepSeek model endpoints back to `http://192.168.1.112:11434`.
- `http://192.168.1.112:11434/api/tags` returned Ollama model tags.
- Cleared stale Playwright MCP/Chrome processes that held profile `mcp-chrome-4e05678`.
- Ran a Qwen-backed Docker orchestration task through `POST /api/tasks/{taskId}/runs/stream`.
- Assignment `ec0d0b45-50f8-496b-8a79-82eaa126f453` completed with run `e24ef1be-2286-4b73-9759-c46f69166e77`.
- Registered artifacts:
  - `1526fa6b-f85c-46ec-b3e5-a7adf21523b8` -> `hello.txt`, agent `f90e3d3a-e1e1-4cfe-a0b1-a4b428fea496`, run type `TASK_RUN`
  - `bf9dd379-8bdc-4b9a-93a2-12c27c56e2db` -> `result.json`, agent `f90e3d3a-e1e1-4cfe-a0b1-a4b428fea496`, run type `TASK_RUN`
- Host files existed under `/home/hickelpickle/.magenta/root/agents/f90e3d3a-e1e1-4cfe-a0b1-a4b428fea496/outputs/alpha-docker-output-proof-qwen-e24ef1be-2286-4b73-9759-c46f69166e77/`.
- File readback:
  - `hello.txt`: `Alpha Docker final validation`
  - `result.json`: `{"ok": true}`
- Output download endpoints returned HTTP 200 for both files.
- Browser validation with isolated repo-local Playwright loaded `/dashboard`, `/agents`, `/outputs`, `/plans`, `/workflows`, and `/settings`; `/outputs` showed the run evidence and downloads returned expected content.
