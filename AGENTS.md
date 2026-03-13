## `.internal-dev` Development Document Store

`.internal-dev/` is the persistent engineering document store for plans, bugs, changelogs, reviews, notes, and reusable knowledge.

### Required workflow
- Plans and reviews are written to `.internal-dev/plans/` and `.internal-dev/reviews/`.
- Out-of-scope bugs found during work are logged immediately in `.internal-dev/bugs/`.
- Finalized work gets a changelog entry in `.internal-dev/changelogs/`.
- Reusable insights go to `.internal-dev/knowledge/`.
- Deferred future ideas go to `.internal-dev/notes/` after confirming they are out of scope.
- Move finalized bug/plan artifacts to sibling `.archive/` directories.

### Controlled access
- Do not read `.internal-dev` broadly by default.
- Read only the files required for the active task.

### Reference guide
- Process and templates: `.internal-dev/AGENTS.md`

## Magenta2 Project Context

Magenta2 is a local-first, configurable, scriptable multi-agent provider.

A deployment may operate as:
- an autonomous background workflow runner,
- a terminal-first coding assistant,
- a direct conversational agent,
- or a composition of these.

Primary objective:
- build a lean, reliable core that supports sessions, secure tool use, durable cognition, delegation, and wake/sleep orchestration without enterprise-style over-abstraction.

## Rewrite Collaboration Mode

Magenta2 is an evolving, pair-programming rewrite of `/home/hickelpickle/Code/Java/Magenta`.

Rewrite intent:
- deliver a simplified architecture, not parity reconstruction of legacy structure,
- preserve feature behavior where useful while reducing scaffolding and abstraction weight.

Legacy reference rules:
- the agent may inspect `/home/hickelpickle/Code/Java/Magenta` when aiding a feature or when directly asked to port behavior,
- legacy code is behavior reference only; do not copy implementations verbatim,
- keep ports as small, domain-targeted edits in Magenta2.

Simplification and maintainability rules:
- default to cohesive, co-located logic for related behavior,
- do not split every functional or logical aspect into its own class/interface,
- extract classes/interfaces only for a proven boundary, concrete test seam, or second implementation need,
- never broaden scope by importing/recreating legacy scaffolding, framework layers, or abstraction trees.

Out-of-scope dependency rule:
- if implementing a requested feature requires additional supporting code that is out of scope, stop immediately,
- report the blocking dependency and why it exceeds scope,
- recommend dropping into plan mode to decide whether to expand scope.

## Product Terms

- `Skill`: stable instruction bundle an agent can apply repeatedly.
- `Task`: specialized repeatable workflow unit.
- `Workflow`: composition rules for tasks/tools/skills toward a goal.
- `Memory`: tagged summary linked to prior session evidence.
- `Thought`: reflective note on outcomes, quality, and improvement ideas; includes why it mattered.
- `Strategy`: persistent multi-step approach spanning sessions.
- `Knowledge`: local or external reference material; external sources must be summarized.
- `Mind`: per-agent persistent filesystem workspace for cognition artifacts.

## LangChain4j Reference Policy

Primary LangChain4j references:
- Docs: `https://docs.langchain4j.dev/`
- Doc chatbot: `https://chat.langchain4j.dev/`
- Examples: `https://github.com/langchain4j/langchain4j-examples`

Knowledge capture requirements:
- When LangChain4j pages/resources are requested, capture summarized outputs in `.internal-dev/knowledge/langchain4j/`.
- Use clear, task-retrievable file names and maintain/update `.internal-dev/knowledge/langchain4j/00-index.md`.

Lookup order for LangChain4j work:
- First check local knowledge in `.internal-dev/knowledge/langchain4j/`.
- If needed information is missing or stale, consult the official references above and then write/update the local knowledge notes.

## Codex CLI Reference Policy

Primary Codex CLI reference:
- Repo: `https://github.com/openai/codex`

Usage guidance:
- Use Codex CLI as a technical reference when reviewing or implementing features.
- Treat it as a robust agent implementation reference and distill useful techniques and improvements into Magenta2 where appropriate.
- Codex CLI is more complex than Magenta2; prefer simpler approaches when they satisfy Magenta2 requirements.
- Use Codex CLI as a strong reference for high-quality prompt and tool descriptions that align with industry standards.
- Use Codex CLI as a strong reference for terminal UI approach and implementation specifics when shaping Magenta2 terminal behavior.

## Architecture Targets

