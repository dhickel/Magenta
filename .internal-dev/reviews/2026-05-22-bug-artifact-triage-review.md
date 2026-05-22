# Bug Artifact Triage Review (2026-05-22)

## Scope

Reviewed active bug reports under `.internal-dev/bugs/` (excluding `.archive`) and validated each against current relevant code/docs plus GitHub issue state checks.

## Findings

Archived:
- `.internal-dev/bugs/.archive/bug-config-example-api-key`
  - Reason: `config/ai-config.example.json` now uses placeholder `apiKey` (`set-your-provider-api-key`) and the report itself records local sanitization on 2026-05-18. No active code defect remains in this repository.

Still active:
- `.internal-dev/bugs/public-alpha-remediation/bug-empty-job-runs-remain-running`
  - Reason: In `OrchestrationRunnerService#runJob`, when `job.items()` is empty the item loop is skipped and assignment is completed, but no `JobRun` terminal update is performed. `resumeOrStartJobRun` moves run to `RUNNING`, and terminal transition currently happens through `updateWorkItemRun` (which is never called for empty jobs). This remains a plausible outstanding bug.

Bugs needing human review:
- `bug-empty-job-runs-remain-running`
  - Confirm intended product behavior for empty jobs (`COMPLETED` immediately vs rejected as invalid submission) before implementation changes.

GitHub issue check status:
- `gh` CLI available and functional.
- Executed:
  - `gh issue list --state all --limit 200`
  - `gh issue list --state all --search "config example api key deepseek" --limit 50`
  - `gh issue list --state all --search "empty job runs remain running" --limit 50`
- Result: only issues `#3`, `#4`, `#5` were returned and are unrelated; no matching closed issue found for either bug artifact.
- Follow-up: mirrored active bug `bug-empty-job-runs-remain-running` to GitHub issue `#6`.

## Risk Assessment

- Low risk for archived config example key bug: resolved in current repository state.
- Medium risk for empty-job-run bug remaining active: run-history/status correctness may still be misleading in operational UI and APIs.

## Recommendations

1. Keep `bug-empty-job-runs-remain-running` active.
2. Track the empty-job terminal-status defect through GitHub issue `#6`.
3. Decide and document expected behavior for empty jobs, then implement runner/job-service terminal handling accordingly.

## Follow-ups

- No `.codex-orchestration/chat-planning-question-composer/notes.md` update made; no cross-agent coordination delta was identified from this triage pass.
