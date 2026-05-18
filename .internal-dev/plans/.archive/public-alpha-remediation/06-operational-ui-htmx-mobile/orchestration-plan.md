# Operational UI, HTMX, and Mobile Orchestration Plan

## 1. Objective

Make the operational UI usable on phone-width screens, make HTMX mutation failures visible through HTTP status, and remove stale runtime references that confuse operators.

## 2. Inputs And Assumptions

SimplyPages/HTMX remain the default UI implementation style. JavaScript should not be introduced for standard CRUD/fragment behavior unless clearly simpler.

## 3. Scope

In scope: mobile shell CSS/layout fix, lifecycle HTMX targets, non-2xx fragment error statuses, stale Docker/Podman labels/classes/docs, placeholder event log, richer workspace health display.

Out of scope: broad redesign of the operational shell or marketing/landing UI.

## 4. Current-State Analysis

Playwright measured a 70px content area on phone width. Review also found lifecycle controls targeting missing Docker ids, fragment handlers swallowing errors as 200, stale Docker naming, static placeholder events, and hidden richer workspace status data.

## 5. Target Design

- Mobile operational pages render one usable content column or an overlay sidebar.
- HTMX failures return meaningful non-2xx status while still rendering useful fragments.
- Lifecycle controls target existing filesystem-runtime panels.
- Runtime wording reflects filesystem-backed runtime.
- Agent detail surfaces show real event/workspace status data or clearly omit unavailable sections.

## 6. Implementation Plan

Execute mobile layout first because it blocks browser validation. Then fix lifecycle targets, error statuses, stale labels, and detail quality.

## 7. Validation Plan

- Playwright mobile viewport check at `390x780` for `/agents/{agentId}`.
- HTMX lifecycle swap test.
- Error fragment test verifies non-2xx status.
- Static scan for stale Docker/Podman UI strings/classes after cleanup.
- Focused tests, full `mvn test`, bounded startup.

## 8. Handoff Checklist

Validation must be browser-origin for mobile/HTMX changes and should be performed by a validation agent.