Core runtime services:
- `Magenta`: root owner of runtime services and top-level runtime API.
- `RuntimeConfig`: single-record runtime config loader with nested records for model/agent docs.
- `SessionManager`: session start/resume/fork lifecycle ownership.
- `ContextManager`: per-session context creation/load/store/compaction ownership.
- `ModelRunner`: model turn orchestration, ADT mapping, and tool-loop handling.
- `OllamaClient`: blocking/streaming model transport.
- `SecurityManager`: session-scoped tool policy authorization and audit decision ownership.
- `ToolManager`: stateless tool execution dispatch with deterministic fallback.
- `MindStore` (future phases): filesystem-first cognition persistence.
- `SchedulerService` (future phases): durable wake/sleep and timeout jobs.
- `SecurityService` (future phases): broader side-effect ingress unification beyond current tool path.

Current implementation status:
- implemented runtime slice is `Magenta + RuntimeConfig + SessionManager + SessionRouter + ContextManager + ModelRunner + OllamaClient + SecurityManager + ToolManager`.
- terminal entrypoint is Lanterna-based through internal `io.mindspice.magenta.ui` package (`TerminalUiRuntime`/`TerminalUiBootstrap`) and still wired through handle-first route + callback contracts.
- tool/security behavior enters through runtime-wrapped `SessionConfig.toolBridge` callback wiring.
- runtime external API is handle-first (`SessionHandle`) with routed input/output through `SessionRouter`.
- `MindStore`, `SchedulerService`, and full cross-domain `SecurityService` remain future-phase targets.

Design rules:
- one runtime owner loop,
- one tool execution callback bridge path,
- security as composable wrapper around callbacks,
- fail-fast startup validation,
- typed outcomes over string parsing,
- minimal abstraction until a second concrete implementation exists.

## Lean Build Constraints

- Runtime core target: keep core runtime compact; prefer adding data fields/callbacks over new classes.
- Prefer flat structure and co-located contracts early.
- Use records for immutable contracts.
- Use sealed interfaces/classes for closed polymorphism.
- Define sealed type hierarchies as nested types under a single root sealed contract when practical (for example, `SessionInput` owns all input subtypes/kinds).
- Use exhaustive pattern matching switches.
- For ADTs, use sealed variants as the identity source; do not add parallel enum identity tags for those variants.
- If identity modeling is needed for a non-enum domain concept, prefer introducing/refining an ADT instead of adding an enum mirror.
- Exception: simple policy/state flags may remain enums when they do not duplicate ADT variant identity.
- Use virtual threads/structured concurrency only where they simplify logic.
- Avoid helper-class explosion and framework-heavy layering.
- Prefer cohesive classes over micro-abstractions; keep related behavior co-located unless extraction has clear justification.

## Configuration Contract

Configuration root is `configs/`.

Expected layout:

```text
configs/
  magenta.yaml
  agents/*.yaml
  models/*.yaml
  prompts/base/*.md
  prompts/personas/*.md
  prompts/agents/*.md
  tasks/*.yaml
  workflows/*.yaml
```

Rules:
- `magenta.yaml` wires include sets and runtime defaults.
- All config entity identity is derived from relative file path (filename/path without extension), not inline `id` fields.
- Agents reference reusable file-derived IDs (models/prompts/tasks/workflows).
- Agent task wiring uses one `tasks` list (no separate `task`/`taskIds` split).
- `*` in reference lists expands to all loaded entries for that domain.
- Startup resolves and validates full graph before runtime side effects.
- Merge precedence: `defaults < YAML < env vars < CLI flags`.
- No hot-reload requirement for v1.

Validation policy (strict fail-fast):
- reject unknown keys,
- reject unresolved IDs,
- reject duplicate IDs,
- reject cycles,
- reject unsupported capability combinations,
- reject placeholder capability exposure in enabled runtime paths.
- parse failures should report file + line/column + parser message.

## Persistence Contract

Session and scheduler canonical store:
- SQLite.
- Use `SimplyJDBC` for record mapping and query helpers.

Mind canonical store:
- filesystem (`dataRoot/Mind/{agentId}/...`).
- deterministic directories by Mind type.
- manifest + tag index + metadata sidecars.
- lock-guarded concurrency.
- atomic writes with temp file + atomic move.
- auditable mutation events.

Core Mind categories:
- `memory`
- `thought`
- `strategy`
- `workflow`
- `task`
- `knowledge`

Backend portability requirement:
- Mind API must support future alternate filesystem backend (Jimfs-compatible contract).

## V1 Tool Surface

