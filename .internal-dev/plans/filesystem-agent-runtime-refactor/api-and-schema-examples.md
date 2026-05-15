# Filesystem Agent Runtime Refactor: API And Schema Examples

## Workspace Status Response

Recommended shape if a public endpoint remains useful:

```json
{
  "agentId": "agent-123",
  "workspaceRelativePath": "agents/agent-123/workspace",
  "status": "READY",
  "exists": true,
  "writable": true,
  "activeRunCount": 1,
  "linkedProjectIds": ["project-9"],
  "outputArtifactCount": 4,
  "outputBytes": 18240,
  "lastActivityAt": "2026-05-15T16:14:00Z",
  "message": "Workspace ready"
}
```

Do not preserve old fields like `containerId`, `containerName`, `dockerHost`, `image`, or `mounts` in the new active response.

## Shell Execution Result

Recommended active contract:

```json
{
  "command": "pwd",
  "executionSource": "bash",
  "workingDirectory": "agents/agent-123/workspace",
  "exitCode": 0,
  "stdout": "/absolute/dataRoot/agents/agent-123/workspace\n",
  "stderr": "",
  "timedOut": false,
  "truncated": false
}
```

Do not expose `containerId` after the cutover.

## Workspace Metadata

Recommended persisted root for agent-owned workspace records:

```json
{
  "ownerType": "AGENT",
  "ownerId": "agent-123",
  "rootRelativePath": "agents/agent-123/workspace"
}
```

## Project Link Example

```text
agents/agent-123/workspace/projects/project-9
  -> ../../../projects/project-9/workspace
```

Project links are an execution affordance, not the canonical project data store. The canonical project root remains `projects/<projectId>/workspace`.

## Removed Endpoints

Expected deletions:

```text
GET  /api/runtime/docker/status
GET  /agents/_detail/{agentId}/docker
GET  /agents/_detail/{agentId}/docker-status
GET  /agents/_docker/{agentId}/docker-status
POST /agents/_docker/{agentId}/start
POST /agents/_docker/{agentId}/stop
POST /agents/_docker/{agentId}/restart
GET  /agents/_docker/{agentId}/status-row
```

Any replacement endpoint must be workspace-named and justified by an actual consumer.
