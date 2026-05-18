# Configuration and Operations

Runtime defaults are split between file-backed AI configuration, persisted runtime settings, Spring application configuration, and SQLite data. Source anchors are [`application.yml`](../../src/main/resources/application.yml), [`ai/config/user`](../../src/main/java/io/mindspice/magenta2/ai/config/user), [`ai/orchestration/settings`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/settings), and [`schema.sql`](../../src/main/resources/schema.sql).

## Run Mode

The app is a Spring Boot service with:

- HTTP port default `8080`.
- SQLite datasource default `jdbc:sqlite:./chat-memory.db?foreign_keys=true`.
- SQL init always enabled against `classpath:schema.sql`.
- Spring AI OpenAI auto-config disabled; model clients are assembled from user AI config.
- Ollama base URL and default chat option present in Spring config, but application model routing primarily uses configured model definitions.

Startup smoke for backend wiring should use a bounded Spring Boot run, for example:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Use task-specific validation when a plan provides a stricter runtime path.

## AI Config File

`app.ai.config-path` defaults to `./config/ai-config.example.json`.

The file-backed config records:

- Model endpoint definitions.
- Endpoint type.
- Default model selection.
- Summary/planning model defaults.
- Context buffer policy.
- Web search settings.
- Legacy agent seed configuration.
- Unsafe wildcard shell override.

Source records include `AiConfig`, `ModelConfig`, `EndpointType`, `AgentConfig`, and `WebSearchConfig`.

Model endpoint definitions are read from file config. Durable agent profiles and runtime settings are stored in SQLite.

## Runtime Settings

`RuntimeSettingsService` merges persisted settings with legacy file-configured defaults.

Persisted settings include:

- Default agent id/name.
- Default model.
- Planning, summary, and compaction model.
- Context buffer percent.
- System chat model, prompt, approved tools, context limit, enabled flag.
- Assignment history auto-purge days.

API:

- `GET /api/settings/runtime`
- `PUT /api/settings/runtime`

Operational UI:

- `/settings`
- `PUT /settings`

## Feature Flags

Configured feature flags:

- `magenta.features.schedules-enabled`, default `true` in `application.yml`.
- `magenta.features.reactions-enabled`, default `true` in `application.yml`.

When disabled, schedule and event reaction routes in `AgentOrchestrationController` return `404`.

## Alpha Access

Alpha credentials:

- `MAGENTA_ALPHA_USERNAME`, default `alpha`.
- `MAGENTA_ALPHA_PASSWORD`, default `change-me-alpha`.

Unsafe HTTP methods require Basic auth and CSRF. See [Security](security.md).

## Data and Schema Operations

SQLite schema is initialized from `schema.sql` on startup. Repositories also self-bootstrap tables and add compatibility columns for existing local databases.

Operational implications:

- Treat `schema.sql` as the table inventory for fresh databases.
- Before changing a column assumption, inspect the owning repository for compatibility migrations.
- Keep foreign key behavior in mind; the SQLite URL enables `foreign_keys=true`.
- Warm data roots may have legacy workspace directories and older tables that repositories migrate forward.

## Filesystem Workspaces

Agent execution uses filesystem-backed workspaces and host shell tools. `application.yml` explicitly notes that agent execution uses filesystem-backed workspaces.

Workspace directories are confined under the configured data root by `WorkspaceDirectoryService`. Output downloads and artifact materialization also enforce data-root confinement.

## Schedules and Recurrence

Agent schedules use `agent_schedules` and `schedule_firings`. Job recurrence uses `job_recurrences`.

Cron expressions and timezones are validated by service/controller paths. Schedule firing rows de-duplicate assignment creation for a schedule/due time.

## Operational Validation

For nontrivial code changes:

- Run focused automated tests for changed packages.
- Run a bounded Spring startup smoke unless blocked by local services/secrets.
- For UI/chat/SSE/planning/concurrent interaction changes, use focused Playwright validation against a running app and follow the repo's Playwright workflow.

For documentation-only changes like this phase:

- Verify source links and referenced files exist.
- Check route/source references against current controllers.
- Run `git diff --check`.
- Avoid starting runtime services unless the task explicitly asks for live behavior validation.

## Common Operational Reads

- `GET /api/runtime/status`: app status, timestamp, default agent, model count.
- `GET /api/dashboard/summary`: dashboard summary for projects, work, agents, inbox, outputs, and system stats.
- `GET /api/models`: configured model summaries.
- `GET /api/settings/runtime`: persisted/effective runtime settings.

These routes are public `GET` routes under alpha security; avoid exposing secrets through them.
