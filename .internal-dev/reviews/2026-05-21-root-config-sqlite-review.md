# Scope

Review agent: R1, non-mutating root/config/SQLite risk review.

Reviewed Phase 1 of `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md` against current code/config. Focus areas:

- `src/main/resources/application.yml`
- `src/test/resources/application.yml`
- `config/ai-config.example.json`
- `AiUserConfigConfiguration`
- `ExternalAiConfigLoader`
- `AiConfig`
- datasource/SQL init behavior around SQLite
- current consumers that require `AiConfig.dataRoot()`
- fresh-install root/data-root creation constraints

No production code was changed.

# Findings

## High: SQLite parent creation must happen before datasource connection and SQL init

Current `application.yml` uses `spring.sql.init.mode=always` and `spring.datasource.url=jdbc:sqlite:./chat-memory.db?foreign_keys=true`. Phase 1 intends to move the default to `jdbc:sqlite:${magenta.root.path}/magenta.sqlite?foreign_keys=true`.

Spring SQL init opens the datasource during context refresh. If `<magenta.root.path>` does not exist yet, SQLite cannot create a file inside the missing parent directory. An `ApplicationRunner`, late startup hook, or workspace service constructor is too late to guarantee fresh-install startup.

Implementation constraint: create the Magenta root / SQLite DB parent inside datasource construction or an earlier datasource-dependent initializer, using the resolved `spring.datasource.url`.

## High: `dataRoot` defaulting must run before workspace/tool beans see `AiConfig`

`AiUserConfigConfiguration` currently returns `ExternalAiConfigLoader.load(Path.of(configPath))` directly. `ExternalAiConfigLoader` preserves `config.dataRoot()` as read from file, including `null` or relative paths.

Several Spring services require a non-null existing `dataRoot` during construction:

- `WorkspaceDirectoryService` creates `aiConfig.dataRoot()` and canonicalizes it.
- `WorkspaceService` creates `aiConfig.dataRoot()` and canonicalizes it.
- `AgentFileToolService` and `AgentShellToolService` reject a non-existing `aiConfig.dataRoot()`.

Phase 1 must return an `AiConfig` whose `dataRoot` is already resolved as:

- missing -> `<magenta.root.path>/root`
- relative -> `<magenta.root.path>/<dataRoot>`
- absolute -> unchanged

Do not leave relative `dataRoot` values to be interpreted against process cwd.

## High: In-memory SQLite URLs must be ignored by root/parent creation logic

The test suite uses many `jdbc:sqlite::memory:?foreign_keys=true` datasources, and some Spring tests override `spring.datasource.url` dynamically. A naive parser that treats every `jdbc:sqlite:` suffix as a filesystem path can try to create directories for `:memory:` or for URI-style memory URLs.

Implementation constraint: only create parent directories for plain file-backed SQLite URLs. Ignore at least:

- `jdbc:sqlite::memory:`
- `jdbc:sqlite::memory:?foreign_keys=true`
- URI memory forms such as `jdbc:sqlite:file:memdb?mode=memory&cache=shared`

Strip query parameters before parent extraction for file-backed URLs.

## Medium: Placeholder handling must rely on Spring-resolved properties

The planned default URL uses a nested placeholder: `jdbc:sqlite:${magenta.root.path}/magenta.sqlite?foreign_keys=true`. This is acceptable if datasource setup reads the value after Spring property resolution, for example through `DataSourceProperties` or `@Value`.

Do not parse `application.yml` manually or build the datasource from an unresolved literal containing `${magenta.root.path}`.

## Medium: Relative AI `dataRoot` semantics differ from prompt-path semantics

Prompt files are intentionally resolved relative to the config file directory. Phase 1 changes only `dataRoot`: relative `dataRoot` must resolve under `magenta.root.path`, not beside the AI config file.

Tests need to lock this distinction down because `ExternalAiConfigLoader` currently handles only prompt path resolution.

## Medium: The example AI config is not safe as a reusable fresh-install example

`config/ai-config.example.json` currently contains a host-specific absolute `dataRoot` and a real-looking provider `apiKey`. Phase 1 should remove the absolute `dataRoot` or replace it with a relative value. When touching the example file, also avoid preserving real-looking secrets in committed sample config.

## Medium: Phase 1 must not touch old roots or legacy DB locations

