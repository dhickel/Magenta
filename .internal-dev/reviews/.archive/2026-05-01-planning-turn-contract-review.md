# Scope

Reviewed the planning-chat behavior around `PlanService` prompt construction, plan tool descriptions, chat-mode prompt selection, and plan-mode tool routing.

# Findings

- Plan mode relied on model discretion to either call `plan_ask_questions` or `plan_ready_for_approval`. If the model answered conversationally, the backend had no invariant that forced a queued planning question or an approval-ready state.
- The global default prompt strongly favors autonomous execution and avoiding user handoff. Plan mode replaces that prompt, but its own wording still allowed the model to infer assumptions when it believed details did not strictly block a plan.
- `plan_ask_questions` was described as available for asking questions, but not as the required fallback when a plan is not ready for approval.
- `plan_ready_for_approval` correctly gates persistence with required plan fields, but quality and user-preference confirmation still depend on prompt/tool behavior.

# Risk Assessment

The immediate risk was behavioral rather than schema-related: a planning turn could complete with only text, leaving the user with no UI question and no approval preview. This is most likely with models that prefer concise autonomy or if tool calling is unavailable/falls back to plain chat.

# Recommendations

- Make PLAN-mode instructions state that every planning turn must move the planning UI forward by either queueing questions or marking a complete draft ready for approval.
- Tell the model to ask when user preferences, constraints, or tradeoffs would otherwise be guessed.
- Tighten planning tool descriptions so `plan_ask_questions` is the default path for incomplete plans and `plan_ready_for_approval` is reserved for guess-free drafts.
- Consider a future backend postcondition if model-only prompting is not reliable enough: detect `PLAN` + `DRAFT` + no pending question after a turn and either retry with a repair prompt or surface a deterministic fallback question.

# Follow-ups

No backend enforcement was added in this pass. If the strengthened prompt still permits dead-end planning turns with the selected model, add a deterministic service-level repair step.
