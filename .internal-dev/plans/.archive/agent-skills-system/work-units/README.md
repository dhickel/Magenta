# Work Units

## Classification

Large. This feature spans application-root filesystem contracts, parser/loader services, schema and assignment metadata, chat/tool runtime integration, web/API surfaces, guided UI creation, docs/specs, browser validation, and final external-spec adherence review.

## Phase Summary

| phase | work unit | primary owner | dependencies | notes |
| --- | --- | --- | --- | --- |
| 01 | Contract, docs, and governance baseline | implementation worker | branch only | Locks intended contracts before code; updates specs/docs/AGENTS/knowledge. |
| 02 | Skill repository, parser, discovery, metadata | implementation worker | Phase 01 pass | Adds backend domain package, root path handling, parser, DB metadata, discovery tests. |
| 03 | Skill assignments and chat activation | implementation worker | Phase 02 pass | Adds agent assignment metadata, runtime catalog filtering, activation tool/service, prompt integration. |
| 04 | Skill APIs and file-management service | implementation worker | Phase 02 pass; can overlap late Phase 03 only after service contracts stabilize | Adds REST/fragment endpoints and safe skill file operations. |
| 05 | Skill browser/editor and guided creation UI | `gpt-5.3` xhigh implementation worker | Phase 04 pass; Phase 03 pass for assignment controls | Owns UI implementation and focused browser proof criteria. |
| 06 | Integration, spec-adherence, and closeout | implementation worker plus validators | Phases 01-05 pass | Runs full validation, final external spec review, `.internal-dev` closeout, changelog, archive readiness. |

## Session Policy

- Use one consistent worker session per phase.
- Use one consistent validator session per phase.
- If validation fails, resume the same worker with the validator's remediation handoff, then resume the same validator.
- Replace a worker only if it misunderstood the directive, violated boundaries, or remediation moved into a different domain.
- Use a fresh validator when criteria changed, a new domain was touched, more than two failed cycles occurred, or the validator missed an obvious issue.

## Parallelization

- Phase 01 must complete first.
- Phase 02 and selected Phase 01 doc follow-ups must not overlap in the same files.
- Phase 03 depends on Phase 02 service contracts.
- Phase 04 depends on Phase 02 and may begin before Phase 03 is fully validated only if it avoids runtime catalog/activation files.
- Phase 05 starts after Phase 04 and Phase 03 pass.
- Phase 06 starts after all mutating phases pass.
