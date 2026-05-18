# Workspace, Tools, and Outputs Orchestration Plan

## 1. Objective

Make shell, file, web, project workspace, and output handling honor the filesystem-backed runtime contract. Tool-capable agents should see only the active assignment workspace and linked project scopes, project leases should be usable through promised paths, and outputs should be materialized and attributed safely.

## 2. Inputs And Assumptions

- Binding inputs: bug reports 08, 09, 10, 13, 22, 23, 24 and domain workspace/tool/output review.
- Filesystem-backed runtime is authoritative; do not reintroduce Docker as a validation or containment dependency.
- Use existing workspace lease/link abstractions where possible.

## 3. Scope

In scope: shell/file/web tool confinement, redirect validation, project link materialization, allocation fail-fast behavior, output symlink hardening, attribution parsing, and runtime tests.

Out of scope: full OS sandboxing, container runtime restoration, broad project permission redesign.

## 4. Current-State Analysis

The review found host `ProcessBuilder` shell execution with wildcard defaults, file tools rooted at `dataRoot`, redirect validation only on original URI, project leases acquired without materialized paths, allocation exceptions logged while execution continues, lexical output path checks, and stale output attribution layout assumptions.

## 5. Target Design

- Tool root is derived from active `OrchestrationTaskContext`.
- Shell defaults do not allow wildcard host command execution; command effects are constrained as far as this runtime can enforce.
- `web_fetch` validates each redirect hop or disables automatic redirects.
- Project workspaces are materialized under the documented assignment workspace path or the UI/docs contract is corrected.
- Required workspace/output allocation failures fail the run clearly.
- Output materialization checks real paths and attribution understands current workspace layout.

## 6. Implementation Plan

Execute subplans in listed order. Start with project/workspace context and confinement, then output hardening. Keep changes serial because shell/file/project materialization touch overlapping runtime services.

## 7. Validation Plan

- Tool confinement tests for absolute paths, unrelated workspaces, shell wrappers, and allowed workspace-local operations.
- Redirect-to-private tests for `web_fetch`.
- Runtime test proving project-linked work can access the promised workspace path.
- Allocation failure test proves immediate failed run.
- Symlink output materialization test with `toRealPath()` confinement.
- Output attribution regression for current `agents/{agentId}/workspace/outputs` layout.
- Full `mvn test` and bounded startup.

## 8. Handoff Checklist

Update root progress, implementation notes, changelog, knowledge if a reusable confinement pattern is introduced, and commit the domain changes with validation evidence.
