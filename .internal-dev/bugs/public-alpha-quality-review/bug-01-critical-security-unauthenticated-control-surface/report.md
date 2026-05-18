# Unauthenticated Public Mutation and Control Surface

## Summary

The public web/API surface has no authentication or CSRF protection while exposing state mutation, shell execution, queue control, settings changes, and destructive lifecycle operations.

## Scope

Public routes under `io.mindspice.magenta2.api.web`.

## Reproduction

1. Run the app normally.
2. From an unauthenticated browser/client, call public mutation routes such as `/api/agents`, `/api/settings/runtime`, `/agents/_detail/{agentId}/exec`, or `/agents/_lifecycle/{agentId}/hard-delete`.

## Expected

Remote-host alpha surfaces require authentication and mutation protection appropriate for operator controls.

## Actual

Static review found no Spring Security dependency/configuration, no `SecurityFilterChain`, and no CSRF protection; mutation routes are publicly mapped.

## Evidence

- `pom.xml` dependency block has web/JDBC/actuator but no Spring Security.
- `OrchestrationController.java:5742` exposes shell execution UI/POST path.
- `OrchestrationController.java:6168` exposes agent hard delete.
- `RuntimeSettingsController.java:15` exposes runtime settings mutation.
- `AgentProfileController.java:56`, `JobController.java:49`, `ProjectController.java:37`, and `WorkspaceController.java:68` expose additional mutation APIs.
- 2026-05-18 update: `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java` adds the alpha HTTP gate; `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java` covers unauthenticated, CSRF-missing, HTMX error, and authenticated-with-CSRF mutation paths.

## Impact

Critical for a remote-host public alpha: unauthenticated callers can mutate runtime state and invoke operator controls.

## Status

Implementation complete; pending validation-agent review for the security-access-control domain.

## Next Action

Run the planned validation pass against the original bug evidence and security domain route matrix before marking bug-01 passed.
