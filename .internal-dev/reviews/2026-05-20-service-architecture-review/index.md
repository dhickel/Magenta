# Service Architecture Review Index

## Scope

This directory contains the complete orchestrated service architecture review for Magenta from May 20, 2026. The suite covers chat, tools, context management, tool history, compaction, threading, audit, persistence, runtime assignments, jobs, workflows, workspaces, outputs, configuration, API/frontend surfaces, and cross-domain contracts.

## Findings

Read the files in this order:

1. [Overview](overview.md) - executive review, highest-severity findings, risk assessment, and primary recommendations.
2. [System Map](system-map.md) - service/domain map plus Mermaid diagrams for chat, execution, workspace/output, and persistence ownership flows.
3. [Risk Register](risk-register.md) - deduplicated severity-ranked risks and remediation priorities.
4. [Contracts](contracts.md) - contracts to preserve, broken or ambiguous contracts, and target contracts for remediation planning.
5. [Schema Spec Review](schema-spec-review.md) - database ownership, cleanup, schema drift, and API/spec mismatch details.
6. [Orchestration Notes](orchestration-notes.md) - coordination notes from the planning, domain review, synthesis, and editorial passes.

## Risk Assessment

The suite identifies integration contract drift as the main architectural risk. The highest priority issues are duplicate workflow execution, split execution lifecycle ownership, incomplete chat audit/memory parity, unenforced runtime tool settings, inconsistent workspace/output attribution, plan/task lifecycle gaps, HTMX field loss, and persistence cleanup drift.

## Recommendations

Use [Risk Register](risk-register.md) and [Contracts](contracts.md) as the starting point for a remediation plan. The first implementation phase should address workflow duplicate execution and assignment-owned lifecycle semantics before expanding workflow-backed jobs or subagent orchestration.

## Follow-ups

- Create a phased remediation plan under `.internal-dev/plans/` if the team chooses to act on the review.
- Update permanent docs after remediation decisions are accepted.
- Keep this directory as the canonical review bundle for the May 20, 2026 architecture review.
