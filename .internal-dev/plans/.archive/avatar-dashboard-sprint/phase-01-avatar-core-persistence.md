# Phase 01 - Avatar Core And Persistence

## Context

Avatar needs its own user-centric data model without becoming a parallel runtime. Current Magenta data lives in `magenta.sqlite` via the primary Spring datasource and `schema.sql`; orchestration agents, runtime settings, workspaces, jobs, schedules, reactions, and output artifacts already have main-DB services. The sprint decision is that Avatar personal data uses a separate `avatar.sqlite` under the Magenta root.

Relevant anchors:

- `src/main/resources/application.yml`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootProperties.java`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileSeeder.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`

## Goal

Add the Avatar domain store and seed/reserve the backing Avatar agent profile while keeping the existing chat/tool/orchestration runtime as the only runtime path. Avatar persistence should support profile, preferences, daily tasks, todos, calendar items, notes, widget layout, facts/state, and audit-style Avatar events.

## In Scope

- New Avatar package guide and Java package.
- Separate `avatar.sqlite` datasource and `JdbcTemplate` under the Magenta root.
- Separate `avatar-schema.sql` with Avatar-owned tables.
- Avatar repository/service records for profile, preferences, dashboard layout, todos, daily tasks, calendar items, notes, facts/state, and events.
- Reserved backing `AgentProfile` row named `Avatar`, without changing default runtime settings.
- Focus update when implementation starts: Avatar is the first durable current focus.

## Out of Scope

- New model client, tool loop, runner, queue, assignment system, or chat runtime.
- Cross-database foreign keys between `avatar.sqlite` and `magenta.sqlite`.
- External calendar/mail provider sync.
- Plugin runtime tables or plugin loaders.

## Implementation Steps

1. Create `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`.
   - State that this package owns Avatar user-centric data in `avatar.sqlite`.
   - State that orchestration, assignments, workspaces, outputs, and agent profiles remain in existing packages.

2. Add Avatar datasource configuration.
   - Create `AvatarDataConfiguration`.
   - Add named beans `avatarDataSource` and `avatarJdbcTemplate`.
   - Resolve `MagentaRootProperties.path().resolve("avatar.sqlite")`.
   - Create parent directories before building the datasource.
   - Do not mark Avatar datasource or template as primary.

3. Add schema bootstrap.
   - Create `src/main/resources/avatar-schema.sql`.
   - Create `AvatarSchemaInitializer` that runs this schema against `avatarDataSource`.
   - Do not add Avatar user-centric tables to `schema.sql`.

4. Define Avatar schema.
   - `avatar_profile`: singleton profile row.
   - `avatar_preferences`: key/value or JSON-backed personal preferences.
   - `avatar_dashboard_layout`: widget order, size, enabled/collapsed state.
   - `avatar_todos`: title, notes, status, priority, due date, linked project/task/output refs.
   - `avatar_daily_tasks`: date-scoped task plan/checklist entries.
   - `avatar_calendar_items`: local calendar items only, timezone-aware timestamps.
   - `avatar_notes`: searchable app-owned notes with tags and source refs.
   - `avatar_facts`: durable facts/state indexed by namespace and key.
   - `avatar_events`: append-only Avatar domain events.

5. Add records and repository/service layer.
   - Use Java records for data carriers.
   - Keep JSON conversion in the repository with `ObjectMapper`.
   - Keep service methods small: profile, preferences, layout, todos, daily tasks, calendar, notes, facts, events, and `snapshot()`.
   - Use explicit status enums for todo/fact/task states.

6. Reserve the Avatar backing agent profile.
   - Create `AvatarAgentProfileBootstrap`.
   - Ensure one `agent_profiles` row exists with `id = "avatar"` or a stable generated id with `name = "Avatar"` if id validation makes literal id unsafe.
   - Prefer disabled/direct-line-off defaults until tool and UI lanes are ready.
   - Preserve an existing Avatar profile if present.
   - Fail loudly on id/name conflicts instead of silently renaming.
   - Do not update `runtime_settings.default_agent_id` or `default_agent_name`.

7. Update docs and closeout.
   - Technical docs: configuration/data model/services.
   - Package guide if responsibilities change.
   - Changelog and focus records.

## Validation

Focused tests:

- Avatar datasource creates `avatar.sqlite` under temp `magenta.root.path`.
- Primary `magenta.sqlite` schema remains separate from Avatar schema.
- Avatar schema initializer is idempotent.
- Repository tests cover profile singleton, preferences, layout, todo/daily/calendar/note CRUD, fact upsert uniqueness, event append ordering, JSON round trips, and status parsing.
- Service tests cover default profile creation, snapshot composition, and empty-state behavior.
- Agent profile bootstrap is idempotent, does not change runtime defaults, and fails on conflicts.

Commands:

- `mvn -Dtest=AvatarDataConfigurationTest,AvatarRepositoryTest,AvatarServiceTest,AvatarAgentProfileBootstrapTest test`
- `mvn -Dtest=MagentaRootConfigurationTest,OrchestrationRuntimeTest,RuntimeSettingsServiceTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Exit Criteria

- Avatar-owned tables exist only in `avatar.sqlite`.
- Existing primary datasource behavior and runtime settings are unchanged.
- The Avatar profile is reserved without creating runtime work.
- Later UI/tool lanes can use Avatar services without inventing persistence.
