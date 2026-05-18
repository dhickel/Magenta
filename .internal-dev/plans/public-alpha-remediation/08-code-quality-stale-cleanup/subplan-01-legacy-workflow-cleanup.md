# Subplan 01: Legacy Workflow Cleanup

## Goal

Resolve deprecated `ai.chat.workflow` code that still compiles while canonical workflow code lives under orchestration.

## Implementation Steps

1. Search imports, tests, and runtime references for `ai.chat.workflow`.
2. If unused, remove package and tests tied only to legacy behavior.
3. If retained, document why and prevent future agents from using it accidentally.
4. Run compile/tests.

## Validation

Active workflow routes still use canonical orchestration workflow package.
