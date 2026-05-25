# Root Migration Future Tooling

Deferred ideas confirmed out of scope for the 2026-05-21 root-relative workspace migration:

- One-time migration CLI with dry-run/apply modes.
- Admin import/API path for moving a populated root and database.
- Startup diagnostics or opt-in repair reporting for stale absolute path rows.
- Controlled rewrite of old owned absolute path columns.
- Optional discovery/reporting for unstructured JSON path references.
- Operator-guided chat-file copy verification.

The implemented behavior is intentionally smaller: new writes are root-relative, chat carry-forward is documented as a manual copy, and old workspace/output/runtime files are not auto-copied or deleted.
