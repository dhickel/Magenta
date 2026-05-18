# Public Alpha Security ID Segment Validation

## Date

2026-05-18

## Change Summary

Implemented subplan 02 for bug-02 by adding central plain path-segment validation and applying it before agent ids and workspace lifecycle ids are composed into filesystem paths.

## Files

- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/test/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidatorTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfilePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathSegmentValidationTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentProfileControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-02-critical-security-agent-id-path-traversal/report.md`
- `.internal-dev/knowledge/plain-path-segment-id-validation.md`

## Behavioral Impact

Agent ids and workspace path ids must now be plain path segments. Explicit blank ids, dot-only ids, slash or backslash separators, absolute path syntax, and percent-encoded path syntax fail fast with `IllegalArgumentException`; REST agent profile create/update maps those validation failures to HTTP 400. Agent profile creation still generates an id when the incoming id is `null`.

## Risks

Existing invalid persisted ids, if any exist, will now fail service operations that read or mutate those ids. The subplan did not include a broad id migration.

## Follow-up Items

Run the external subplan validation gate before marking bug-02 as passed.
