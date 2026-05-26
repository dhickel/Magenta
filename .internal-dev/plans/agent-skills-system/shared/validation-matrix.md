# Validation Matrix

| Area | Required Evidence | Commands Or Checks | Owner |
| --- | --- | --- | --- |
| Official spec verification | Worker and validator reports name the official pages opened and summarize checked requirements. | Manual evidence in reports; final spec-adherence review. | All workers/validators |
| Parser/frontmatter | Valid YAML parses; required fields enforced; optional fields captured; invalid names/descriptions produce expected diagnostics. | Focused parser tests, e.g. `mvn -Dtest='*AgentSkill*Parser*,*AgentSkill*Validation*' test` | Phase 02 validator |
| Directory contract | Root `skills/` discovery finds `SKILL.md`; ignores non-skill dirs; lists `scripts/`, `references/`, `assets/`; handles name mismatch. | Loader tests with temp dirs and malformed fixtures. | Phase 02 validator |
| Safe malformed handling | Broken YAML, missing frontmatter, missing description, traversal/symlink attempts, and unreadable files do not crash discovery or UI. | Unit/service/controller tests; validator negative review. | Phase 02/04 validators |
| DB metadata | Skills persist with status, diagnostics, paths, timestamps, optional directory flags, and refresh after edits. | Repository/service tests against SQLite test DB. | Phase 02 validator |
| Assignment metadata | Agent assignments persist separately from tool allowlists; assigned vs unassigned filtering works; duplicates are prevented. | Repository/service tests, `rg` check that skill assignments are not stored in approved tool JSON. | Phase 03 validator |
| Catalog disclosure | Assigned valid skills appear as catalog metadata only; unassigned/invalid skills and no-skill cases omit catalog/tool exposure. | Chat prompt/tool integration tests. | Phase 03 validator |
| Activation loading | Activation returns full skill instructions and resource list without eager resource reads; duplicate activation is no-op/explicitly deduped. | Activation service/tool tests. | Phase 03 validator |
| API/file management | Skill APIs validate route names, path confinement, status codes, diagnostics, file read/save/create, and refresh. | Controller/API tests. | Phase 04 validator |
| UI browser/editor | Skill list/detail, filter, diagnostics, file viewer/editor, `SKILL.md` edit, add file, assignment controls, guided creation. | Focused Playwright checklist with desktop/mobile screenshots and visual critique. | Phase 05 validator plus Playwright agent |
| Docs/spec closeout | Living specs, docs, knowledge, AGENTS guidance, changelog, and plan archival/update match implementation. | Targeted `rg`, doc review, changelog review. | Phase 01 and Phase 06 validators |
| Integration | All phases compose; startup passes; no undocumented spec divergence remains. | `mvn test`; bounded startup; integration validator; final spec validator. | Phase 06/integration validators |

## Required Minimum Test Cases

- Skill discovery from the Magenta root skill repository.
- Valid `SKILL.md` frontmatter parsing.
- Missing/invalid required fields.
- Parent directory/frontmatter name mismatch handling.
- Optional `scripts/`, `references/`, and `assets/` directory visibility.
- Catalog-only loading behavior.
- Full skill body activation/loading behavior.
- Activation deduplication.
- Assigned vs unassigned skill availability.
- Loader behavior after skill file edits.
- Safe failure for malformed skills.
- Spec-adherence validation by a `gpt-5.5` xhigh validator against the official spec.

## Final Validation Commands

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
rg -n "Agent Skills|agent skills|skills/|SKILL.md|allowed-tools|\\.agents/skills|skill assignment" src/main/java src/test/java docs .internal-dev/specifications .internal-dev/knowledge
git status --short
```

The `rg` command is not a pass/fail command by itself. Validators use it to verify active-vs-deferred wording, route/API/docs consistency, and absence of accidental project-local scope claims.
