# Phase 00: Orchestration Setup

## Context

Magenta is preparing a workspace/file architecture refactor. The intended architecture is captured in `.internal-dev/notes/current-architecture-focus.md`.

## Goal

Create durable planning scaffolding and shared coordination notes before running the agent review, planning, implementation, validation, remediation, and closeout workflow.

## In Scope

- Dedicated plan directory for the refactor.
- Running agent notes file.
- Initial architecture note reference.
- Initial setup commit.

## Out of Scope

- Code implementation.
- Schema changes.
- Runtime behavior changes.
- Documentation updates beyond planning setup.

## Implementation Steps

1. Create `.internal-dev/plans/workspace-file-architecture-refactor/`.
2. Create `agent-notes.md` for cross-agent coordination.
3. Commit the architecture note and plan setup.
4. Begin read-only review agents after setup commit.

## Validation

- Confirm files exist.
- Confirm git branch is `workspace-file-architecture-refactor`.
- Confirm initial setup commit is created without sweeping unrelated local changes.

## Exit Criteria

- Plan scaffolding exists.
- Shared notes exist.
- Initial setup commit exists.
- No implementation has started.
