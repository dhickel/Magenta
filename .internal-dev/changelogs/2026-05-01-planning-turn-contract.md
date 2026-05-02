# Date

2026-05-01

# Change Summary

Strengthened PLAN-mode instructions and planning tool descriptions so planning turns self-iterate through research and plan updates, then relinquish control only through queued user questions, a focused planning discussion point, or a draft marked ready for approval.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveToolsTest.java`
- `.internal-dev/reviews/2026-05-01-planning-turn-contract-review.md`

# Behavioral Impact

Planning prompts now explicitly discourage ending with only conversational text and direct the model to ask concrete user questions, ask a series of questions, discuss a specific planning aspect, or call `plan_ready_for_approval` instead of guessing preferences, constraints, or tradeoffs.

# Risks

This is prompt-level guidance, not hard backend enforcement. A weak or non-tool-capable planning model can still fail to satisfy the contract.

# Follow-up Items

Consider adding service-level post-turn repair if prompt guidance does not reliably prevent dead-end planning turns.
