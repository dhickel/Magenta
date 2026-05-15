# Docker Stop Endpoint Reports IDLE While Container Remains Running

## Summary
During live Docker validation, `POST /agents/_docker/{agentId}/stop` returned an `IDLE` Docker status fragment even though the managed Podman container was still running. Manual `podman stop` was required and escalated from SIGTERM to SIGKILL after 10 seconds.

## Scope
- Docker-backed agent lifecycle UI/API fragment.
- Observed with agent `b8d77b75-1c7e-4994-bf22-894a16b12675` and container `7bbc848781d8`.
- Podman socket: `unix:///run/user/1000/podman/podman.sock`.

## Reproduction
1. Start the app with Docker enabled and `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock`.
2. Create an agent and start its managed container through `POST /agents/_docker/{agentId}/start`.
3. Call `POST /agents/_docker/{agentId}/stop`.
4. Check `podman ps --filter label=magenta.agent.id={agentId}`.

## Expected
The stop endpoint should stop the container or return an actionable failure. The returned status fragment should not report `IDLE` if the container remains running.

## Actual
The stop endpoint returned an `IDLE`/`ok` fragment. `podman ps` still showed the container running. Manual `podman stop 7bbc848781d8` logged: `StopSignal SIGTERM failed to stop container ... in 10 seconds, resorting to SIGKILL`.

## Evidence
- Live validation on 2026-05-14.
- App log showed recoverable Docker client I/O exceptions around the stop call.
- Manual `podman ps` confirmed the container remained running after the app stop endpoint response.

## Impact
Medium alpha risk. It does not block task output routing, but lifecycle controls can mislead users and leave managed containers running.

## Status
Open

## Next Action
Make `AgentContainerRuntimeService.stopAgentContainer(...)` verify post-stop container state and return a failed/actionable status if the runtime cannot stop the container. Add a lifecycle regression or integration smoke around stop status consistency.
