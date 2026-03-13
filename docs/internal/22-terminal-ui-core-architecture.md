# Terminal UI Core Architecture

## Scope

Defines the internal Lanterna terminal UI core under `io.mindspice.magenta.ui`.

This package is internal-facing and composes existing `Magenta` lifecycle, route, callback, and security contracts.

## Core components

- `TerminalUiBootstrap`: wires terminal/screen/TextGUI, session config callbacks, routes, prompt service, and slash registry.
- `TerminalUiRuntime`: owns chat loop, status rendering, inline prompt pane, slash dispatch, and shutdown hygiene.
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
- Completion is intentionally deferred to a later phase; no JLine completer dependency is required for runtime operation.

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
- Prompt mode is inline-pane blocking for v1 and defaults to deny when interrupted/failing in tool approval flow.

## Status and rendering contract

- Status rendering remains type-driven through `UiStatusBar` and is mapped into Lanterna UI components.
- Transcript output is role/event typed (user, assistant, system, tool, security) and rendered through bounded, boxed rows for operator scanning.
- Status corners are updated from facade reads:
  - model name
  - estimated context token usage
  - percentage of max context
  - tools/streaming/security mode summary

## Terminal config contract

- Runtime `magenta.yaml` supports a top-level `terminal` section:
  - `terminal.rendering`: `colorEnabled`, `showTimestamps`, `showStatusBar`, and named ANSI `colors`.
  - `terminal.security.eventVisibility`: `denials_only` (default), `all`, or `off`.
  - `terminal.tools.outputFormat`: currently `compact_summary`.
- Terminal tool output is compact by default and designed for operator scanning (key metrics, not full payload dumps).
- Security event rendering defaults to denied/validation decisions only to reduce approval noise.

## Facade reads used by terminal UI

`Magenta` provides:
- `contextUsage(SessionHandle)`
- `contextUsageSupplier(SessionHandle)`

`SessionContextUsage` includes token estimate, model metadata, message count, and context percent.

## Invariants

- Session ingress/egress remains route-based (`SessionRouter`) through `Magenta` facade APIs.
- Tool execution remains security-wrapped through runtime-owned `SessionConfig.toolBridge` path.
- Approval prompts are callback-based, include tool reason + argument preview, and deny-by-default on prompt failures/interruption.
- UI mutations are callback/event-driven and applied on the UI thread (`TextGUIThread.invokeLater`).
