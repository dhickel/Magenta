# Context
Magenta already has plan mode, saved execution plans, planning tools, approval markdown, and execution evidence. The current implementation still uses choice/text prompt state and command-style execution that hides live tool activity.

# Goal
Refactor plan mode to use queued free-response planning questions, richer plan state, streamed plan execution, and validation-gated completion.

# In Scope
- Plan schema/model additions for inputs, outputs, queued questions, validation feedback, and detailed plan markdown.
- Planning tool and prompt updates for `plan_ask_questions`.
- Chat/UI updates for one-at-a-time question answering and plan execution visibility.
- Inline validator pass using the planning model when execution requests completion.

# Out of Scope
- Reusable task execution forms.
- Task chaining.
- General subagent orchestration beyond the validator pass.

# Implementation Steps
- Update persistence and DTOs first so service/UI contracts have stable fields.
- Replace old structured prompt tools with queued free-response planning questions.
- Append planning Q/A to chat memory instead of hiding answer history in plan state.
- Make plan execution run through the normal stream path.
- Add validator-gated `plan_complete` behavior and expose validation feedback.

# Validation
- Add focused repository, service, tool, controller, and frontend tests.
- Run `mvn test`.

# Exit Criteria
- Plans can ask up to five queued questions and show question progress.
- Approved plan markdown includes optional inputs only when present.
- Executing a plan clears chat context and shows normal tool activity.
- Completion requires validator approval or returns remediation feedback.
