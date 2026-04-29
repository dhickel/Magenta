# Date

2026-04-29

# Change Summary

Changed saved-plan execution so returned model turns leave plans in `NEEDS_REVIEW` with persisted execution evidence instead of automatically treating the plan as completed.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/js/chat-client.js`
- `src/main/resources/schema.sql`
- Affected focused tests under `src/test/java/`

# Behavioral Impact

Plan drafts can now store acceptance criteria. During execution, models are instructed to call `plan_report` with evidence, artifacts, deviations, and unmet criteria before the final answer. `/exec-plan` and `/clr-exec-plan` now leave saved plans in `NEEDS_REVIEW`; if no structured report is recorded, Magenta persists a fallback evidence entry noting that gap. The browser plan banner displays execution evidence when present.

# Risks

The first version relies on model tool use for high-quality evidence. It surfaces missing evidence but does not yet perform automatic verification against typed criteria.

# Follow-up Items

- Consider a user-facing command to accept or close a `NEEDS_REVIEW` plan.
- Consider typed verification checks after observing real plan_report output quality.
