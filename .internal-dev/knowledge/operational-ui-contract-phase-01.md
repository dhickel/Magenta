# Topic

Operational UI Phase 01 contract stabilization.

# Source References

- `.internal-dev/plans/operational-ui-contract-refactor/01-contract-repair-and-data-model.md`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/resources/static/js/orchestration/projects.js`
- `src/main/resources/static/js/orchestration/dashboard.js`
- `src/main/resources/static/js/orchestration/outputs.js`
- `src/main/resources/static/js/orchestration/inbox.js`

# Key Takeaways

- Treat `JobDefinition` as the public job API model for operational pages. Empty `DRAFT` jobs are valid because the manual UI creates metadata before users add ordered items.
- Job item requests should accept UI-friendly `itemType` and `itemOrder` aliases, but normalize into `JobWorkItem.type` and `JobWorkItem.order` before service validation.
- Project UI must use `name` and `description`; `title` and `summary` are compatibility aliases on request DTOs only.
- Workspace endpoints should return display metadata and relative paths, not raw filesystem roots.
- Output browsing should use `/api/outputs` as the canonical query endpoint. `/api/jobs/{jobId}/outputs` should return the same artifact shape as a list.
- Agent inbox messages in the runtime inbox are read/handled records. Do not render approve/reject controls unless the endpoint is actually backed by workflow approval semantics.

# Engine Relevance

Future orchestration UI phases should add visual and interaction redesign on top of these contracts instead of reintroducing page-local probing. If a page needs data, add one explicit API route and test that the static JS calls it with fields accepted by the controller request DTO.

# Open Questions

- Should `AssignmentService` consume canonical `JobDefinition` rows for `JOB_RUN` assignments, or should legacy `OrchestrationJob` be fully migrated into the job definition service?
- Should job events become durable first-class records, or are run-derived event summaries enough until the larger jobs/projects operational surface is implemented?
