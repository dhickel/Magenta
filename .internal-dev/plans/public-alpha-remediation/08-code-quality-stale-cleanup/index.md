# Code Quality and Stale Cleanup Domain

## Objective

Clean up review-identified stale code, deprecated surfaces, dead static modules, and misleading comments/docs after functional domains have stabilized.

## Branch

Implementation branch: `public-alpha-remediation/code-quality-stale-cleanup`.

## Owned Findings

- ro-06 deprecated `ai.chat.workflow` still compiles.
- ro-07 stale `magenta-tools.js` direct-run/workflow references.
- ro-08 stale/dead `/inbox` and `/outputs` JS modules.
- ro-18 stale Docker comments/docs.
- Cleanup follow-through for bug-18, bug-19, and bug-22 where active functional fixes leave stale residue.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-legacy-workflow-cleanup.md` | ro-06 |
| 2 | `subplan-02-static-module-cleanup.md` | ro-07, ro-08 |
| 3 | `subplan-03-stale-doc-comment-cleanup.md` | ro-18 |
| 4 | `subplan-04-final-review-residue-sweep.md` | cleanup follow-through |

## Context

Run this domain after the functional domains unless the team explicitly pulls one cleanup forward. Validators must read the listed review files and verify cleanup does not remove active behavior.
