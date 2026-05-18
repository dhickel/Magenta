# Phase 02: Agent Docker Lifecycle And Management UI

## Context

Agents must own a persistent Docker-backed environment while active. The UI must expose lifecycle controls and make workspace persistence clear enough for alpha operators.

## Goal

Validate through Playwright that agents can be created, enabled, awakened, stopped, restarted, disabled, and deletion/archive-confirmed while their Docker container and persistent workspace/home behavior match the contract.

## In Scope

- Agent creation UI.
- Agent list and detail pages.
- Docker status panel and per-agent lifecycle controls.
- Enable, disable, wake/start, stop/sleep, restart, refresh.
- Delete/archive confirmation flow.
- Persistent `/home/agent`, `/workspace`, and `/output` mount evidence through container-backed execution controls or browser-origin validation endpoints if exposed.

## Out of Scope

- Agent cloning. If clone controls exist, record as a defect unless explicitly hidden from alpha users.
- Deep task/workflow execution validation; that is phase `03` and `04`.

## Implementation Steps

1. Open `/agents` in Playwright and create a new alpha validation agent with a unique name.
2. Confirm the new agent appears in the list without a full page reload when HTMX is expected.
3. Open the agent detail page and verify:
   - profile state
   - Docker state
   - workspace/home/output paths if shown
   - inbox/queue/jobs/workspace/outputs/history tabs are functional
4. Use UI controls to start/wake the agent container.
5. Refresh status and verify the container reaches running/ready state.
6. Use UI controls to stop/sleep, restart, disable, and enable the agent.
7. Verify disabled agents cannot accept new work and show a clear user-facing state.
8. Trigger delete/archive confirmation and verify first click does not remove data.
9. If hard delete is offered, verify it requires explicit confirmation text and that archive/disable is the safer default.

## Validation

Required Playwright checks:
- Agent creation succeeds from UI and produces a durable detail page.
- Start/wake changes Docker state from stopped/unavailable-ready to running, or reports an actionable runtime error.
- Stop/sleep changes Docker state without deleting workspace data.
- Restart returns to running with the same agent identity.
- Disable blocks or pauses work assignment with visible feedback.
- Delete/archive confirmation is a two-step flow.
- No visible clone path exists for alpha.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/02-agent-docker-lifecycle-evidence.md` exists.
- Any Docker lifecycle mismatch is logged as an alpha blocker bug.
- The created agent id/name is passed to phases `03`, `04`, `05`, `06`, and `07`.
