# Terminal UI Core Architecture

## Scope

Defines the internal JLine terminal UI core under `io.mindspice.magenta.ui`.

This package is internal-facing and composes existing `Magenta` lifecycle, route, callback, and security contracts.

## Core components

- `TerminalUiBootstrap`: wires terminal, line reader, session config callbacks, routes, prompt service, and slash registry.
- `TerminalUiRuntime`: owns chat loop, status rendering, slash dispatch, and shutdown hygiene.
- `TerminalUiConfig`: immutable hierarchical config for session/render/behavior/prompt/callback wiring.
- `TerminalUiSession`: immutable runtime wiring record (handle/routes/ingress/context usage supplier).
- `ToolApprovalPromptAdapter`: bridges `SecurityManager.ApprovalCallback` to UI prompt flow.

## Slash command contract

- Commands are immutable at runtime once `SlashCommandRegistry` is constructed.
- Parser supports slash commands with quoted args.
- Max positional arg count is 3.
- Dispatch uses sealed ADT action variants wrapping functional interfaces:
  - `ZeroArg` (`Runnable`)
  - `OneArg` (`Consumer<String>`)
  - `TwoArg` (`BiConsumer<String, String>`)
  - `ThreeArg` (`TriConsumer<String, String, String>`)
- JLine completer resolves command names and optional argument hints from registry spec metadata.

## Prompt contract

- Prompt requests are sealed ADT variants:
  - `ConfirmPrompt`
  - `SelectPrompt`
  - `TextPrompt`
- Responses are sealed ADT variants:
  - `ConfirmResponse`
  - `SelectResponse`
  - `TextResponse`
  - `Cancelled`
- Prompt mode is inline blocking for v1 and defaults to deny when interrupted/failing in tool approval flow.

## Status and rendering contract

- Generic render primitives:
  - `UiRenderBlock`
  - `UiRenderTable`
  - `UiStatusBar`
- Status corners are updated from facade reads:
  - model name
  - estimated context token usage
  - percentage of max context
  - tools/streaming/security mode summary

## Facade reads used by terminal UI

`Magenta` provides:
- `contextUsage(SessionHandle)`
- `contextUsageSupplier(SessionHandle)`

`SessionContextUsage` includes token estimate, model metadata, message count, and context percent.

## Invariants

- Session ingress/egress remains route-based (`SessionRouter`) through `Magenta` facade APIs.
- Tool execution remains security-wrapped through runtime-owned `SessionConfig.toolBridge` path.
- Approval prompts are callback-based and deny-by-default on prompt failures/interruption.
