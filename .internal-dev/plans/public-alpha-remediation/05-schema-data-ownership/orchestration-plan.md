# Schema and Data Ownership Orchestration Plan

## 1. Objective

Make `schema.sql` represent the current persistence shape, remove destructive lease migration behavior, and clarify table ownership so clean and warm DB startup are predictable.

## 2. Inputs And Assumptions

`schema.sql` should be canonical for clean startup. Repository guarded migrations may remain for warm DB compatibility but must not hide drift or drop live lease data.

## 3. Scope

In scope: workspace lease preservation, canonical columns/tables, inbox ownership decision, orphan schema cleanup, clean/warm startup tests and DB probes.

Out of scope: switching migration frameworks unless explicitly approved.

## 4. Current-State Analysis

Review found stale `workspace_roots` creation causing destructive migration, missing columns patched by repositories, duplicate inbox tables, and likely orphan `job_work_items`.

## 5. Target Design

- Clean DB starts with current tables/columns.
- Warm DB migration preserves workspace leases.
- Inbox/user/runtime message tables have documented ownership or are unified with migration.
- Orphan schema is removed or documented as intentionally retained.
- Schema drift test compares repository bootstrap expectations against `schema.sql`.

## 6. Implementation Plan

Fix lease-destructive migration before broad schema drift so warm DB safety is preserved. Then update schema, inbox ownership, and orphan cleanup.

## 7. Validation Plan

- Clean isolated SQLite startup.
- Warm fixture startup with existing leases.
- DB probes for lease preservation, output attribution columns, inbox tables, and removed/retained orphan tables.
- Full `mvn test` and bounded startup.

## 8. Handoff Checklist

Record schema decisions in knowledge if reusable. Update progress and commit with validation evidence.
