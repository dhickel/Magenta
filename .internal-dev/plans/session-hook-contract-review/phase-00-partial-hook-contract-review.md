# Phase 00: Partial Hook Contract Review (Deferred)

## Context

Current callback/hook surface in `SessionConfig` has grown and now mixes:
- pre-input interception,
- post-append observation,
- token stream output,
- per-message-type observer hooks,
- error callbacks,
- tool bridge execution.

Recent design discussion identified contract ambiguity around hook timing and semantic boundaries (especially what affects model context vs what is observational only). The runtime currently appends system/context/summary messages through multiple paths, and callback semantics are not yet unified under a single append contract.

## Goal

Capture the current state and define the next planning work needed to simplify and harden the hook contract before further implementation.

## In Scope

- Document current concerns and design direction.
- Define proposed contract simplification targets for follow-up planning.
- Identify concrete review questions and decision points.
- Explicitly defer implementation until the design is reviewed.

## Out of Scope

- Any runtime code changes.
- Any callback/hook API removals or additions.
- Any Session/ModelRunner/ContextManager mutation-path refactors.
- Any SecurityService integration changes.

## Implementation Steps

1. Inventory current hook call sites and timing points:
   - pre-input handling,
   - post-append handling,
   - token streaming,
   - error path,
   - tool response path.
2. Produce a timing matrix for all message origins:
   - user input,
   - inbound message/event input,
   - assistant response,
   - tool message,
   - system prompt message,
   - summary message.
3. Evaluate minimal hook surface candidate:
   - `onInputHook(SessionInput)`
   - `onMessageAppendedHook(SessionMessage)`
   - `onErrorHook(Throwable)`
   - keep `toolBridge` as execution seam
   - consider dropping `onTokenStreamHook` and per-message-type hooks.
4. Define observability hooks explicitly required by product intent:
   - tool responses,
   - model responses,
   - security denials/blocks.
5. Propose a single append pipeline rule to ensure deterministic hook emission for all appended messages.
6. Create a full implementation plan only after design decisions are locked.

## Validation

- Review output includes a complete timing matrix for all message append sources.
- Review output includes a reduced hook surface proposal with tradeoffs.
- Review output identifies any backwards-compatibility impacts.
- Review output provides a decision-complete implementation plan candidate for next phase.

## Exit Criteria

- This artifact is accepted as a deferred/partial plan.
- Team agrees no implementation should proceed until dedicated review/planning is completed.
- A follow-up full plan phase is scheduled to finalize API changes and migration steps.
