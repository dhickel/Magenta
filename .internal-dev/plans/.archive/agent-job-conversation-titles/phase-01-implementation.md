# Agent Job Conversation Titles

## Context

Magenta needs persisted background agent jobs, starting with conversation title generation after the first user message in a new conversation.

## Goal

Add Magenta-owned persisted agent jobs for conversation title generation, include nullable titles in chat metadata payloads, and surface titles in the browser session list.

## In Scope

- Internal agent job table, repository, service, and bounded executor.
- Conversation title job type with duplicate prevention per conversation.
- Title generation through the selected model via existing model routing.
- Nullable title metadata on chat session/history API payloads.
- Browser session rendering and short polling for new-conversation title availability.
- Focused unit/controller/frontend coverage.

## Out of Scope

- Public job API.
- General subagent orchestration.
- Tool use or chat-memory writes from title jobs.

## Implementation Steps

1. Inspect current chat persistence, service, model, controller, and browser client shapes.
2. Add schema/repository/model/service support for persisted jobs and titles.
3. Wire bounded executor and enqueue title jobs after first message acceptance.
4. Extend API payloads and frontend rendering/polling.
5. Add or update focused tests.

## Validation

- Run affected Maven tests, broadening to full test suite if practical.

## Exit Criteria

- New conversations enqueue at most one title job.
- At most two background jobs execute concurrently.
- Title appears in sessions/history payloads and browser session list when available.
- Job status and failure details persist.
