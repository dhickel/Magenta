---
schema_version: 1
document_type: simplypages-specification
status: active
owner: web
created: 2026-05-25
---

# SimplyPages Specification

## Intended Contract

SimplyPages is the default server-rendered frontend composition library for Magenta. Use framework-native components/modules, HTMX fragments, slot keys, row/column layouts, and demo-backed editing patterns before raw HTML or bespoke JavaScript.

## SimplyPages Entries

| id | component_or_module | status | intended_contract | observed_anchors | reuse_boundary | validation | related_decisions | related_knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SP-20260525-01 | Avatar layout editing | active | Real module cards first, small top-corner decorators, centered add-module controls, and low-emphasis insert-row separators. | Avatar dashboard and SimplyPages editing demo | Layout placement and 12-column sizing happen in-place; modal/drawer flows are for deeper module-specific iteration. | Playwright visual critique. | `DECISION-20260523-03` | `simplypages-avatar-layout-and-editing.md` |
| SP-20260525-02 | HTMX default policy | active | CRUD, filtering, row actions, form submissions, and partial refreshes should use HTMX when it is the simpler path. | Dashboard, Avatar, Work Areas, orchestration surfaces | JavaScript stays narrow to streaming, local geometry, and interactions where it is clearly simpler. | Browser validation verifies HTMX viability and JS justification. | `DECISION-20260522-07` | `operational-ui-htmx-inline-editing-pattern.md` |
| SP-20260525-03 | Reusable module candidates | candidate | Entity selector support, master/detail browser shell, inline editable list helpers, status badges, HTMX tab navigation, polling panels, and transcript/event feed panels may be worth upstreaming. | Magenta frontend surfaces and prior SimplyPages PRs | Keep Magenta domain editors, lifecycle controls, security wiring, and endpoint names out of SimplyPages core. | Upstream PR tests/docs when pursued. | none | `entity-selector-htmx-pattern.md` |
| SP-20260525-04 | Work Area explorer | active | Current Magenta Work Area file explorer stays Magenta-local details/list UI rather than a reusable card/module explorer. | Avatar Work Areas browser | Generic upstream file explorer work is not required for current Magenta CI portability. | Focused browser validation. | `DECISION-20260524-03` | `workspace-file-explorer-details-list-rewrite.md` |
| SP-20260526-01 | Work Area/project browser interactions | active | File browsing and editing controls should be composed as reusable HTMX fragments around Work Area/project service contracts, not raw internal-root editors. Markdown/text editor save transport remains HTMX; narrow JavaScript may handle local mode switching, dirty status, undo/redo/revert, and unsaved markdown preview synchronization. | Work Area explorer and project browser MVP | Standard CRUD, filtering, row actions, and partial refreshes use HTMX unless a narrow browser interaction is clearly simpler in JavaScript. | Browser validation checks changed surfaces for layout quality, HTMX behavior, and JS justification. | `DECISION-20260526-01` | `workspace-file-explorer-details-list-rewrite.md` |
| SP-20260526-02 | Agent Skills browser/editor composition | active | Skill browser/editor and assignment panels should use reusable SimplyPages components/fragments (catalog rows, metadata editor, assignment panel, guided-creation steps) with HTMX-first updates. Slot-key reuse remains optional unless structures are shared across requests. | Implemented `/skills` shell and fragments in `SkillFragments`, shared operational shell/nav, shared `EntitySelectorComponents`, and Work Area details/list-inspired file table/editor. | Avoid page-specific duplicated markup for skill cards/forms across list, detail, and agent assignment contexts. Do not add UI affordances that imply script execution from browser controls. | Controller rendering tests and browser-validation evidence are reconciled in Phase 06; selector/path-script pitfalls from first-run Playwright checks are captured in knowledge for reuse. | `DECISION-20260526-06` | `agent-skills-specification-reference.md`, `agent-skills-ui-htmx-pattern.md` |

## Ownership Boundary

This file owns SimplyPages composition policy. Concrete page contracts live in `web.md`; API contracts live in `api.md`.

## Drift/Gaps

| id | status | observed_drift | routing | review_after |
| --- | --- | --- | --- | --- |
| DRIFT-20260525-07 | watching | Some older operational surfaces may still use JS-heavy or ad hoc structures. | Evaluate during surface-specific refactors. | 2026-06-24 |

## Validation Expectations

Frontend changes should inspect relevant SimplyPages docs and demos before editing. Focused Playwright validation is required for UI behavior/layout changes unless specifically blocked and reported.

## Related Decisions

`DECISION-20260522-07`, `DECISION-20260523-03`, `DECISION-20260524-03`.

## Related Knowledge

Search knowledge filenames for `simplypages`, `htmx`, `avatar`, `entity-selector`, `workspace-file`, and `operational-ui`.
