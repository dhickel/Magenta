# Code Quality and Stale Cleanup Orchestration Plan

## 1. Objective

Remove stale/deprecated code and misleading runtime residue identified by review without destabilizing active public-alpha behavior.

## 2. Inputs And Assumptions

Functional blocker fixes may make some cleanup targets obsolete or newly reachable. Re-verify current imports/routes before deleting code.

## 3. Scope

In scope: legacy workflow package cleanup, stale static module removal or quarantine, stale Docker comments/docs cleanup, final review residue sweep.

Out of scope: speculative refactors not mentioned by the review.

## 4. Current-State Analysis

Review found deprecated workflow code still compiling, static JS modules with stale direct-run routes but no active import, `/inbox` and `/outputs` JS modules that appear dead beside HTMX fragments, stale Docker comments/docs, and orphan schema/doc residue.

## 5. Target Design

- Active codebase no longer compiles unused legacy workflow implementation unless explicitly retained with documentation.
- Static resources loaded by no current page are removed or clearly quarantined.
- Runtime comments/docs match filesystem-backed contract.
- No cleanup removes active routes or user workflows.

## 6. Implementation Plan

Perform static import/reference checks before each deletion. Prefer deleting unused code over leaving misleading inert code, but add compatibility notes if external routes/assets might still be referenced.

## 7. Validation Plan

- `rg` proof for no active imports before deletion.
- Maven compile/tests.
- Public page browser sweep for static asset 404 regressions.
- Stale string scan for Docker/direct-run residue in active files.

## 8. Handoff Checklist

Record any intentionally retained stale-looking code in knowledge or comments with owner/reason.
