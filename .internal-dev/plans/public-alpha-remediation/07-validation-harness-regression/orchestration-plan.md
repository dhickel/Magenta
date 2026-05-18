# Validation Harness and Regression Orchestration Plan

## 1. Objective

Add durable automated coverage around public routes, SSE contracts, browser flows, and DB fixture behavior so alpha-blocking regressions cannot hide behind direct controller tests.

## 2. Inputs And Assumptions

Existing Maven tests are green but insufficient. Browser validation should run against the live app. Playwright execution should be delegated to validation agents.

## 3. Scope

In scope: Spring web/application-context smoke tests, controller route coverage, reusable Playwright config/specs, SQLite foreign key fixture parity, schedule/reaction test config parity, regression tests for previously missed blockers.

Out of scope: exhaustive end-to-end production campaign unless explicitly approved.

## 4. Current-State Analysis

Review found no Spring web/context test layer, many public controllers without route tests, Playwright feasible but not checked in, test config disabling schedules/reactions, and SQLite fixtures often omitting `foreign_keys=true`.

## 5. Target Design

- Public REST/SSE route groups have focused route binding/status/DTO/SSE tests.
- A small Playwright harness can validate public page matrix and changed HTMX workflows.
- Test fixtures use SQLite foreign keys where relevant.
- Tests exercise schedule/reaction behavior close enough to production to catch save/runtime mismatch.

## 6. Implementation Plan

Build route/context coverage first, then Playwright harness, fixture parity, and targeted regression tests for review blockers.

## 7. Validation Plan

Run new route tests, Playwright focused specs, full `mvn test`, clean/warm startup, and document how to invoke browser validation.

## 8. Handoff Checklist

Update knowledge with reusable validation workflow improvements if the Playwright setup changes.
