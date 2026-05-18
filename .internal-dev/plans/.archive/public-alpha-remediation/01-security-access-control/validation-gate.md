# Security and Access Control Validation Gate

## Validator Instructions

Use `gpt-5.3-codex` with reasoning effort `medium` unless explicitly overridden. Before validating, read:

- `.internal-dev/reviews/public-alpha-quality-review/domain-api-web.md`
- `.internal-dev/reviews/public-alpha-quality-review/horizontal-security-error-htmx.md`
- `.internal-dev/reviews/public-alpha-quality-review/domain-orchestration-runtime.md`
- `.internal-dev/reviews/public-alpha-quality-review/domain-workflow.md`
- bug reports 01, 02, 11, and 12

## Required Checks

- Security tests reject unauthenticated/missing-CSRF mutation and allow authenticated configured alpha flow.
- Path segment validator tests cover traversal/separator/absolute/encoded invalid cases.
- Workflow XSS payload is rendered inert.
- Agent lifecycle cross-agent mutation is rejected.
- Focused changed-area Maven tests pass.
- Full `mvn test` passes.
- Bounded Spring startup reaches `Started Magenta2Application`.

## Failure Protocol

Record failures in root `progress.md` and append enough detail to `implementation_notes.md` for the implementing agent to fix without rereading the entire review. Do not mark the domain passed until original review concerns are directly disproven.
