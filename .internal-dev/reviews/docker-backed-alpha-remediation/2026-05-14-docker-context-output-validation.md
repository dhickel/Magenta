# Docker Context Output Validation

## Scope
Reviewed and remediated the Docker-backed task execution path for context propagation, container shell robustness, run-scoped output paths, artifact resolution, startup health, and live container mount behavior.

## Findings
- `ChatService -> TaskService -> PlanService` previously dropped `OrchestrationTaskContext` when creating chat task runs, causing agent assignments to allocate outputs under `agents/system/outputs`.
- The persistent container mount maps the agent output root to `/output`; the prompt must identify `/output/<run-dir>` for the active run.
- Container shell execution accepted host absolute working directories and did not pass the requested per-call timeout into Docker exec.
- Stale Playwright MCP/Chrome processes holding `mcp-chrome-4e05678` were terminated. The MCP transport had already closed, so final browser validation used the repo-local Playwright package with an isolated Chromium profile.
- A true model-backed Docker task assignment passed after correcting the model endpoint back to `192.168.1.112:11434`.
- Model-backed validation passed with Qwen-backed agent `f90e3d3a-e1e1-4cfe-a0b1-a4b428fea496`, assignment `ec0d0b45-50f8-496b-8a79-82eaa126f453`, and run `e24ef1be-2286-4b73-9759-c46f69166e77`.

## Risk Assessment
The Docker-backed alpha integration is validated for the covered surfaces. Focused tests and the full suite pass, Spring starts with Docker disabled and enabled, the Podman-backed app executes a real model-backed task through the managed agent container, output files land in the agent run output directory, artifacts are registered with agent attribution, and output downloads return the expected content.

## Recommendations
- Archive DEFECT-03-03, DEFECT-04-01, DEFECT-04-02, and DEFECT-07-01 as fixed.
- Use `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock` as the default local validation path.
- Keep the newly opened Docker stop status mismatch bug active; it is lifecycle-control polish, not an output execution blocker.

## Follow-ups
- Consider adding `MAGENTA_OUTPUT_DIR` to the model/tool environment after alpha if models still confuse `/output` root vs run output directories.
