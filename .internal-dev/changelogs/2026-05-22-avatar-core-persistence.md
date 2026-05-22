# Avatar Core Persistence

## Date

2026-05-22

## Change Summary

Implemented Phase 01 of the Avatar dashboard sprint. Avatar now has a separate `avatar.sqlite` datasource under the Magenta root, an idempotent Avatar schema initializer, typed persistence records/repository/service, a snapshot read model, and a dormant reserved `Avatar` agent profile in the existing primary runtime database.

## Files

- `src/main/java/io/mindspice/magenta2/avatar/**`
- `src/test/java/io/mindspice/magenta2/avatar/**`
- `src/main/resources/avatar-schema.sql`
- `docs/technical/data-model.md`
- `docs/technical/configuration-operations.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/architecture-focus.md`

## Behavioral Impact

Fresh startup creates both the primary `magenta.sqlite` database and the Avatar-owned `avatar.sqlite` database under `magenta.root.path`. Avatar user-centric state is isolated from orchestration/runtime tables. The reserved `Avatar` agent profile is disabled, direct-line-off, and does not alter runtime default agent settings.

## Risks

Adding a second datasource required explicitly preserving the primary `dataSource` and `jdbcTemplate` beans so existing repositories continue to use `magenta.sqlite`. Later lanes must inject `AvatarService` or `avatarJdbcTemplate` explicitly when they need Avatar personal data.

## Follow-up Items

- Phase 04 should decide when and how the dormant Avatar profile becomes available to profile-scoped chat behavior.
- Later dashboard/tool lanes should use `AvatarService` instead of reaching into `AvatarRepository` directly.
