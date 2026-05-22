# Phase 01 Core Persistence Worker Handoff

## Scope

Implemented within owned Avatar paths only:

- `src/main/java/io/mindspice/magenta2/avatar/**`
- `src/test/java/io/mindspice/magenta2/avatar/**`
- `src/main/resources/avatar-schema.sql`
- `.codex-orchestration/avatar-dashboard-sprint/lanes/phase-01-core-persistence-worker.md`

No commits were created.

## Completed

- Added Avatar package guide defining the `avatar.sqlite` ownership boundary and runtime-package boundaries.
- Added Avatar datasource wiring:
  - named `avatarDataSource`
  - named `avatarJdbcTemplate`
  - explicit primary `dataSource` and `jdbcTemplate` preservation so Spring Boot does not bind existing code to the Avatar datasource/template after a second datasource is introduced.
- Added `avatar-schema.sql` and `AvatarSchemaInitializer`.
- Added Avatar repository/service records and enums for:
  - profile
  - preferences
  - dashboard layout
  - todos
  - daily tasks
  - calendar items
  - notes
  - facts
  - events
  - snapshot composition
- Added `AvatarAgentProfileBootstrap` to reserve `agent_profiles.id = 'avatar'` / `name = 'Avatar'` after application runners complete.
  - Existing Avatar profile is preserved.
  - Runtime defaults are not changed.
  - ID/name conflicts fail loudly.
  - Reserved default is disabled, direct line off, empty tool/shell allowlists.

## Validation

- `mvn -Dtest=AvatarDataConfigurationTest,AvatarRepositoryTest,AvatarServiceTest,AvatarAgentProfileBootstrapTest test`
  - Result: pass, 12 tests.
- `mvn -DskipTests compile`
  - Result: pass.
- `mvn -Dtest=MagentaRootConfigurationTest,RuntimeSettingsServiceTest test`
  - Result: pass, 9 tests.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --magenta.root.path=/tmp/tmp.VR1M2W0wmM --magenta.features.schedules-enabled=false --magenta.features.reactions-enabled=false --magenta.plan.execution-stream-timeout-seconds=0"`
  - Result: app started successfully on random port and then exited with code 124 due to the expected timeout.
  - Evidence: startup log reached `Started Magenta2Application`; temp root contained both `magenta.sqlite` and `avatar.sqlite`.

## Blockers And Requested Integration Edits

- `.internal-dev/focus/current-focus.md` still shows Avatar as `watching`; phase 01 implementation has started, so coordinator should promote/update the focus during serial closeout.
- Required changelog/docs updates were not made because this worker was not mapped to `.internal-dev/changelogs/**` or `docs/**`.
- Suggested coordinator closeout docs:
  - Avatar datasource/configuration note.
  - Avatar data model/service note.
  - Reserved Avatar agent profile behavior note.
- Existing unrelated worktree changes are present under orchestration workspace/output files and other lane notes. This worker did not modify them.

## Notes For Later Phases

- Inject `AvatarService` for UI/tool behavior instead of reaching into `AvatarRepository`.
- Use `AvatarRepository.PROFILE_ID` for the singleton Avatar profile row.
- Avatar agent profile is intentionally dormant until later tool/chat behavior lanes enable user-visible behavior.
