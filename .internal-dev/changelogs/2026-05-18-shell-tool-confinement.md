# Shell Tool Confinement

## Date

2026-05-18

## Change Summary

Implemented public-alpha remediation bug-08 shell confinement. Shell command wildcards are disabled by default, legacy wildcard shell defaults are dropped unless the operator explicitly sets `unsafeAllowWildcardShellCommands=true`, and shell execution now resolves active assignment working directories against the run workspace/output/project scope when that context exists.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellTools.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileSeeder.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `config/ai-config.example.json`
- Focused shell/config/orchestration tests and package guides.

## Behavioral Impact

- `allowedShellCommands: ["*"]` no longer grants command execution unless `unsafeAllowWildcardShellCommands` is explicitly true.
- Shell wrapper executables and absolute/traversal filesystem access patterns are rejected before process launch.
- Active orchestration shell calls run from the current assignment workspace by default, not the broader agent workspace.
- Example config no longer grants wildcard tool or shell access.

## Risks

Existing local agent profiles that rely on wildcard shell commands will need explicit command allowlists or the unsafe override. The unsafe override only re-enables wildcard executable names; wrapper and path escape checks still apply.

## Follow-up Items

Continue domain 02 with file tool workspace confinement, web redirect/SSRF validation, project workspace materialization, allocation failure handling, output symlink hardening, and output attribution fixes.
