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

## Architecture Targets

Core runtime services:
- `Magenta`: root owner of runtime services and top-level runtime API.
- `RuntimeConfig`: single-record runtime config loader with nested records for model/agent docs.
- `SessionManager`: session start/resume/fork lifecycle ownership.
- `ContextManager`: per-session context creation/load/store/compaction ownership.
- `ModelRunner`: model turn orchestration, ADT mapping, and tool-loop handling.
- `OllamaClient`: blocking/streaming model transport.
- `MindStore` (future phases): filesystem-first cognition persistence.
- `SchedulerService` (future phases): durable wake/sleep and timeout jobs.
- `SecurityService` (future phases): single authorization ingress for side effects.

Current implementation status:
- implemented runtime slice is `Magenta + RuntimeConfig + SessionManager + SessionRouter + ContextManager + ModelRunner + OllamaClient`.
- tool/security behavior currently enters through `SessionConfig.toolBridge` callback wiring.
- runtime external API is handle-first (`SessionHandle`) with routed input/output through `SessionRouter`.
- `MindStore`, `SchedulerService`, and `SecurityService` remain future-phase targets.

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
- Agents reference reusable IDs (models/prompts/tasks/workflows).
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
- `grep_files`: recursive pattern search with bounded output.
- `search_replace`: structured block replacement with conflict diagnostics.
- `write_file`: bounded deterministic write with overwrite guard.
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
- every side effect routes through `SecurityService`.
- deny-by-default baseline.
- developer override (`yolo`) must be explicit and auditable.
- no shell/file mutation bypass paths.
- tool outputs are structured and machine-verifiable.

Policy expectations:
- explicit decision codes (`allowed`, `denied`, `validation_error`, `override_allowed`).
- deterministic path/command checks.
- all denials and overrides logged as structured events.

Tool execution contract:
- `validate -> authorize -> execute -> normalize -> emit event`.
- no alternate privileged route.

## Reliability Lessons (Must Preserve)

- Never terminate autonomous loops from ambiguous empty model output.
- Never expose mock/placeholder tools as active production capability.
- Never split execution paths around security.
- Never favor abstraction-first rewrites over stable behavior contracts.
- Never reshape production APIs or architecture just to satisfy stale tests; update tests to reflect intentional code design.
- Keep edit/search/replace tooling harness-verified and deterministic.
- Preserve prior SQL robustness lessons (CTE-aware classification and quote/comment-aware statement splitting).

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
