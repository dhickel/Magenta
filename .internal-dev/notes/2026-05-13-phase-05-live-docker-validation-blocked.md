# Deferred Idea
Run Phase 05 daemon-backed Docker live validation in an environment with Docker/Podman CLI and socket access.

# Why Deferred
Current validation environment does not have `docker` CLI available (`docker info` exited `127`), so live daemon-backed test gate could not be executed here.

# What To Run Later
- Use setup guide first:
  - `.internal-dev/knowledge/docker-runtime-host-setup-and-prereqs.md`
- `mvn -q -Dmagenta.docker.live=true -Dtest=DockerRuntimeClientLiveTest,AgentContainerRuntimeServiceLiveTest test`
- Re-run targeted agent lifecycle flows while daemon is available:
  - wake/start agent container
  - stop/sleep agent container
  - restart
  - archive/disable and hard-delete confirmation paths
