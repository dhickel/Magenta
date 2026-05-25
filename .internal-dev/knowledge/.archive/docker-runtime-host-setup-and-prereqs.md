# Topic
Docker runtime host setup and prerequisites for Magenta orchestration

# Source References
- `src/main/resources/application.yml`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`
- `.internal-dev/scripts/docker-setup.sh`

# Key Takeaways
- Yes, a host container runtime is required for full agent container lifecycle behavior (`magenta.docker.enabled=true`).
- Install one of the following on the host:
  - Podman (recommended, rootless) with Docker API socket enabled.
  - Docker Engine + Docker CLI with daemon running.
- Minimum host dependencies for setup/verification scripts:
  - `python3`
  - `curl`
  - container runtime binary (`podman` or `docker`)
- Agent image must exist locally before runtime is considered healthy:
  - default: `python:3.11`
- Runtime checks used by app:
  - daemon reachability (`ping`)
  - agent image availability (`inspect image`)

## Fast Setup (Linux)
- Recommended one-command setup/verify:
  - `chmod +x .internal-dev/scripts/docker-setup.sh`
  - `./.internal-dev/scripts/docker-setup.sh`
- Verify only mode (no installation):
  - `./.internal-dev/scripts/docker-setup.sh --verify-only`

## Podman Path (Recommended)
1. Install Podman (Fedora/RHEL):
   - `sudo dnf install -y podman`
2. Start Docker-compatible user socket:
   - `systemctl --user enable --now podman.socket`
3. Ensure lingering (socket survives logout):
   - `sudo loginctl enable-linger $USER`
4. Export Docker API host:
   - `export DOCKER_HOST=unix://${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/podman/podman.sock`
5. Pull agent image:
   - `podman pull python:3.11`

## Docker Engine Path
1. Install Docker Engine + CLI.
2. Start daemon:
   - `sudo systemctl enable --now docker`
3. Add user to docker group:
   - `sudo usermod -aG docker $USER`
   - log out/in
4. Export Docker API host (usually optional):
   - `export DOCKER_HOST=unix:///var/run/docker.sock`
5. Pull agent image:
   - `docker pull python:3.11`

## App Runtime Configuration
- In `application.yml` or environment:
  - `magenta.docker.enabled=true`
  - `magenta.docker.agent-image=python:3.11`
  - keep `magenta.docker.selinux-relabel=true` on SELinux hosts
- Recommendation: explicitly set `DOCKER_HOST` in shell/service env to avoid host-path ambiguity.

## Preflight Verification
- Runtime binary:
  - `podman --version` or `docker --version`
- API socket reachable:
  - `docker info` (or `podman info`)
- Image present:
  - `docker image inspect python:3.11 >/dev/null`
- SELinux-safe bind mount smoke:
  - `docker run --rm -v "$(mktemp -d):/output:rw,Z" python:3.11 bash -lc "echo ok >/output/test.txt"`

## Magenta Validation Gate
- Focused tests:
  - `mvn -q -Dtest=DockerRuntimeClientTest,OrchestrationControllerTest test`
- Live daemon tests:
  - `mvn -q -Dmagenta.docker.live=true -Dtest=DockerRuntimeClientLiveTest,AgentContainerRuntimeServiceLiveTest test`

# Engine Relevance
- Container lifecycle and agent execution controls depend on daemon/socket/image health.
- If host setup is missing, agent Docker status surfaces show unavailable states and lifecycle execution is blocked.
- This runbook is the required precondition for production-like Docker validation and deployment readiness.

# Open Questions
- Should we enforce a startup hard-fail when `magenta.docker.enabled=true` but daemon/image checks fail, or continue with degraded operational UI states?
- Should CI provide a daemon-backed lane for `-Dmagenta.docker.live=true` to make this gate non-manual?
