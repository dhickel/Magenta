# Date
2026-05-26

# Change Summary
Completed Phase 04 prompt/context integration and validation closeout for `agents-md-runtime-support`. Runtime prompt assembly now injects bound-root `AGENTS.md` layers for model-backed agent runtime contexts, with explicit user-prompt precedence and closest-on-conflict wording.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssemblerTest.java`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/end-user/agents.md`
- `.internal-dev/plans/agents-md-runtime-support/phase-04-worker-report.md`
- `.internal-dev/changelogs/2026-05-26-agents-md-runtime-support.md`

# Behavioral Impact
- Prompt/context assembly now appends a structured `Runtime AGENTS.md Context` block when:
  - an orchestration runtime context is present,
  - the context is model-backed agent execution (`agentId` bound), and
  - resolver output contains applicable `AGENTS.md` layers under the bound root.
- Injected block includes:
  - explicit user/task precedence over `AGENTS.md`,
  - root-to-leaf layer ordering,
  - closest layer precedence only on conflict,
  - ancestor non-conflicting guidance retention,
  - per-layer source labels.
- Ordinary chat turns without model-backed agent runtime binding omit `AGENTS.md` injection.
- No-file and no-bound-root paths continue to omit the block cleanly.

# Validation
- Focused: `mvn -Dtest='*PromptContext*Test,*AgentsMd*Test,*Workspace*Test,*Orchestration*Test' test` (PASS)
- Full suite: `mvn test` (PASS)
- Startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` (PASS; app started and graceful shutdown triggered by timeout)
- Whitespace/diff check: `git diff --check` (PASS)

# Specification Impact
- Docs updated to clarify runtime injection scope: only model-backed assignment/agent runtime contexts; ordinary chat without that runtime binding omits `AGENTS.md` injection.

# Risks
- The final directive-required independent `gpt-5.5` xhigh spec-adherence validator was not run by this worker session and remains a downstream validator gate.

# Follow-up Items
- Run the independent final spec-adherence validator against <https://agents.md/> per Phase 04 directive.
- After validator pass, proceed with main-thread archive/merge closeout.

## Remediation Update (Phase 04 Validation Gap)

Validator reported that prompt assembly always resolved `AGENTS.md` from `workspace/` root and could not honor deeper active paths such as `workspace/a/file.txt`.

Remediation applied:

- `PromptContextAssembler` now derives an active runtime alias path from `OrchestrationTaskContext.hostWorkspacePath` relative to `hostDurableWorkspacePath` when that active path is inside the durable workspace root, then passes that path into `AgentsMdResolver.resolveForContext(...)`.
- Fallback remains `workspace` when no deeper path is present or derivation is invalid.
- Added a focused regression test proving subtree switch behavior under one bound root with sibling nested files:
  - `workspace/a/AGENTS.md` + active path `workspace/a/file.txt`
  - then `workspace/b/AGENTS.md` + active path `workspace/b/file.txt`
  - stale nested layer is absent after the switch.

Remediation validation rerun:

- `mvn -Dtest='*PromptContext*Test,*AgentsMd*Test,*Workspace*Test,*Orchestration*Test' test` (PASS, 241 tests)
- `mvn test` (PASS, 849 tests)
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` (PASS)
- `git diff --check` (PASS)

## Targeted Escalation Repair Update

Final spec-adherence validation reported two remaining issues: active-path resolution was still not proven through real model-backed file/shell runtime use, and knowledge/research wording presented Magenta ancestor retention as official AGENTS.md truth.

Repair applied:

- Added an active runtime path to `OrchestrationTaskContext` and a holder helper for runtime tools to publish the confined path they actually touched.
- Updated file and shell tool services to publish active runtime paths after their existing service-owned confinement/alias resolution.
- Updated the tool loop to refresh system instructions after tool execution, so subsequent model invocations see `AGENTS.md` layers for the current tool target path.
- Added model-backed chat/tool-loop regression coverage proving `file_read` on `workspace/a/file.txt` then `workspace/b/file.txt` switches nested AGENTS.md context from `a` to `b`.
- Added direct file/shell service tests for active runtime path capture.
- Corrected knowledge/spec/docs wording to distinguish official nearest-file precedence and user-prompt override from Magenta's ancestor-retention policy.
- Relabeled misleading claims in untracked `.internal-dev/research/agents-md-specification-research.md`; it remains untracked and is not part of the committed closeout.

Escalation validation:

- `mvn -Dtest='ChatServiceTest,*PromptContext*Test,*AgentsMd*Test,*AgentFileToolServiceTest,*AgentShellToolServiceTest' test` (PASS, 92 tests)
- `mvn -Dtest='ChatServiceTest,*PromptContext*Test,*AgentsMd*Test,*Workspace*Test,*Orchestration*Test,*AgentFileToolServiceTest,*AgentShellToolServiceTest' test` (PASS, 317 tests)
- `mvn test` (PASS, 853 tests)
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0` (PASS; app started and graceful shutdown was triggered by timeout)
- `git diff --check` (PASS)
