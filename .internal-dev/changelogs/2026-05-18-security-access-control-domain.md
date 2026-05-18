# Security Access Control Domain

## Date

2026-05-18

## Change Summary

Completed public-alpha remediation domain `01-security-access-control`. The domain added the alpha auth/CSRF gate, strict plain path-segment id validation, stored-XSS-safe workflow graph rendering, and route-agent scoped assignment lifecycle controls.

## Files

- `pom.xml`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
- `src/main/java/io/mindspice/magenta2/core/util/PlainPathSegmentValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/resources/static/js/alpha-security.js`
- `src/main/resources/static/js/orchestration/workflows.js`
- Security, path-segment, workflow graph, and lifecycle controller/runtime tests
- `.internal-dev/plans/public-alpha-remediation/**`
- `.internal-dev/bugs/public-alpha-quality-review/bug-01*`, `bug-02*`, `bug-11*`, `bug-12*`

## Behavioral Impact

Unsafe public mutation/control routes require the configured alpha credential and CSRF, filesystem path ids are rejected unless they are plain path segments, workflow graph persisted text renders inert, and assignment lifecycle controls cannot mutate another route agent's assignments.

## Risks

Existing invalid persisted ids will now fail validation when used. Remote deployments must override the development fallback alpha password before exposure.

## Follow-up Items

Domain 02 should build from this validated integration tip and consume the plain segment validation policy for workspace/tool confinement.
