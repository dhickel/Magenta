# Anonymous Chat Plan Clean Execution Review

## Scope
Reviewed the `/plans` saved planning tab behavior, `/chat` anonymous approved plan execution path, clean-context prompt handling, documentation boundaries, and focused regression coverage.

## Findings
- The browser was using the blocking `/api/chat/{conversationId}/plan/execute` route for approved anonymous execution, which delayed visible feedback and bypassed the documented streaming browser path.
- The streaming execution endpoint did not accept `clearContext`, so clean execution could not be requested over the intended SSE path.
- Clean execution cleared persisted chat memory by writing an empty message list, which risked losing the user-visible conversation transcript.
- Tool-capable clean execution needed to keep the approved execution instruction in tool-loop checkpoints while still omitting older stored transcript.
- Saved plan chat tab switching needed to preserve pending scripted opening questions instead of falling through to draft resume prompts.

## Risk Assessment
The highest risk was data loss from destructive clean-context handling. The fix makes clean context request-scoped and adds regression coverage for preserving stored transcript rows. Browser validation confirmed the saved chat is tab-isolated and starts with the runtime-inputs question, but successful live model execution streaming was not validated because the validation pass did not reach a model-backed approved execution.

## Recommendations
- Keep `/chat` anonymous planning and `/plans` saved task planning documented and tested as separate systems.
- Prefer request flags and prompt assembly behavior for clean-context semantics rather than repository mutation.
- Add a deterministic browser fixture for approved anonymous plans so Playwright can validate a successful SSE `start` event without relying on live model completion.

## Follow-ups
- No out-of-scope bug artifact was created. The remaining validation limitation is captured as a follow-up idea in the changelog and knowledge note.
