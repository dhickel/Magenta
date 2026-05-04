# Date
2026-05-03

# Change Summary
Fixed plan completion validation false-rejections caused by poor evidence structure. Replaced the flat `evidence` parameter in `plan_complete` and `plan_report` with `criterionResults`, forcing the execution model to map evidence to specific validation criteria. Added automatic artifact file reading so the validator actually sees deliverable contents instead of opaque file paths. Updated execution instructions and validator prompt to demand specific, verifiable evidence per criterion rather than vague claims of completion.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`

# Behavioral Impact
- `plan_complete` and `plan_report` no longer accept a generic `evidence` list. They accept `criterionResults` with entries formatted as `"Criterion: <text> | Result: <evidence>"`.
- The validator now receives the contents of artifact files (auto-read with path confinement, 8000-char truncation) alongside the approved plan and criterion evidence.
- Execution instructions now guide the model to call `plan_report` incrementally (before compaction can lose context) and to provide one criterion result per validation criterion.
- Validator system prompt now demands specific evidence per criterion and names the unmet criterion in remediation steps.
- Evidence stored in the database uses the label `"Criterion result:"` instead of `"Evidence:"`.

# Risks
- Artifact reading depends on `aiConfig.dataRoot()` being configured. Falls back to error markers in the validation input if unavailable.
- File reading errors (path escapes, missing files, permissions) produce inline error markers rather than failing validation. The validator must recognize these as evidence gaps.
- The model must understand the `"Criterion: ... | Result: ..."` format. Description guidance is provided in the tool annotations.

# Follow-up Items
- End-to-end test with a real model executing a plan and calling `plan_complete` with criterion-mapped evidence.
- Consider adding a pre-validation check that warns if any criterion is not addressed by at least one `criterionResult` entry.
