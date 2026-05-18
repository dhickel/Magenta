# Subplan 03: Workflow XSS Security

## Context

bug-11 reports persisted workflow/node values interpolated into raw `innerHTML`. This is security-owned, with implementation overlap in workflow UI.

## Goal

Prevent persisted workflow graph data from executing script or markup when rendered.

## In Scope

- Replace unsafe graph rendering interpolation with escaped values or DOM text nodes.
- Preserve graph editing behavior.
- Add regression coverage for node label/key/type/editable fields.

## Out of Scope

- Full workflow builder redesign, which belongs to domain 04.

## Implementation Steps

1. Identify all workflow graph render and side-panel `innerHTML` sites.
2. Replace persisted text interpolation with DOM APIs or a shared escape helper.
3. Keep any static markup construction limited to trusted literal templates.
4. Add tests or browser validation with script-like persisted node values.
5. Coordinate any overlapping file edits with domain 04.

## Validation

- XSS payload displays as text and does not execute.
- Existing workflow graph cards and side panel still render expected fields.
- Focused Playwright/browser check if graph UI is changed.

## Exit Criteria

Persisted workflow text is never executed by the graph composer.
