# Workflow Authoring, Runtime, and JS Orchestration Plan

## 1. Objective

Enable users to build normal approval/task workflows incrementally, prevent empty no-op workflow execution, and narrow the workflow graph JS island so CRUD and validation remain server/HTMX-owned.

## 2. Inputs And Assumptions

The canonical workflow package is `io.mindspice.magenta2.ai.orchestration.workflow`. Incomplete draft graph states are valid for persistence but invalid for validate/submit/run.

## 3. Scope

In scope: draft save versus execution validation split, condition editing, nonempty executable graph validation, JS island scope and error handling, tests and browser validation.

Out of scope: legacy workflow package removal, owned by domain 08; public direct-run route contract, owned by domain 03; XSS security hardening, owned by domain 01 but coordinated here.

## 4. Current-State Analysis

The builder validates on node add/update, route forms lack condition controls, empty workflows can validate/complete as no-ops, and `workflows.js` overrides HTMX editor behavior with fetch-based CRUD and weak network failure reporting.

## 5. Target Design

- Draft persistence accepts incomplete intermediate graphs.
- Validate/submit/run require at least one executable node and valid start path.
- Approval/control route conditions are editable.
- JavaScript handles graph layout/dragging and local interaction only where simpler than raw HTMX; persistence and validation remain server-owned.
- Network failures surface as visible non-success states.

## 6. Implementation Plan

Execute draft/editing split first, then executable validation, then JS island cleanup and error handling. Avoid large UI rewrites; reuse SimplyPages/HTMX patterns.

## 7. Validation Plan

- Browser test builds approval workflow incrementally.
- Empty workflow cannot validate/submit/run.
- Route condition editing persists.
- Graph network failure is visible and does not silently mutate local state.
- Focused workflow tests, full `mvn test`, and bounded startup.

## 8. Handoff Checklist

Record HTMX-vs-JS justification in implementation notes if any JS remains beyond graph layout/dragging.
