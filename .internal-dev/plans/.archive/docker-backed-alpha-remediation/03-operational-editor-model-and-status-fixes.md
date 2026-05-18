# Phase 03: Operational Editor, Model, Output, And Status Fixes

## Context

The validation reports found multiple operator-facing contract bugs that are not safe to defer for alpha:

- Plan field type wire names do not persist.
- Add-step and add-deliverable controls create blank values that are immediately cleaned away.
- Plan saves auto-approve drafts.
- Output rows cannot be opened.
- Job status stays `DRAFT` after terminal runs.
- Agent history is a static placeholder.
- Model dropdowns mix aliases and raw model names.
- Disabled schedules/reactions messages say to enable with `=false`.
- Job item bindings have no guidance for required inputs.
- A minor Docker status endpoint mismatch can confuse tools.

This phase intentionally centralizes `OrchestrationController.java` work so multiple UI workers do not edit the same large controller at the same time.

## Goal

Make the operational UI and API reflect the backing contracts: editor changes persist, model choices are valid, output content is accessible, job/history status is real, and operator guidance prevents predictable runtime failures.

## In Scope

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeSettingsController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- Focused controller/service tests for the listed contracts.

## Out of Scope

- Shell/Docker execution path changes belong to Phase 2.
- Agent side-panel chat belongs to Phase 4.
- Broad visual redesign is out of scope; keep SimplyPages/HTMX patterns.

## Implementation Steps

1. Read package guides:
   - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`

2. Fix plan field type persistence.
   - Target: `OrchestrationController.updateField(...)`.
   - Replace `PlanFieldType.valueOf(typeStr)` with `PlanFieldType.fromWireName(typeStr)`.
   - Use `existing.type().wireName()` as the fallback form value, not `name()`.
   - Add tests for `user_message`, `file_path`, `json`, and enum-name fallback.

3. Fix add-step and add-deliverable flows.
   - Target: plan list-item add/update methods in `OrchestrationController`.
   - Do not save an empty string that `PlanService.cleanSteps()`/`cleanList()` removes before the row renders.
   - Preferred behavior: add a persisted placeholder such as `New step` or accept the first text from the add form.
   - Render the row immediately with HTMX autosave controls.

4. Restore draft/finalize lifecycle.
   - Target: `PlanService.saveTask(...)` and web finalize routes.
   - Save should preserve incoming `PlanStatus.DRAFT` and `READY_FOR_APPROVAL`.
   - Finalize should explicitly validate and set `APPROVED`.
   - Existing approved plans must remain approved.
   - Add tests for create -> draft, save -> draft, finalize -> approved.

5. Add output content access.
   - Add a confined service method to load artifact content by artifact ID.
   - Endpoint options:
     - `GET /outputs/_content/{artifactId}` returns an HTML fragment for text/json/markdown.
     - `GET /api/outputs/{artifactId}/content` returns content metadata and text where safe.
     - `GET /api/outputs/{artifactId}/download` streams the file for download.
   - Use artifact IDs rather than path parameters containing user-controlled filenames.
   - Reject missing, directory, non-data-root, and too-large files with clear status/errors.
   - Update `/outputs/_list`, agent outputs, job outputs, and project outputs rows with HTMX "View" actions.

6. Synchronize job status.
   - Target: `JobService`, `JobRepository`, `OrchestrationRunnerService.runJob(...)`.
   - When a job run starts, mark the job definition `RUNNING`.
   - On terminal assignment success, mark `COMPLETED`.
   - On failure, mark `FAILED` with run events preserved.
   - Empty zero-work jobs may complete, but the status must still move out of `DRAFT`.

7. Replace static agent history.
   - Target: `OrchestrationController.agentHistoryTab(...)`.
   - Render recent assignments, job run events, workflow/task run IDs, statuses, and timestamps for the selected agent.
   - Keep the queue tab focused on active/recent assignments; history can be chronological and read-only.

8. Make model dropdowns canonical.
   - Source of truth: `RuntimeSettingsService` validates runtime model keys against configured file model aliases.
   - Dropdown option `value` attributes must be valid aliases, not raw remote names, unless backend reverse-resolution is deliberately implemented.
   - Display labels may include the raw model name for readability: `local-qwen (qwen3.6:35b)`.
   - Apply consistently to settings, plan, job, project, and agent profile editors.

9. Improve job item binding guidance.
   - When adding a PLAN item, show required inputs from the selected plan.
   - Validate `bindingsJson` at add/save time if the selected plan has required inputs.
   - Error before submission when required bindings are missing.

10. Fix minor messages and endpoint compatibility.
   - Schedules/reactions disabled copy must say `=true`.
   - Add a compatibility redirect or fragment alias for `/agents/_docker/{agentId}/docker-status`, or remove any stale references and document the canonical path.
   - Explain output filename behavior in UI if field-name-based materialization remains.

11. Add tests.
   - Controller tests for plan field type persistence and list-item add behavior.
   - Service tests for draft/finalize status.
   - Controller/API tests for output content view/download and path confinement.
   - Runtime tests for job status transitions.
   - Controller tests for model dropdown values and settings save.
   - Controller test for agent history rendering after completed/failed assignments.

## Validation

Run:

```bash
mvn -q -Dtest=OrchestrationControllerTest test
mvn -q -Dtest=OutputControllerTest test
mvn -q -Dtest=RuntimeSettingsControllerTest test
mvn -q -Dtest=PlanServiceTest test
mvn -q -Dtest=OrchestrationRuntimeTest test
```

Then:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

HTMX/browser validation to hand to Phase 5:

- Create a draft plan, add field types, steps, deliverables, save, reload, and finalize.
- Select every model dropdown and save with no alias/raw-name validation error.
- Create a job with a required-input plan item and verify missing bindings fail before runtime.
- Run a job and verify job list/detail/dashboard status changes.
- Open `/outputs`, click output view, read text/json content, and download a file.
- Open agent History and verify completed/failed assignments appear.

## Exit Criteria

- `DEFECT-03-01`, `DEFECT-03-02`, `DEFECT-03-04`, `DEFECT-03-05`, `DEFECT-07-01`, `DEFECT-05`, and `DEFECT-06` are fixed and validated.
- Operator UI no longer exposes controls that predictably save invalid state.
- Output rows are no longer metadata-only dead ends.
