# Summary

Plan approval previews could render execution steps as missing or blank when a model supplied step text wrapped in XML CDATA markers.

# Scope

Observed on the recent plan conversation `c56dac5e-9544-4dba-9fb5-6595bc8f5c2e`. The issue affects plan item text normalization and preview rendering, not the planning tool's ability to persist steps.

# Reproduction

1. In PLAN mode, have the model call `plan_put_item` for section `step` with text shaped like `<![CDATA[**Step title** - details]]>`.
2. Mark the plan ready for approval.
3. View the approval preview.

# Expected

The preview renders the step text as normal markdown, and the saved plan executes with the same readable step text.

# Actual

The backend persisted the CDATA wrapper verbatim in `ai_chat_plan_steps.step_text`. The markdown/HTML rendering path could then treat the value as markup instead of normal text, causing the preview to appear as if steps were missing even though rows existed.

# Evidence

- `ai_chat_plan_steps` contained 8 rows for `c56dac5e-9544-4dba-9fb5-6595bc8f5c2e`.
- `audit_event` showed successful `plan_put_item` tool calls for step keys 1 through 8 before `plan_ready_for_approval`.
- Each persisted step started with `<![CDATA[` and ended with `]]>`.

# Impact

Users can approve or execute a plan whose backend steps exist while the approval preview suggests no steps were present. That undermines trust in the planning UI and makes it hard to tell whether the model, tools, or renderer failed.

# Status

Fixed in code by unwrapping CDATA-wrapped plan text during normalization and when reading legacy persisted step rows.

# Next Action

Run full tests and startup smoke, then rely on the normalizer for future plans. Existing CDATA rows are readable through the patched repository without manual database repair.
