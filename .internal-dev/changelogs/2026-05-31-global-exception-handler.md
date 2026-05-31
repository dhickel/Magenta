---
schema_version: 1
document_type: changelog
status: finalized
owner: api
created: 2026-05-31
---

# Global Exception Handler Constructor Cleanup

## Date

2026-05-31

## Change Summary

- Removed the public no-argument `GlobalExceptionHandler` constructor that delegated to a hidden `null` audit dependency.
- Kept audit recording optional through one explicit Spring constructor accepting `Optional<AuditService>`.
- Updated focused handler tests to construct the advice with an explicit absent-audit policy and assert that the public no-argument constructor is not available.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java`
- `src/test/java/io/mindspice/magenta2/api/web/GlobalExceptionHandlerTest.java`

## Behavioral Impact

HTTP exception statuses and error response bodies are unchanged.

## Specification Impact

None. This only clarifies Spring construction and optional audit dependency handling; route status mappings and response bodies are unchanged.

## Risks

Low. Spring construction now depends on `Optional<AuditService>` injection, which was verified by bounded application startup.

## Follow-up Items

None.

## Validation

- Passed: `mvn -q -Dtest=GlobalExceptionHandlerTest test`
- Startup reached `Started Magenta2Application`: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - The command exited `124` when `timeout` stopped the running application after successful startup.
