# Date

2026-05-31

# Change Summary

Workflow repository startup migrations now pre-check existing SQLite columns before issuing compatibility `ALTER TABLE` statements. Expected warm-start columns are skipped idempotently, while unexpected migration inspection or alter failures raise a contextual startup-time exception instead of being silently swallowed.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md`

# Behavioral Impact

Existing workflow databases with already-applied columns remain harmless on startup. Schema drift or failed workflow migrations now fail visibly during repository construction, which prevents partially migrated workflow persistence from surfacing later as cryptic runtime query failures.

# Specification Impact

Workflow inline migration policy is now documented as idempotent for expected already-existing columns and fail-visible for unexpected schema inspection or alteration failures. Formal migration tooling remains outside this phase and tracked as architecture hardening.

# Validation

- `mvn -q -Dtest=WorkflowRepositoryTest test` passed.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` reached `Started Magenta2Application` on port `40841`; command then exited `124` when the bounded timeout stopped the running server.

# Risks

The repository still uses inline SQLite compatibility migrations rather than a formal schema migration tool by directive. The focused failure test forces one `ALTER TABLE` failure path; broader database-level corruption remains covered by visible startup failure rather than recovery.
