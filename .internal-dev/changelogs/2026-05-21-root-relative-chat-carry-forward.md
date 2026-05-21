# Date

2026-05-21

# Change Summary

Documented Phase 6 of the root-relative workspace migration and added focused tests for chat file carry-forward behavior. The docs now explain the new root-owned defaults, the manual operator copy process for existing chat state, persisted path semantics, and future migration tooling that remains out of scope.

# Files

- `docs/technical/configuration-operations.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/data-model.md`
- `docs/end-user/chat.md`
- `docs/end-user/quickstart.md`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatFileServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/ChatFileControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
- `.codex-orchestration/root-relative-workspace-migration/notes.md`

# Behavioral Impact

No runtime auto-copy, import, delete, repair, or path rewrite behavior was added. Operators must stop Magenta, back up old state, copy the old database to `<magenta.root.path>/magenta.sqlite`, and copy old `chats/` to `<magenta.root.path>/root/chats/` to carry ordinary chat files forward.

# Risks

Documentation must stay aligned with the root-relative path behavior from Phases 1-5. Stale absolute rows from old roots remain a deliberate operation-time failure mode until a future migration tool exists.

# Follow-up Items

- One-time migration CLI.
- Admin import/API.
- Startup diagnostics and repair.
- Controlled rewrite of old absolute database rows.
