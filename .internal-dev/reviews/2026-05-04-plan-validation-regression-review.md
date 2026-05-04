# Scope

Reviewed the recent plan-validation refactor and the latest saved-plan execution that ended without calling `plan_report` or `plan_complete`.

Files and evidence reviewed:

- `.internal-dev/changelogs/2026-05-03-plan-validation-evidence.md`
- `.internal-dev/changelogs/2026-05-01-planning-deterministic-keyed-flow.md`
- `.internal-dev/changelogs/2026-05-01-planning-turn-contract.md`
- `.internal-dev/knowledge/chat-plan-mode-flow.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `chat-memory.db` conversation `a7880ebe-73f3-4c42-abd6-ce478d7dc597`

# Findings

1. EXECUTE_PLAN has no hard terminal-state enforcement.

`ChatService.toolChat()` repairs invalid PLAN turns, but only when `mode == PlanMode.PLAN`. There is no equivalent repair when `mode == PlanMode.EXECUTE_PLAN` and the model returns ordinary assistant text without calling `plan_complete`.

The latest execution confirms this path: conversation `a7880ebe-73f3-4c42-abd6-ce478d7dc597` used web tools, returned a normal report, and the saved plan ended `NORMAL` / `NEEDS_REVIEW` with only fallback evidence:

`Deviation: execution returned without a structured completion ledger.`

There were no persisted `plan_report` or `plan_complete` tool transcripts.

2. The refactor strengthened prompt/tool guidance, but left validation compliance model-driven.

`PlanService.executionInstructions()` tells the model to call `plan_report` and `plan_complete` with one `criterionResults` entry per validation criterion. `PlanSaveTools` exposes the new `criterionResults` parameters. However, all completion parameters are optional and the service does not check whether the model actually called the terminal validation tool before accepting the final assistant message.

This means the refactor can improve successful tool calls, but it does not prevent the exact bypass observed in the latest session.

3. The fallback state is diagnostic, not corrective.

After `chat(request)` returns, `ChatService.executeSavedPlan()` records fallback execution evidence and marks the plan `NEEDS_REVIEW` if it is still in `EXECUTE_PLAN`. That correctly avoids falsely marking the plan completed, but it happens after the model turn is over. The model is not forced to continue and call `plan_complete`, so the user receives a finished-looking assistant answer without validator feedback.

4. The criteria refactor likely made compliance more fragile for weaker tool-following models.

The old flat `evidence` shape was simpler. The new `criterionResults` contract is better for validation quality, but it is also more demanding: exact criterion text, one entry per criterion, and artifact handling. Without a backend repair loop, a model can ignore the harder terminal contract and produce a final answer.

# Risk Assessment

High for saved-plan execution reliability. The system now avoids the previous false `COMPLETED` state, but still allows an execution to finish without validation. For the user, this looks like the agent completed the task but the plan machinery quietly marked it `NEEDS_REVIEW`.

The core regression is not in `PlanCompletionService` validation itself. The validator is never reached when the model skips `plan_complete`.

# Recommendations

Add an EXECUTE_PLAN terminal repair path mirroring PLAN-mode repair.

Recommended behavior:

- If the model returns a non-empty final assistant response while the plan is still `EXECUTE_PLAN`, append a control message and retry the model turn.
- The control message should say the execution turn cannot finish until `plan_complete` is called, and it should require criterion-mapped evidence or explicit unmet criteria.
- Limit retries, then keep the existing fallback `NEEDS_REVIEW` state if the model still refuses.
- Add a focused service test where a fake model first returns ordinary final text, then calls `plan_complete` after the repair control message.

Add a pre-validation coverage check before calling the validator.

Recommended behavior:

- Compare approved validation criteria to submitted `criterionResults`.
- If any criterion is missing, return remediation directly without spending a validator call.
- Keep artifact auto-read as a supplement, not a substitute for criterion coverage.

Consider restricting EXECUTE_PLAN tools to an explicit allowlist that includes `plan_report` and `plan_complete`.

The current execution mode allows every default-agent tool except planning draft tools. With `approvedTools: ["*"]`, this works, but the terminal tools compete with the full tool set. A compact execution-specific allowlist would make the required completion path more prominent.

# Follow-ups

- Existing focused tests passed: `mvn -q -Dtest=PlanServiceTest,PlanSaveToolsTest,ChatServiceTest test`
- No code fix was applied in this review.
