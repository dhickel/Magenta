# Topic

Plan step text normalization

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanText.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepository.java`
- `audit_event` and `ai_chat_plan_steps` evidence from conversation `c56dac5e-9544-4dba-9fb5-6595bc8f5c2e`

# Key Takeaways

Tool argument text from models may include XML-ish CDATA wrappers even when the tool schema expects plain strings. If stored verbatim, markdown rendering can interpret the wrapper as markup and hide otherwise valid content.

Normalize user-visible plan item text at the service boundary and also normalize legacy rows on repository read. This preserves current affected plans without requiring immediate manual database migration.

# Engine Relevance

Plan approval depends on the user seeing the same executable steps that the backend persisted. Text normalization keeps the planning tool path, approval preview, and execution instructions aligned.

# Open Questions

None.
