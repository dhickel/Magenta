# Security and Access Control Orchestration Plan

## 1. Objective

Make the public-alpha portal defensible before remote exposure. Mutating/control routes must require the alpha access gate, ids used in paths must be valid plain segments, workflow graph rendering must not execute persisted text, and agent-scoped lifecycle routes must reject cross-agent mutations.

## 2. Inputs And Assumptions

- Binding inputs: bug reports 01, 02, 11, 12 and review files listed in `index.md`.
- Assume the alpha gate can be small and explicit; do not build a broad user-management system unless existing code already provides one.
- Assume browser UI remains HTMX-first and existing user workflows must be preserved after auth/CSRF is added.

## 3. Scope

In scope: alpha auth/CSRF for public mutations, id segment validation, XSS-safe workflow graph rendering, route-agent ownership checks, focused tests, startup smoke, and `.internal-dev` closeout.

Out of scope: multi-user RBAC, OAuth, broad permission modeling, workflow builder redesign beyond the XSS fix, and tool command confinement handled by domain 02.

## 4. Current-State Analysis

The review found no Spring Security/auth dependency, public mutation endpoints in web/API controllers, raw caller-supplied agent ids flowing into workspace paths, workflow node values rendered with raw `innerHTML`, and lifecycle routes loading assignments by assignment id without checking route agent ownership.

## 5. Target Design

- Public mutation/control routes reject unauthenticated or CSRF-missing requests with non-2xx status while preserving HTMX-friendly error rendering.
- Ids used as filesystem path segments are validated centrally as strict segment values: no blank, slash, backslash, dot-dot, absolute path syntax, encoded traversal, or platform separator variants.
- Workflow persisted text is rendered through escaping or text-node construction, never raw interpolation into `innerHTML`.
- Agent-scoped lifecycle route handlers verify assignment ownership before cancel/pause/resume/force-interrupt.

## 6. Implementation Plan

1. Implement `subplan-01-auth-csrf-gate.md`, keeping the gate narrow and testable.
2. Implement `subplan-02-id-segment-validation.md`, then replace ad hoc nonblank checks for path ids.
3. Implement `subplan-03-workflow-xss-security.md` in coordination with workflow domain if it has already started.
4. Implement `subplan-04-agent-scoped-lifecycle.md`, adding service-level methods where possible so controllers stay thin.
5. Update `progress.md`, append cross-domain facts to `implementation_notes.md`, and complete `.internal-dev` changelog/knowledge/bug status updates before commit.

## 7. Validation Plan

- Focused Spring web/security tests for unauthenticated and missing-CSRF mutation rejection.
- Unit tests for segment validator with `..`, slash, backslash, absolute path, percent-encoded traversal, and valid ids.
- XSS regression using workflow/node labels with script-like payloads; browser validation if rendering behavior changes.
- Lifecycle route tests proving cross-agent assignment ids are rejected.
- Run focused Maven tests, full `mvn test`, and bounded Spring startup.

## 8. Handoff Checklist

- Branch created and root progress updated.
- All owned bug reports addressed or blocked with evidence.
- Validation agent read original review docs and bug reports before validating.
- Changelog, reusable knowledge, and any newly discovered out-of-scope bugs recorded.
- Commit includes code and `.internal-dev` updates.
