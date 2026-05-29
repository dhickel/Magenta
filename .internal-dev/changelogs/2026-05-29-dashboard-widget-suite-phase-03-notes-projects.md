---
schema_version: 1
document_type: changelog
status: complete
date: 2026-05-29
---

# Date

2026-05-29

# Change Summary

Implemented Phase 03 dashboard widgets for Notes, Projects, and Contacts/Materials. Scoped repair added normalized project note path checks, project artifact symlink hardening, and representative Avatar tool behavior tests.

# Files

- `src/main/java/io/mindspice/magenta2/avatar/dashboard/`: added note/project read models and `ProjectArtifactService`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`: added instance-scoped personal note, file-note, and project artifact fragment routes.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`: rendered source-aware Notes, Projects, Contacts/Materials summaries and note detail modals.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/`: registered controlled file-note and project artifact Avatar tools.
- Focused tests under `src/test/java/...`: covered source settings, file-note rendering/editing, project artifact defaults/validation, normalized traversal rejection, symlink rejection, and representative tool behavior.
- Specs/docs/evidence updated for Phase 03 behavior.

# Behavioral Impact

Notes widgets now support personal, agent, project, Work Area, and mixed source settings. Personal notes remain in `avatar_notes`; file notes remain file-backed and are read/edited through confined service paths. Project file-note paths are normalized before policy checks and must remain under `.magenta/project/`. Project widgets summarize fixed typed artifacts under `.magenta/project/*.json`, reject symlinked artifact parents/files, and distinguish household projects from code projects.

# Specification Impact

Updated `architecture.md`, `services.md`, `api.md`, `web.md`, `simplypages.md`, and `decisions.md` with the Phase 03 notes/project context contract.

# Risks

Browser proof is still pending by directive. Project artifact UI currently summarizes fixed JSON artifacts and exposes service/tool updates; richer per-artifact editing UX can be expanded after browser validation. Symlink rejection tests are skipped only on filesystems that do not support symlinks.

# Follow-up Items

- Run delegated Playwright proof for Notes personal mode, Work Area file-note mode, Markdown viewer/editor, Projects widget, Contacts/Materials widget, and mobile modal scrolling.
