# Plain Path Segment ID Validation

## Topic

Central validation for ids that are composed into filesystem paths.

## Source References

- `.internal-dev/plans/public-alpha-remediation/01-security-access-control/subplan-02-id-segment-validation.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-02-critical-security-agent-id-path-traversal/report.md`
- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`

## Key Takeaways

- Data-root confinement alone is not enough when an id is supposed to stay inside a fixed subtree like `agents/<id>`.
- Validate user-controlled ids before composing strings such as `agents/` + id + `/workspace`.
- The central validator rejects blank values, surrounding whitespace, dot-only names, `/`, `\`, Windows drive syntax, absolute paths, and percent-encoded path syntax.
- Agent profile creation treats `null` id as omitted and generates a UUID, but explicit blank ids are invalid.

## Engine Relevance

Use `PlainPathSegmentValidator.requirePlainSegment(value, label)` for any future id that becomes a single filesystem path segment. Keep broader file path validation separate; this helper is intentionally for ids, not user-selected relative file paths.

## Open Questions

- Whether a future migration should detect and repair any invalid agent ids that may already exist in persisted local data.
