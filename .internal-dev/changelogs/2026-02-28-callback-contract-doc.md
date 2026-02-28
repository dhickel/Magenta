# Callback Contract Documentation

## Date

2026-02-28

## Change Summary

Added a dedicated internal architecture document for the session callback contract and linked it from the internal docs index.

The new doc defines:
- callback surface and defaults
- message callback dispatch ordering
- turn-path callback behavior
- `onError` emission and propagation semantics
- extension boundaries

## Files

- `docs/internal/15-callback-contract-architecture.md`
- `docs/internal/00-index.md`

## Behavioral Impact

No runtime behavior changes. Documentation now has a first-class, standalone callback contract reference.

## Risks

Low. Documentation-only update.

## Follow-up Items

- Keep this document in sync when callback fields/semantics change.