The migration decision explicitly excludes auto-copy, auto-repair, archive, symlink, and cleanup behavior. Phase 1 should create only the configured Magenta root, the configured data root, and the configured SQLite parent. It must not delete, rename, archive, or scan old roots such as `./chat-memory.db` or a previous absolute `dataRoot`.

## Low: Fresh install still depends on an external AI config file

Moving DB/data defaults into the Magenta root does not by itself make packaged fresh installs work without `app.ai.config-path`. The current default path is still `./config/ai-config.example.json`, which is repo/cwd-relative. This is acceptable only if Phase 1 documents that the AI config file must still exist; otherwise it is a remaining fresh-install gap.

# Risk Assessment

Overall Phase 1 risk is moderate-to-high because it changes Spring bootstrap inputs that are consumed before normal application code runs.

Primary failure modes:

- Fresh install fails before context startup because SQLite parent does not exist.
- Tests fail because in-memory SQLite URLs are treated as filesystem paths.
- `dataRoot` is accidentally resolved against cwd or the AI config directory instead of `magenta.root.path`.
- Tool/workspace beans see an unresolved or non-existing `dataRoot`.
- Operator overrides of `spring.datasource.url` are forced into the Magenta root or otherwise broken.

# Recommendations

- Add a small root/datasource configuration class rather than a late startup runner.
- Bind or inject `magenta.root.path` as a normalized `Path`; default it to `${user.home}/.magenta`.
- Build the datasource from the resolved `spring.datasource.url` and existing `spring.datasource.driver-class-name`.
- Keep operator-provided `spring.datasource.url` authoritative; only create its parent when it is a file-backed SQLite URL.
- Resolve/default AI `dataRoot` in `AiUserConfigConfiguration` or an explicit loader overload that receives the Magenta root.
- Keep prompt path resolution exactly as-is: prompt files remain relative to the AI config file directory.
- Update the example config to avoid host-specific paths and real-looking credentials.
- Add a Spring context test that starts from a missing temp Magenta root.

# Phase 1 Implementation Constraints

- Do not use `ApplicationRunner` or `CommandLineRunner` for SQLite parent creation.
- Do not require `AiConfig.dataRoot()` to be present in the external config file.
- Do not resolve relative `dataRoot` against process cwd.
- Do not resolve relative `dataRoot` against the AI config file directory.
- Do not create directories for in-memory SQLite URLs.
- Do not rewrite, move, copy, archive, delete, or inspect old root/database locations.
- Do not change table names, schema columns, or path persistence semantics in Phase 1.
- Do not make production tests write to the developer's real `${user.home}/.magenta`; use temp roots in tests.

# Tests To Require

- `ExternalAiConfigLoaderTest` or a new root-aware config test:
  - missing `dataRoot` resolves to `<magenta.root.path>/root`
  - relative `dataRoot` resolves to `<magenta.root.path>/<relative>`
  - absolute `dataRoot` is preserved
  - prompt paths still resolve relative to the config file directory
  - relative `dataRoot` does not resolve relative to the config file directory
- New datasource/root configuration tests:
  - file-backed SQLite URL creates the DB parent directory before first connection
  - default placeholder URL resolves into the configured temp Magenta root
  - in-memory SQLite URLs are ignored
  - URI memory SQLite URLs are ignored
  - operator override to a different file-backed SQLite path is preserved and only that parent is created
- Spring context/fresh-install test:
  - starts with a missing temp `magenta.root.path`
  - AI config omits `dataRoot`
  - verifies Magenta root, data root, SQLite file, and schema initialization
- Existing focused tests:
  - `mvn -Dtest=ExternalAiConfigLoaderTest test`
  - run the new root/datasource config tests directly
  - run at least one `@SpringBootTest` with dynamic datasource/config override, such as `PublicApiRouteBindingTest` or `ScheduleReactionFeatureParitySpringTest`
- Startup smoke after Phase 1:
  - use temp `magenta.root.path` and a temp AI config path
  - confirm no writes occur under the developer's real home root

# Follow-ups

- Document that Phase 1 does not provide AI config bootstrapping, DB import, chat-file copy, old-root discovery, or repair tooling.
- Track packaged-install AI config placement separately if the project wants a true no-repo fresh install.
- Treat removal of real-looking sample credentials from `config/ai-config.example.json` as part of the Phase 1 config cleanup if that file is edited.
