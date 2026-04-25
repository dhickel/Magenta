## Chat Repository Package

This package owns chat persistence details.

### Responsibilities
- Persist and retrieve chat memory and session metadata.
- Keep SQL, schema assumptions, and storage-specific behavior localized here.
- Preserve conversation identifiers and model metadata consistently.

### Change guidance
- Do not leak SQLite-specific details into services or controllers.
- Keep repository APIs narrow and named around use cases.
- Coordinate schema changes with `src/main/resources/schema.sql`.
- Keep this guide updated when persistence ownership, schema expectations, or repository contracts change.

### Validation
- Add repository or service tests for persistence behavior changes.
- Verify schema initialization still works for a clean local database.
