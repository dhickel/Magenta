# Subplan 01: Alpha Auth and CSRF Gate

## Context

bug-01 reports public mutation/control routes including shell execution, hard delete, runtime settings, agent/profile/job/project/workspace mutation APIs, and queue controls without authentication or CSRF.

## Goal

Add the smallest explicit alpha access gate that protects public mutation/control endpoints before remote exposure.

## In Scope

- Add or configure Spring security support if absent.
- Protect non-read routes under public web/API control surfaces.
- Preserve read-only public page reachability as intentionally configured for alpha.
- Provide HTMX-compatible unauthorized/forbidden behavior.

## Out of Scope

- Full user management, roles, OAuth, or multi-tenant permissions.

## Implementation Steps

1. Inventory mutation endpoints from the review and current route annotations before editing.
2. Add a simple configured alpha credential or token gate using existing configuration style.
3. Enable CSRF for browser mutations, including HTMX request compatibility.
4. Adjust forms/fragments to include CSRF data through SimplyPages/HTMX patterns.
5. Add route-level tests for protected mutation rejection and accepted authenticated mutation.

## Validation

- Spring web/security tests for representative mutation endpoints.
- Browser-origin HTMX smoke for one protected form path.
- `mvn test` and bounded Spring startup.

## Exit Criteria

Unauthenticated callers cannot mutate runtime state or invoke controls, and protected HTMX flows still work with the configured alpha credential.