Mandatory built-in tools:
- `read_file`: bounded file reads with line anchors.
- `list_directory`: bounded directory listing for non-content discovery.
- `file_metadata`: bounded file/directory stat inspection without full content read.
- `grep_files`: recursive pattern search with bounded output.
- `search_replace`: structured block replacement with conflict diagnostics.
- `write_file`: bounded deterministic write with overwrite guard.
- `delete_file`: bounded deterministic file deletion with optional snapshot guard.
- `shell_command`: policy-gated command execution with timeout.
- `sqlite_query`: bounded read-only SQL.
- `sqlite_exec`: mutating SQL with accurate statement classification.
- `Mind_*` tools (Phase 02+): create/get/search/update/delete.
- orchestration tools (Phase 03+): message/delegate/schedule.

All tool results must be structured JSON-like payloads with explicit success/failed status and machine-verifiable fields.

## Edit Harness Rules

Search/replace reliability is a first-class concern.

Required rules:
- Prefer anchored edits over blind full-file rewrite.
- Read and grep outputs should expose stable hashline anchors (`line:hh`).
- Hashline token uses normalized-line CRC32 reduced to a two-char base36 token.
- `read_file` must return a `snapshotId` (SHA-256 of normalized file content).
- `search_replace` must require `snapshotId` and fail with `snapshot_mismatch` when stale.
- `search_replace` must return structured conflicts when anchors/text do not match.
- No silent fuzzy auto-merge when ambiguity exists.
- Always emit diagnostics sufficient for a retry without guesswork.
- Mutation path must be `validate -> authorize -> execute -> normalize -> event`.

## Security and Tooling Rules

Non-negotiable rules:
- every side effect in current runtime paths routes through `SecurityManager` authorization on the tool bridge path.
- future broader side-effect ingress should converge into `SecurityService` when that phase is implemented.
- deny-by-default baseline.
- developer override (`yolo`) must be explicit and auditable.
- CLI `--yolo` forces runtime override of tool policy (including tool/file access checks) regardless of config values.
- no shell/file mutation bypass paths.
- tool outputs are structured and machine-verifiable.

Policy expectations:
- explicit decision codes (`allowed`, `denied`, `validation_error`, `override_allowed`).
- deterministic descriptor-driven path/command/url checks (no hardcoded tool-name key scanning in security core).
- `allowedPaths` represents approved path roots and must be enforced against resolved real targets.
- out-of-approved-root path requests require explicit approval callback decision.
- all denials and overrides logged as structured events.

Tool execution contract:
- `validate -> authorize -> execute -> normalize -> emit event`.
- no alternate privileged route.

## Reliability Lessons (Must Preserve)

- Never terminate autonomous loops from ambiguous empty model output.
- Never expose mock/placeholder tools as active production capability.
- Never split execution paths around security.
- Ensure edit/runtime paths catch exceptions and propagate them through `onError` callbacks to support async error handling and prevent runtime crashes.
- Never favor abstraction-first rewrites over stable behavior contracts.
- Never reshape production APIs or architecture just to satisfy stale tests; update tests to reflect intentional code design.
- Keep edit/search/replace tooling harness-verified and deterministic.
- Preserve SQL robustness lessons with parser-based fail-closed classification for safety-sensitive tool gating.

## Scope Boundaries

In scope for v1:
- local single-process runtime,
- secure tool execution pipeline,
- terminal interaction for testing/operations,
- filesystem Mind persistence,
- peer mesh messaging/delegation,
- durable SQLite wake/sleep scheduler,
- LangChain4j model integration with capability checks.

Out of scope for v1:
- distributed multi-host clustering,
- desktop/web control plane,
- vector database as foundation,
- global hot-reload architecture,
- specialized medical/clinical behavior systems,
- legacy scaffolding parity/backporting from `/home/hickelpickle/Code/Java/Magenta`,
- architecture expansion driven by old-repo structure instead of Magenta2 simplification goals.

Note:
- reflective wellness-style workflows are acceptable as generic cognition behavior,
- specialized clinical diagnosis/treatment behavior is not in v1 scope.

## Testing and Acceptance Expectations

Minimum gates:
- startup config graph validation tests,
- tool argument and policy tests,
- regression tests for known historical failures,
- scheduler restart recovery tests,
- Mind concurrency and corruption-isolation tests,
- end-to-end delegation/wake-sleep workflows.

Tool test gate (mandatory for every tool addition/change):
- each built-in tool must have functionality tests that cover success, argument validation, and deterministic failure payload shape (`status`, `code`, key `data` fields),
- each built-in tool must have policy tests that validate authorization outcomes in security (`allowed`, `denied`, `validation_error`, `override_allowed`) for relevant tool risk surfaces,
- each built-in tool must have at least one integration-path test through the runtime tool security path proving allowed execution and denied non-execution side effects,
- historical tool failures must get regression tests before merge.

