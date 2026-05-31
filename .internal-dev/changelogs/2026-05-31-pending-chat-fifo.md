## Date

2026-05-31

## Change Summary

Remediated GitHub issue #19 by making pending chat same-conversation FIFO order keys unique and deterministic under concurrent enqueue.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepository.java`: added per-conversation enqueue serialization, autocommit unique-conflict retry, deterministic order tie-breaks, and legacy duplicate order normalization before unique index creation.
- `src/main/resources/schema.sql`: added schema-init legacy duplicate order normalization before the `(conversation_id, message_order)` unique index and aligned the status/order index with deterministic tie-break fields.
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepositoryTest.java`: added concurrent enqueue, uniqueness, repository legacy duplicate normalization, and real `schema.sql` warm-start duplicate normalization coverage.
- `.internal-dev/specifications/schema.md`: documented the pending-message order uniqueness invariant.
- `.internal-dev/specifications/services.md`: documented deterministic FIFO behavior under concurrent same-conversation enqueue.

## Behavioral Impact

Normal `/chat` mid-turn queued messages keep FIFO behavior, but concurrent same-conversation submissions can no longer persist duplicate `message_order` values. Legacy duplicate pending rows are re-numbered deterministically by `message_order`, `created_at`, and `id` during `schema.sql` startup initialization before the unique index is created; repository initialization keeps the same compatibility guard.

## Specification Impact

Updated schema and service specifications to include the unique pending-message order invariant, legacy duplicate warm-start normalization, and concurrent enqueue validation expectation. `web.md` was not changed because no browser/client behavior changed.

## Risks

The repository now keeps a per-conversation JVM lock map for enqueue serialization. The database unique index remains the cross-instance invariant, with retry handling for unique conflicts.

## Follow-up Items

None.
