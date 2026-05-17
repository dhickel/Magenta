# Domain Chat/Plan/Task Review

## Agent

- Agent: domain-chat-plan-task
- Agent id: `019e371c-625e-7bd1-808d-d6af2ede9a9e`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed chat, plan, task execution services, chat/plan/task controllers, SSE helpers, static chat client, and related tests.

## Files and Routes Reviewed

- Files: `ChatController`, `PlanController`, `TaskController`, `ChatService`, `PlanService`, `ChatMemoryRepository`, `TaskStreamSupport`, `chat-client.js`, related tests.
- Routes: `/api/chat/**`, `/api/chat/{conversationId}/plan/execute`, `/api/chat/{conversationId}/plan/execute/stream`, `/api/plans/{planId}/submit`, `/api/plans/{planId}/runs/stream`, `/api/tasks/{taskId}/runs/stream`.

## Commands and Probes

- `find .. -name AGENTS.md`
- Targeted `sed`/`nl` reads
- `rg` route/SSE/task/plan searches
- `git status --short`

## Findings

- Critical: approved plans still expose and run direct chat execution instead of submit-to-agent semantics. `chat-client.js` renders `Execute now` and posts to direct plan execution routes.
- Critical: saved-plan execution deliberately deletes the persisted conversation transcript by clearing the conversation before execution and saving an empty chat-memory list.
- High: `/api/plans/{planId}/runs/stream` emits every event as `TaskExecutionEvent` and wraps the real event, unlike `TaskStreamSupport`'s named event mapping.
- High: submit-to-agent priority is inconsistent: chat send-to-agent defaults to priority `0`, while the operational HTMX route defaults to high priority `9`.
- Medium: task run stream still has inline synchronous execution paths rather than saved-definition queue submission.

## Explicitly Ruled Out

- Core `/api/chat/stream` retains same-conversation overlap protection.
- `TaskStreamSupport` itself has correctly shaped event mapping and focused tests.
- `magenta-tools.js` has stale direct-run references, but this pass did not find a current server-side include.
