# Chat

The `/chat` page is the main conversation surface. Use it for normal assistant conversations and anonymous, ad hoc plan drafting.

## Layout

The chat page has:

- A session sidebar with bulk actions for delete, archive, and favorite.
- An **Agent Model** dropdown for normal responses.
- A **Planning Model** dropdown for planning mode.
- A session indicator showing the active conversation.
- A plan status area with **View plan** details when planning is active.
- An **Outputs** panel for files created in the selected chat.
- A context usage bar.
- A message composer.

## Start Or Continue A Chat

Open `/chat` for a new or existing chat session. Select a prior session from the sidebar to continue it. The session list supports favorites, archives, deletion, and bulk actions.

Use **Send** to submit a message. Enter sends the message; Shift+Enter inserts a newline.

Sessions with chat-scoped files show a green `<n> Outputs` badge in the session card. Selecting that session shows the files in the right-side **Outputs** panel with file type, name, relative path when useful, size, modified time, and a **Download** button. The panel lists ordinary chat files created under the chat's persistent file directory; it does not preview or edit file contents.

## Planning Mode

Planning mode creates an anonymous in-chat session plan inside the current chat conversation. These plans are `SESSION_PLAN` records keyed by the chat conversation id. They are not saved to `/plans`, do not define structured runtime inputs or outputs, and cannot be submitted to agents as saved task definitions.

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
- **Approve And Exec Clean** executes the approved anonymous plan with only the approved plan instructions and the chat file directory context. It does not delete the conversation transcript; it only omits prior chat messages from that execution prompt.
- **Cancel** exits planning mode for that conversation.

Anonymous chat execution writes chat-scoped files under the persistent chat file directory. If the final response is itself a deliverable, Magenta persists it as `final-message.md` or a collision-safe variant in that directory.

## Saved Plan Chat

Saved plan authoring happens in `/plans`. **New Plan Chat** creates a saved draft there and starts a plan-scoped chat that collects opening answers, gives them to the planning model as seed context, and then continues with model-led follow-up questions until the saved plan is ready to review. It does not use the `/chat` session list or `/api/chat` session architecture.

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
