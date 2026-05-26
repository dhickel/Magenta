# Validation Matrix

| Area | Required Evidence | Commands Or Checks | Owner |
| --- | --- | --- | --- |
| Static layout source | Unit tests prove constants/helpers produce target paths and reject bad ids. No ad hoc legacy strings remain in active code except compatibility markers. | `mvn -Dtest='*Workspace*Path*,*Workspace*Layout*' test`; targeted `rg` checks. | Phase 02 validator |
| Work Areas | Creation uses stable id disk dirs, display name stays DB-owned, Home is preserved, active-use guards still work, project/agent ownership is enforced. | `mvn -Dtest='WorkAreaServiceTest,WorkAreaControllerTest,WorkAreaExplorerServiceTest' test` | Phase 02/04 validators |
| Run staging | Task/workflow/job run staging uses `runs/<runId>/outputs/`, `outputs/` alias points there during execution, staging remains after terminal completion for at least one day. | Focused service tests plus retention clock fixture. | Phase 03 validator |
| Output promotion | Declared outputs copy/promote from run-local outputs to final agent/project/Work Area destination, with attribution and realpath confinement preserved. | `OutputDirectoryServiceTest`, `OutputArtifactServiceAttributionTest`, new promotion tests. | Phase 03 validator |
| Job semantics | Jobs do not create/own workspace dirs; job-bound routing uses assignment/project/Work Area; legacy fields are ignored or compatibility-only. | `JobServiceTest`, `JobRepositoryTest`, `AssignmentContextServiceTest`, targeted `rg jobWorkspace persistentWorkspace`. | Phase 03 validator |
| API payloads | Non-job task/workflow submissions require run display names; changed request/response shapes documented. | Controller/API tests for task/workflow/assignment/job routes. | Phase 04 validator |
| UI surfaces | Work Areas/projects browsable/editable; internal workspace roots/runs/outputs not normal MVP management. Visual critique passes. | Focused Playwright agent checklist after code validation. | Phase 04 validator plus Playwright agent |
| Development reset/migration | Schema-backed records migrate/reset known directories; ambiguous loose files are cleaned only within approved data roots; unrelated untracked artifacts untouched. | Migration dry-run/report, filesystem assertions, `git status --short`. | Phase 05 validator |
| Integration | All phase criteria compose, docs/specs agree with code, no old intended-future docs remain. | Full `mvn test`; bounded startup; integration review; focused Playwright results reconciled. | Integration validator |

## Final Validation Commands

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
rg -n "runtime/task-runs|runtime/workflow-runs|outputs/jobs|jobs/.*/workspace|scratch/" src/main/java src/test/java docs .internal-dev/specifications .internal-dev/knowledge
git status --short
```

The `rg` command may still return legacy/compatibility references only when explicitly marked as legacy, compatibility, or deferred cleanup.

