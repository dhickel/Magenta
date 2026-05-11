# Date

2026-05-09

# Change Summary

Normalized plan item text that arrives wrapped in XML CDATA markers. New keyed step edits now unwrap CDATA before saving, and existing persisted plan step rows are unwrapped when loaded so current plans render and execute with readable step text.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanText.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveToolsTest.java`

# Behavioral Impact

Plan approval previews and execution instructions no longer lose or misrender step content when model tool arguments include CDATA-style wrappers. The existing affected plan loads with 8 clean steps through application code.

# Risks

Low. The normalizer only unwraps values where the full trimmed text starts with `<![CDATA[` and ends with `]]>`. Other plan text is unchanged except for the existing trim behavior.

# Follow-up Items

None.
