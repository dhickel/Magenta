# Validation Matrix

| Phase | Issues | Required Code Validation | Browser Validation | Evidence |
| --- | --- | --- | --- | --- |
| 01 | #9 | Repository security tests for identifier whitelists and old injection payloads; focused repository suite; startup if bean wiring changes. | No. | `validation/phase-01-validation-report.md` |
| 02 | #10 | WorkflowRepository migration tests for known idempotent duplicate-column case and unexpected SQL failure; focused workflow repository tests. | No. | `validation/phase-02-validation-report.md` |
| 03 | #11 | GlobalExceptionHandler tests for Spring constructor behavior and audit-null safety; focused web tests. | No. | `validation/phase-03-validation-report.md` |
| 04 | #12 | Executor saturation/rejection test proving returned future fails and later same-conversation turn proceeds. | No. | `validation/phase-04-validation-report.md` |
| 05 | #13 | Repository/service tests for late completion/failure/waiting/checkpoint writes after `CANCEL_REQUESTED`. | No. | `validation/phase-05-validation-report.md` |
| 06 | #19 | Concurrent enqueue/claim FIFO test; schema/index migration test if uniqueness is added. | Focused `/chat` queue browser check if client-visible drain behavior changes. | `validation/phase-06-validation-report.md` |
| 07 | #14, #15 | SSE lifecycle callback tests, ActiveTurnRegistry tests, ChatController/ChatService tests for plain/tool/tool-fallback interrupt semantics. | Yes if advertised interrupt or stream browser contract changes. | `validation/phase-07-validation-report.md` |
| 08 | #16 | Controller/service/API tests for all public assignment entry points and `AssignmentTemplateParser` validation. | No unless UI forms change. | `validation/phase-08-validation-report.md` |
| 09 | #17 | WorkflowValidator and WorkflowRunner PASS_THROUGH fixture tests; docs/spec alignment. | Optional only if workflow editor UI fields change. | `validation/phase-09-validation-report.md` |
| 10 | #18 | WorkflowRunner delegation tests proving no fabricated completion; supported/unsupported behavior documented. | Optional only if workflow UI labels/controls change. | `validation/phase-10-validation-report.md` |
| 11 | #33 | Static/package-guide enforcement checks; controller render tests for refactored SlotKey templates; SimplyPages compile/render tests; stale "Avatar UI" wording cleanup where touched. Do not remediate #8 dashboard empty-row/density behavior. | Required desktop/mobile proof for refactored Home dashboard/static surfaces and HTMX swaps. | `validation/phase-11-validation-report.md` |

## Final Validation

After all phase reports pass, run a final stale-reference sweep and final quality review. The final validator checks:

- Every phase report exists and reconciles commands/browser evidence.
- Every closed issue has a commit reference and closeout note.
- #8 is still open with no attempted closeout from this plan.
- Docs/spec/changelog updates are present and not stale.
- `artifacts/github-issue-backlog-remediation-20260531/validation-summary.json` is internally consistent.
- No required browser validation remains pending.
