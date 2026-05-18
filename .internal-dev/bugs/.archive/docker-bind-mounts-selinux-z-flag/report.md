# Bug Report: Docker Bind Mounts Missing SELinux :Z Flag

## Summary
The `DockerRuntimeClient` creates container bind mounts via `Bind.parse()` without the `:Z` SELinux relabeling flag. On systems with SELinux in Enforcing mode, this causes `PermissionError: [Errno 13] Permission denied` when the container attempts to write to mounted host directories.

## Scope
Affected file: `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java`

The `execCommand()` method creates bind mounts at lines 137-139:
```java
binds.add(Bind.parse(agentHome.toString() + ":/home/agent:rw"));
binds.add(Bind.parse(workDir.toString() + ":/workspace:rw"));
binds.add(Bind.parse(outputDir.toString() + ":/output:rw"));
```

These use `:rw` mode but do not include `:Z` for SELinux relabeling.

## Reproduction
1. Run on a system with SELinux Enforcing (confirmed on Fedora 43).
2. Start the app with Docker enabled.
3. Submit a task or plan that writes to workspace/output directories.
4. The container will fail with `PermissionError` on any write to `/workspace`, `/output`, or `/home/agent`.

Verified: Running `podman run` with `:rw` (no `:Z`) fails; adding `:rw,Z` succeeds.

## Expected
Container bind mounts should work on SELinux-enforcing hosts without manual intervention.

## Actual
Containers cannot write to mounted host directories.

## Evidence
```
# Without :Z -- fails
podman run --rm -v "${TEMP_OUT}:/output:rw" python:3.11-slim bash -c "echo test > /output/test.txt"
# PermissionError: [Errno 13] Permission denied

# With :Z -- succeeds
podman run --rm -v "${TEMP_OUT}:/output:rw,Z" python:3.11-slim bash -c "echo test > /output/test.txt"
# Works correctly
```

## Impact
- High: All Docker-backed plan/task execution fails on SELinux-enforcing hosts.
- The app startup succeeds (daemon ping, image verification work), so the failure is only visible at execution time.

## Status
Fixed (2026-05-11) — `DockerRuntimeClient.execCommand()` now appends `,Z` to bind mount specs when `magenta.docker.selinux-relabel=true` (default). `DockerRuntimeConfig.selinuxRelabel` property added.

## Next Action
Modify `DockerRuntimeClient.execCommand()` to append `:Z` to bind mount specifications when SELinux is detected, or make it configurable via `magenta.docker.selinux-relabel=true` property.
