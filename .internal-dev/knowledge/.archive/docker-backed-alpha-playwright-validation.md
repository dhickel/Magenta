# Docker-Backed Alpha Playwright Validation

## Topic
End-to-end validation of Magenta's alpha operational contract using Docker/Podman-backed agent execution with HTMX endpoint verification.

## Source References
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/` (orchestration plan suite)
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/` (all phase evidence)
- `.internal-dev/knowledge/docker-runtime-host-setup-and-prereqs.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`

## Key Takeaways

### Docker/Podman Integration
- Podman 5.8.2 implements Docker API v1.44 transparently. The `docker-java` HTTP client connects via `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock` with zero code changes.
- Container lifecycle is deterministic: naming convention `magenta-agent-{first12chars-of-uuid}`, three bind mounts (`/home/agent`, `/workspace`, `/output`), persistent shell idle loop.
- Agent container state is separate from agent lifecycle state: agent can be ACTIVE while container is STOPPED; disabling sets Docker status to DISABLED.

### HTMX Endpoint Architecture
- Every UI control maps to a server endpoint that returns HTML fragments. This means curl-based validation can verify server-side behavior even without browser automation.
- Pattern: `GET /{resource}/_list`, `GET /{resource}/_editor/_new`, `POST /{resource}/_editor`, `PUT /{resource}/_editor/{id}`, `DELETE /{resource}/{id}`
- Model overrides are passed as HTTP params; backend resolves aliases (not raw model names) via `aiConfig.models()`.

### Workflow State Machine
- 7 node types: task, user_approval, agent_approval, user_message, agent_message, delegation, report
- 4 route types: map_output, pass_through, log, control
- Validation catches: duplicate keys, bad route endpoints, cycles, missing inputs
- State transitions: QUEUED → RUNNING → WAITING (at gate) → RUNNING → COMPLETED
- Known gaps: taskNodeExecutor not wired, resume doesn't check approval response

### Output/Workspace Architecture
- Bind mount structure: agent home → container `/home/agent`, workspace root → container `/workspace`, output root → container `/output`
- Output attribution chain: run → plan → agent → job → project (all nullable foreign keys)
- Known gaps: no content viewing endpoint, model writes to host path not container mount, loose files in workspace root unregistered

## Engine Relevance
- The `docker-java` transport layer is fully Podman-compatible. No special Podman handling needed.
- PlanService.saveTask hardcodes APPROVED status — this means the DRAFT → REVIEW → APPROVED workflow doesn't exist yet.
- ChatService wires the taskNodeExecutor for chat-originated plan execution, but this wiring doesn't extend to the WorkflowRunner context.
- The `InboxService.parseApprovalFromResponse()` utility exists but is unused during workflow resume — a classic "utility exists but integration missing" pattern.

## Open Questions
- Should the taskNodeExecutor be wired from ChatService into WorkflowRunner, or should WorkflowRunner have its own independent execution path?
- Should the model execution environment receive container-relative or host-relative paths?
- Should all files in the workspace root be automatically registered as outputs, or only those in known output directories?
