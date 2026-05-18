# Filesystem Agent Runtime Refactor: Implementation Playbook

## Dispatch Rule

Run one implementation agent at a time, in phase order. The validator is separate. Even if individual file sets look disjoint, this refactor is contract-sequential: layout informs execution, execution informs monitoring, monitoring informs UI, UI frees deletion.

## Assignment Template

```text
You own Phase 0X of `.internal-dev/plans/filesystem-agent-runtime-refactor/`.
Read `00-orchestration-plan.md`, your phase file, `phase_handoff_notes.md`, and the closest package `AGENTS.md` before editing.
You are not alone in the codebase. Do not revert unrelated work.
Implement the phase directly, run focused tests, and append a complete handoff section to `phase_handoff_notes.md`.
Do not start work if the preceding phase handoff is incomplete or blocked.
```

## Blocking Matrix

| Phase | Depends On | May Start When |
| --- | --- | --- |
| 01 | none | suite accepted |
| 02 | 01 | canonical workspace helpers and migration decision handed off |
| 03 | 02 | Bash execution and provenance contract handed off |
| 04 | 03 | workspace status/output contract handed off |
| 05 | 04 | no active UI/API consumer needs Docker |
| 06 | 01-05 | all implementation handoffs complete and unblocked |

## Review Gates Per Phase

Each phase owner must answer:

1. What runtime contract changed?
2. What files now consume that contract?
3. What old Docker assumption remains intentionally for a later phase?
4. What focused tests prove the phase?
5. What exact assumption is the next agent allowed to treat as stable?

## Merge Discipline

- Do not let two agents edit `OrchestrationController.java` in parallel.
- Do not delete Docker classes until callers are migrated.
- Do not let the validator become the main remediation author; return failed criteria to the owning phase.
- Do not archive historical Docker reviews just to make search results cleaner.

## Failure Escalation

Stop and ask the user if implementation reveals a need for:

- host isolation stronger than data-root path confinement;
- non-Linux filesystem semantics that make project links unsafe;
- a long-running process supervisor instead of bounded commands;
- preservation of old Docker behavior despite the breaking-refactor directive.
