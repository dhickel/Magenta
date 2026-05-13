## Docker Runtime Package

This package owns Docker/Podman containerized execution for plan and task runs.

### Responsibilities
- Configure Docker host from `DOCKER_HOST` env var or sensible defaults.
- Verify daemon connectivity and agent image availability at startup.
- Create and manage containerized execution with workspace mounts.
- Provide exec results (exit code, stdout, stderr) to callers.
- Fail fast with actionable errors when Docker is unavailable.

### Change guidance
- Never hide Docker failures behind fallback behavior.
- Keep container lifecycle bounded: containers are created per-execution, not long-lived.
- Mount agent home, work temp, and output dir for every execution.
- Job/project workspace mounts require a workspace lease.
- Do not add Docker Compose, Swarm, or multi-node orchestration here.
- Keep the Docker client configuration simple and env-var driven.

### Validation
- Container start, mount, and write smoke test with the configured test image.
- Docker-unavailable startup fails with clear 503-level error.
- Container output (stdout/stderr) is captured and returned.
