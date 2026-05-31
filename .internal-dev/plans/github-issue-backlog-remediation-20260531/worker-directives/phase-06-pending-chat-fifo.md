# Phase 06 Worker Directive: Pending Chat FIFO Ordering (#19)

## Objective

Remediate GitHub issue #19 so concurrent same-conversation pending chat enqueue cannot create nondeterministic duplicate FIFO order keys.

## User-Visible Outcome

Browser/API mid-turn queued chat messages drain in deterministic FIFO order even when concurrent submissions occur.

## Issues

- #19 `Persistence: Pending chat FIFO ordering can race under concurrent enqueue`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatPendingMessageService.java` only if service serialization is chosen
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatPendingMessageServiceTest.java` if service changes
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/web.md` only if browser behavior wording changes
- `.internal-dev/changelogs/2026-05-31-pending-chat-fifo.md`

## Forbidden Scope

- Do not replace the pending-message queue architecture.
- Do not move pending messages into chat memory.
- Do not change client behavior unless backend fix requires it.

## Supporting Docs To Read

- `.internal-dev/specifications/schema.md` entry for `ai_chat_pending_messages`
- `.internal-dev/specifications/services.md` entry `SVC-20260525-12`
- `.internal-dev/specifications/web.md` entry `WEB-20260525-07`
- `.internal-dev/knowledge/chat-planning-composer-architecture.md` if browser queue behavior is touched

## Reproduction Probe Required Before Fix

Add a concurrent same-conversation enqueue test that attempts to expose duplicate `message_order` under parallel calls. If deterministic reproduction is hard with in-memory SQLite, encode the invariant by using multiple repository instances/connections or by adding schema-level uniqueness and testing duplicate insertion/retry behavior.

## Implementation Steps

1. Add reproduction/invariant tests for concurrent enqueue and claim order.
2. Choose the smallest robust fix:
   - per-conversation JVM lock around max+insert plus unique index, or
   - insert retry on `(conversation_id, message_order)` unique violation, or
   - SQLite transaction mode that serializes the read-max/write cycle.
3. Add or migrate a uniqueness guarantee for `(conversation_id, message_order)` if practical; handle existing duplicates if migration can encounter them.
4. Ensure `claimOldest` has deterministic tie-breaking such as `order by message_order asc, created_at asc, id asc`.
5. Update schema/services specs and changelog.

## Senior-Engineer Guidance

- Lock-only fixes may not cover multiple app instances, but this deployment is SQLite/local; still prefer a DB uniqueness invariant when feasible.
- Use deterministic ordering in queries even after uniqueness to keep old rows predictable.
- Do not rely on `rows.indexOf(row)` for large lists if a simple counter is clearer, but avoid unrelated refactors.

## Acceptance Criteria

- Concurrent enqueue cannot persist duplicate order keys for the same conversation.
- Claim/list order is deterministic.
- Existing pending queue claim/ack/release/stale recovery behavior still passes.

## Negative Checks

- No lost messages under retry/lock.
- No broad chat client rewrite.
- No cross-conversation serialization bottleneck unless unavoidable.

## Validation Commands

- `mvn -q -Dtest=ChatPendingMessageRepositoryTest,ChatPendingMessageServiceTest test`
- Browser queue Playwright only if worker changes client-visible behavior or validator requests focused proof.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-06-validation-report.md`

## Closeout Expectations

Main thread closes #19 after validation, commit, push, and email.

## Stop Conditions

- Stop if existing production data migration for duplicates requires a user data policy decision.

## Do Not Close Unless

- Tests prove uniqueness/deterministic FIFO under the targeted concurrent condition.
