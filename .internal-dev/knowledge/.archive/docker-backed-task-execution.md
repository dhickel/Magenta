# Topic
Docker-backed task execution routing and output path confinement in Magenta2

## Source References
- `.internal-dev/plans/docker-backed-alpha-remediation/02-docker-output-execution-context.md`
- `.internal-dev/plans/docker-backed-alpha-remediation/05-final-alpha-validation-gate.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/02-startup-and-docker-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/final-alpha-remediation-readiness.md`
- `.internal-dev/bugs/DEFECT-03-03/report.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`

## Key Takeaways

### 1. Docker container lifecycle works independently of task execution routing
The Podman daemon, image verification, container start/stop/restart, and bind mounts work correctly. The container mounts (`/home/agent`, `/workspace`, `/output`) are properly set up and files can be written/read via `podman exec`. However, the orchestration task runner does not route plan/task execution through the Docker container — it falls through to the system agent path.

### 2. Two agent execution paths exist and are not unified
- **System agent path**: `PlanService.startRun()` executes plans using a direct model call from the host JVM, writing files to `.magenta/root/` (host root). This is the fallback/default.
- **Docker agent path**: `AgentContainerRuntimeService` manages container lifecycle, but the execution dispatch is not wired into the orchestration runner's task execution flow.

### 3. DEFECT-03-03 has two dimensions
The original bug was that the model wrote to `.magenta/root/` instead of `/output/`. The fix needs TWO things:
a) **Path confinement**: The execution context must use container-relative paths (`/output`, `/workspace`), not host filesystem paths
b) **Execution routing**: The orchestration runner must dispatch tasks to the Docker container runtime for Docker-backed agents, not to the system agent path

During Phase 5 validation, dimension (a) was partially addressed (outputs are now registered with proper attribution), but dimension (b) was not — execution still routes through the system agent.

### 4. The `agent=system` log line is the diagnostic signature
When `PlanService` logs `agent=system`, the task execution is NOT running inside a Docker container. The correct diagnostic would show the actual agent ID (e.g., `agent=9d948907...`) and the output path would use the agent's outputs directory instead of `agents/system/outputs/`.

### 5. Output content viewing works independently of execution routing
DEFECT-07-01 (no output content view) was fixed by adding `_content` and `/download` endpoints. These endpoints work regardless of whether the execution ran through Docker or the system agent — they read files from the registered file path. However, the file path registered is wrong when execution runs through system agent.

### 6. Job execution uses the same routing path
Job submissions also route through `OrchestrationRunnerService` which delegates to `PlanService` for embedded plan items. The same `agent=system` path is used for job items, meaning job-plan executions don't run inside Docker containers either.

## Engine Relevance

When implementing the Docker execution routing fix:
- The `OrchestrationRunnerService` (or a new `TaskNodeExecutor` implementation) must be Docker-aware
- For Docker-backed agents, task execution must:
  1. Start the agent container (if not already running)
  2. Run the plan/task through the model inside the container
  3. Use container-relative paths (`/output/hello.txt`, etc.)
  4. Register output artifacts with the agent's outputs directory path
- For non-Docker agents, the current system agent path is acceptable
- The `workflow.taskNodeExecutor` (set via `WorkflowRunner.setTaskNodeExecutor()`) is the integration point for Docker-backed task execution within workflows

## Open Questions
- Should the system agent path be removed entirely once Docker execution routing is fixed, or kept as a fallback for agents without Docker configuration?
- Should task execution require explicit Docker configuration in the agent profile, or should Docker be auto-detected?
- When the Docker container is stopped/restarted during task execution, should the task fail or be rescheduled?
