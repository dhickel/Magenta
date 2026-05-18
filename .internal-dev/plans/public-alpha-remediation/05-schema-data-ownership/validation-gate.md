# Schema Validation Gate

## Validator Instructions

Read schema review files and bug reports 07, 19, and 25 before validating.

## Required Checks

- Warm DB startup preserves workspace leases.
- Clean schema includes current columns without relying on repository patch order.
- Inbox ownership is documented or unified.
- Orphan schema is removed or justified.
- Clean/warm startup, DB probes, focused tests, full `mvn test`, and bounded startup pass.
