# Chat

The `/chat` page is the main conversation surface. Use it for normal assistant conversations and anonymous, ad hoc plan drafting.

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

Planning mode creates an anonymous session plan inside the current chat conversation. These plans are not saved to `/plans`, do not define structured inputs or outputs, and cannot be submitted to agents as saved definitions.

To start planning:

1. Open `/chat`.
2. Use the plan entry point.
3. Answer the three opening questions for goal, assumptions/details/constraints/approach, and expected deliverables.
4. Answer any follow-up clarifying questions in the planning panel.
5. Review **View plan** as the draft fills in.
6. Continue planning, approve and execute, approve and execute clean, or cancel when ready.

The assistant should collect enough goal, deliverable, assumption, step, and validation detail before the plan is approved.

## Answer Questions

When Magenta asks a planning question, answer it in the visible planning prompt. Include direct decisions and constraints. If the question is not relevant, say so explicitly so the plan can record the assumption or continue without it.

## Approve, Continue, Cancel, Execute

- **Continue Planning** keeps planning open when the draft is incomplete.
- **Approve And Exec** executes the approved anonymous plan with the current conversation context.
- **Approve And Exec Clean** executes the approved anonymous plan with only the approved plan instructions and the chat file directory context.
- **Cancel** exits planning mode for that conversation.

Anonymous chat execution writes chat-scoped files under the persistent chat file directory. If the final response is itself a deliverable, Magenta persists it as `final-message.md` or a collision-safe variant in that directory.

## Saved Plan Chat

Saved plan authoring happens in `/plans`. **New Plan Chat** creates a saved draft there and starts a plan-scoped chat that collects explicit inputs, deliverables, and structured outputs for downstream use. It does not use the `/chat` session list or `/api/chat` session architecture.

## Session Management

Use the sidebar to:

- Rename sessions when the title is not useful.
- Mark important sessions as favorites.
- Archive old sessions without deleting them.
- Delete sessions that no longer matter.
- Bulk-select sessions for repeated actions.

Deleting a session removes the visible conversation history for that conversation. It does not automatically delete saved plans, tasks, workflow definitions, jobs, agent history, output artifacts, or persistent chat files created from the conversation.

## Common Errors

- **Unknown command**: only supported chat commands are available. Use visible UI buttons for planning and saved work.
- **Conversation not found**: the session was deleted or the URL points at an invalid conversation.
- **Anonymous chat plans cannot be saved**: create saved plans from `/plans`.
- **Another stream is active**: wait for the current response to finish, interrupt it if the UI offers that control, or refresh if the stream is stale.
- **Model unavailable**: choose a model from the dropdown or update model settings.

## Alpha Limits

Chat and operational pages are intentionally separate. Use `/chat` for conversation and anonymous execution. Use `/plans`, `/workflows`, `/jobs`, and `/agents` for saved definitions and agent submission. Plan status and session lists can become stale after concurrent browser tabs edit the same conversation, so refresh before making destructive changes.
