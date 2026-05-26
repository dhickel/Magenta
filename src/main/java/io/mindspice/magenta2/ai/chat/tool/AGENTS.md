## Chat Tool Package

This package owns chat-scoped tool execution support.

### Responsibilities
- Resolve configured Spring AI tools for chat agents.
- Represent tool activity as Magenta-owned chat context messages.
- Keep tool output retention and truncation policy local to chat tooling.
- Own chat-approved file tools that operate inside the active orchestration context aliases or agent/chat fallback scope.
- Own chat-approved shell execution for explicitly allowed Linux commands inside the active orchestration context aliases or configured fallback scope.
- Own chat-approved public web search and fetch tools backed by configured web search services.
- Own lightweight keyed chat planning tools that mutate Magenta-owned plan state through services.
- Own compact plan execution evidence reporting through `plan_report` and validator-gated completion through `plan_complete`.

### Change guidance
- Do not add separate durable tool-result storage unless a concrete workflow requires it.
- Avoid replay, approval, or orchestration behavior without an explicit user-facing tool use case.
- Keep model-visible tool context concise and easy to inspect.
- Keep file tool names and arguments plain, predictable, and friendly to smaller local models.
- Prefer `file_append` for accumulating notes, outlines, reports, or logs; use `file_write` only when writing the complete desired file content.
- Keep file path confinement centralized and reject traversal or symlink escapes before file IO.
- During orchestration execution, preserve current alias semantics from workspace layout helpers: `workspace/` is the selected Work Area when present, otherwise the effective durable workspace; `root/` is the owner workspace root; `outputs/` is the current run-local `runs/<runId>/outputs/` staging directory; `run/` is current run staging. Final output destinations are backend promotion targets, not tool write aliases. Legacy scratch/job aliases are compatibility only when still accepted.
- File and shell tools that resolve a confined runtime target also record the active runtime path on `OrchestrationTaskContextHolder` so subsequent model-backed prompt/context assembly can refresh `AGENTS.md` layers for the real target path.
- Keep shell command execution structured; do not accept raw shell command strings.
- Keep web tool names and outputs compact, citation-friendly, and explicit about failures/truncation.
- Keep planning tools narrow; they should set the goal/current planning task, add/replace/delete one keyed plan item, queue free-response questions, mark approval readiness, or request completion validation, not orchestrate execution.
- Keep plan execution evidence concise, user-auditable, and tied to the active saved plan.

### Validation
- Add focused tests for tool registry resolution, transcript rendering, and truncation policy changes.
- Add focused tests for file path confinement, chunked reading, search output, and anchored edits.
- Add focused tests for shell command allowlists, wildcard override gating, working-directory confinement, timeout handling, and output truncation.
