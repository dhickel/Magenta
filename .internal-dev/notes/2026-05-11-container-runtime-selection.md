# Container Runtime Selection: Docker vs Podman

## Tradeoff Summary

| Factor | Docker | Podman |
|--------|--------|--------|
| Open-source community | Dominant — 70k GitHub stars, universal CI/CD support, all tutorials assume it | Growing — 25k stars, Red Hat backed, Kubernetes-aligned |
| Rootless execution | Optional (rootless mode, not default) | Default — no daemon, no root, user socket |
| Installation complexity | Third-party repo on all distros, post-install group membership + relogin | Pre-installed on Fedora/RHEL; `apt install podman` on Debian/Ubuntu |
| Docker API compatibility | Native | Via `podman.socket` (user-level systemd unit) |
| docker-java support | First-class | Works via `DOCKER_HOST` env pointing to podman socket |
| Compose / orchestration | `docker compose` (built-in) | `podman-compose` or `podman kube` |
| Image ecosystem | Docker Hub default | Compatible — same registries, same images |
| macOS / Windows | Docker Desktop (polished, proprietary) | Podman Machine (open-source, rougher edges) |
| Fedora 43 | Not pre-installed | Pre-installed |

## Problem with Docker for This Project

Docker requires:
1. Adding `docker-ce.repo` (third-party)
2. `sudo dnf install docker-ce docker-ce-cli containerd.io`
3. `sudo systemctl enable --now docker`
4. `sudo usermod -aG docker $USER`
5. Log out and back in
6. The daemon runs as root — any container escape is a root compromise

## Problem with Podman for This Project

Podman requires:
1. Already installed on Fedora/RHEL. On Debian/Ubuntu: `sudo apt install podman`
2. `systemctl --user enable --now podman.socket` (one-time)
3. `export DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`
4. That's it

The Docker API socket is user-level — no root, no daemon, no group membership.

## Recommendation: Podman as Default, Docker as Documented Alternative

Reasons:
1. **Zero-install on Fedora/RHEL** — ships with the OS, just enable the socket
2. **Rootless security** — no daemon running as root, each container runs as the user
3. **Simpler setup** — no group membership, no relogin, no repo addition
4. **docker-java compatibility** — speaks Docker API natively via podman.socket
5. **Systemd-native** — socket activation, no separate daemon lifecycle management
6. **Docker users aren't locked out** — `docker` CLI works as a drop-in via `podman-docker` package, or they can use native Docker with the same `DOCKER_HOST` pattern

The script at `.internal-dev/scripts/docker-setup.sh` already handles both runtimes. The application's `DockerRuntimeConfig` auto-detects via `DOCKER_HOST` env var.

## Image Decision

Use `python:3.11` (full, ~350 MB) as the default agent image. Rationale:
- Agents will `pip install` packages at runtime
- Slim lacks gcc/python-dev for packages without pre-built wheels
- The image is pulled once, shared by all container instances
- 350 MB is acceptable for a server-side deployment

Configurable via `magenta.docker.agent-image` property.

## Container Lifecycle Model

Current implementation: **one-off containers** — each task/workflow node creates a container, runs it, auto-removes it.

Future consideration: **persistent per-agent containers** — one container per agent that stays running across tasks, with a shell or exec-based interaction loop. This would be a separate execution mode configured at the agent level, not a replacement for one-off execution.

## Open Questions

- Should we ship a custom `Dockerfile` with pre-installed common Python packages?
- Should per-agent persistent containers reuse exec sessions or use a long-running shell loop?
- Do we need image pulling with auth for private registries?
