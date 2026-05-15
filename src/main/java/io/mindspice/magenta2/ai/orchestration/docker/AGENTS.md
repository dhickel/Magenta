## Docker Runtime Package

This package owns Docker/Podman containerized execution for plan and task runs.

### Responsibilities
- Configure Docker host from `DOCKER_HOST` env var or sensible defaults.
- Verify daemon connectivity and agent image availability at startup.
- Own a persistent per-agent container lifecycle manager.
- Keep one app-owned Docker client for the application lifetime.
- Keep at most one managed container per agent.
- Start/ensure/exec/stop/restart managed agent containers.
- Keep agent-private persistence on `/home/agent`, durable outputs on `/output`, and leased project mounts under `/projects/{projectId}`.
- Provide exec results (exit code, stdout, stderr) to callers.
- Fail fast with actionable errors when Docker is unavailable.

### Change guidance
- Never hide Docker failures behind fallback behavior.
- Keep managed containers labeled with `magenta.managed=true` and `magenta.agent.id`.
- Containers may be long-lived while agents are active; idle TTL can stop/sleep them.
- Keep app-shutdown cleanup policy controlled by `magenta.docker.keep-containers-on-shutdown`.
- Mount agent home and output root on every managed container; recreate between turns when the leased project mount set changes.
- Job/project workspace mounts require a workspace lease.
- Do not add Docker Compose, Swarm, or multi-node orchestration here.
- Keep the Docker client configuration simple and env-var driven.

### Validation
- Managed container start/ensure/restart/stop smoke test with the configured test image.
- Mount and write proof for `/home/agent`, `/projects/{projectId}`, and `/output`.
- Docker-unavailable startup fails with clear 503-level error.
- Container exec output (stdout/stderr) is captured and returned.
