# Chat

The `/chat` page is the main conversation surface. Use it for normal assistant conversations, plan drafting, and continuing saved plan work.

## Layout

The chat page has:

- A session sidebar with bulk actions for delete, archive, and favorite.
- An **Agent Model** dropdown for normal responses.
- A **Planning Model** dropdown for planning mode.
- A session indicator showing the active conversation.
- A plan status area with **View plan** details when planning is active.
- A context usage bar.
- A message composer.

## Start Or Continue A Chat

Open `/chat` for a new or existing chat session. Select a prior session from the sidebar to continue it. The session list supports favorites, archives, deletion, and bulk actions.

Use **Send** to submit a message. Enter sends the message; Shift+Enter inserts a newline.

## Planning Mode

Planning mode creates a structured draft plan from a chat conversation.

To start planning:

1. Open `/chat`.
2. Use the plan entry point, or open `/plans` and select **New Plan Chat**.
3. Describe the goal and constraints.
4. Answer clarifying questions in the planning panel.
5. Review **View plan** as the draft fills in.
6. Approve, continue, cancel, or save the plan when ready.

The assistant should collect enough goal, deliverable, assumption, step, and validation detail before the plan is approved.

## Answer Questions

When Magenta asks a planning question, answer it in the visible planning prompt. Include direct decisions and constraints. If the question is not relevant, say so explicitly so the plan can record the assumption or continue without it.

## Approve, Continue, Cancel, Save

- **Approve** marks the plan acceptable for saving or follow-on work.
- **Continue** keeps planning open when the draft is incomplete.
- **Cancel** exits planning mode for that conversation.
- **Save as task/plan** persists the current draft so it can be edited in `/plans` or submitted to an agent.

Direct plan execution from chat is disabled in the alpha UI. Save the plan, then submit the saved definition to an agent from `/plans` or an agent submit panel.

## Continue A Saved Plan

Saved plans can be continued in chat from plan-specific entry points. When a plan is opened in chat, Magenta starts or reuses a conversation and includes the saved plan definition as context. Use this when the plan needs more questions, a changed scope, or a better validation checklist.

Some older UI fragments may still present a copy-and-open prompt for continuing a plan. When a direct launch path is available, prefer it. If only a prompt is shown, copy the visible prompt into `/chat`.

## Session Management

Use the sidebar to:

- Rename sessions when the title is not useful.
- Mark important sessions as favorites.
- Archive old sessions without deleting them.
- Delete sessions that no longer matter.
- Bulk-select sessions for repeated actions.

Deleting a session removes the visible conversation history for that conversation. It does not automatically delete saved plans, tasks, workflow definitions, jobs, agent history, or output artifacts created from the conversation.

## Common Errors

- **Unknown command**: only supported chat commands are available. Use visible UI buttons for planning and saved work.
- **Conversation not found**: the session was deleted or the URL points at an invalid conversation.
- **Direct plan execution is disabled**: save the plan and submit it to an agent.
- **Another stream is active**: wait for the current response to finish, interrupt it if the UI offers that control, or refresh if the stream is stale.
- **Model unavailable**: choose a model from the dropdown or update model settings.

## Alpha Limits

Chat and operational pages are intentionally separate. Use `/chat` for conversation and planning; use `/plans`, `/workflows`, `/jobs`, and `/agents` for execution. Plan status and session lists can become stale after concurrent browser tabs edit the same conversation, so refresh before making destructive changes.