Build/CI gate:
- `mvn verify` is the required test gate for merge readiness (not `mvn test` alone),
- `maven-surefire-plugin` runs `*Test` classes only,
- `maven-failsafe-plugin` runs `*IT`/`*IntegrationTest` suites.

### Home Deployment Workflow (`~/.magenta`)

Use a home deployment target for runtime smoke/ops:
- Deploy root: `~/.magenta`
- Runtime workspace root: `~/.magenta/root`
- Deployed config root: `~/.magenta/configs`
- Deployed jar path: `~/.magenta/Magenta2-1.0-SNAPSHOT.jar`

On any code/config change that should be runnable from the home deployment:
0. Default rule for agents: after implementing a feature, run `scripts/deploy-home-magenta.sh` unless the user explicitly says not to deploy.
1. Run `scripts/deploy-home-magenta.sh` from repo root.
2. Default deploy behavior updates the JAR only and does **not** overwrite `~/.magenta/configs`.
3. If you intentionally want to refresh deployed configs, run `scripts/deploy-home-magenta.sh --sync-configs` (or set `MAGENTA_DEPLOY_SYNC_CONFIGS=true`); this performs a full replace of `~/.magenta/configs`.
4. Confirm deployed config keeps `instance.workspaceRoot` set to `~/.magenta/root`.
5. For live-environment testing after deployment, run `magenta` (alias for `java -jar ~/.magenta/Magenta2-1.0-SNAPSHOT.jar ~/.magenta/configs/magenta.yaml`).
6. If the alias is missing, add `alias magenta='java -jar "$HOME/.magenta/Magenta2-1.0-SNAPSHOT.jar" "$HOME/.magenta/configs/magenta.yaml"'` to your shell profile.

### Ollama Host Diagnostics

Use the dedicated Ollama host to diagnose model/service issues separately from local Magenta2 application issues.
- SSH target: `admin2@192.168.1.112`
- Password: `admin3`
- Primary purpose: inspect Ollama service logs and host-level system state (CPU, memory, disk, networking, model availability) when triaging runtime/model failures.

### Terminal UI Smoke Test Loop

Use this loop for manual terminal validation of prompt handling, output rendering, tool interaction, and streaming behavior.

1. Build once:
   - `mvn verify`
2. Default run (current config behavior):
   - `java -jar target/Magenta2-1.0-SNAPSHOT.jar`
   - Verify the status strip is pinned at the bottom (below prompt area), not printed as chat transcript lines.
   - Run `/session` and verify terminal metadata renders.
   - Send a plain prompt and verify assistant output appears.
   - Send a tool prompt: `Use available tools to run a shell command \`pwd\` and then summarize the result briefly.`
   - Verify tool output renders as a `tool>` block and security event prints.
   - If model config does not support streaming, verify fallback notice appears: `stream-fallback> No streamed chunks were received for this response.`
3. Streaming-enabled run (temporary config copy):
   - Copy `configs/` to a temp directory and set `supportsStreaming: true` in the temp `models/default-model.yaml`.
   - Run: `java -jar target/Magenta2-1.0-SNAPSHOT.jar <temp-config-path>/magenta.yaml`
   - Send a plain response prompt (example: `Write a 3 sentence answer about testing.`).
   - Verify chunked assistant output appears without fallback notice.
4. Optional route-observability check:
   - `MAGENTA_UI_ROUTE_LOGS=true java -jar target/Magenta2-1.0-SNAPSHOT.jar`
   - Verify route logs are structured multi-line blocks with `sessionId`, active status, and separated `matchedRoutes`/`deliveredRoutes`/`failedRoutes`.
5. Exit check:
   - Run `/exit` and confirm clean shutdown.

Observability requirements:
- structured JSONL event logging,
- stable event shape with correlation IDs,
- bounded outputs and diagnosable errors,
- explicit policy/tool/orchestration audit events.

## Documentation Alignment

When architecture/process changes materially:
- update top-level `AGENTS.md`,
- update `docs/internal/00-index.md` and impacted runtime docs under `docs/internal/`,
- run and satisfy `docs/internal/90-documentation-quality-checklist.md`,
- update relevant `.internal-dev` plan/review/changelog artifacts,
- record intentional divergence when docs and implementation differ.

Source of truth policy:
- code is actual behavior,
- docs are intended behavior,
- mismatches must be explicitly documented.
