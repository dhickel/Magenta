# Terminal UI Core Architecture

## Scope

Defines the internal Casciian TUI core under `io.mindspice.magenta.ui.tui`.

The TUI package is internal-facing and composes `Magenta` lifecycle, routing, callback, and security contracts.

## Core components

- `TuiTerminalUiBootstrap`: wires runtime + prompt bridge.
- `TuiTerminalUiRuntime`: owns chat-window session wiring, output routing, workspace host integration, and shutdown hygiene.
- `TuiApplication`: Casciian `TApplication` menu shell and action dispatch.
- `WorkspaceHost`: workspace lifecycle/switch/save/load/open behavior with overlay state and diagnostics.
- `WindowKindFactoryRegistry`: extension-safe, fail-fast registry for window kind factories.
- `WorkspaceConfigLoader`: strict workspace YAML loader/validator (`schemaVersion` gated, unknown-key fail-fast, kind/geometry constraints).
- `TuiThemeRegistry` + `TuiThemeConfigLoader`: profile-driven theme loading and runtime switching.
- `TerminalUiConfig`: immutable hierarchical config for session/render/behavior/prompt/callback wiring.
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
- Chat windows provide `/close` to close the active chat window and its backing session without exiting the full TUI runtime.

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

- Status and transcript rendering is window-local in Casciian windows (`ChatWindow`, `EventViewerWindow`, `DocumentViewerWindow`).
- Transcript output is role/event typed (user, assistant, system, tool, security) and rendered through bounded, boxed rows for operator scanning.
- Transcript role colors are theme-driven via `magenta.transcript.*` color keys.

## Workspace contract

- Workspace defaults load from `configs/workspaces/*.yaml`.
- Workspace identity is filename-derived (no inline id field).
- User overlay state persists to `<workspaceRoot>/.magenta/ui/workspaces/<workspace-id>.yaml`.
- Overlay window state persists `visible`, `maximized`, `geometry`, and `normalGeometry`.
- `geometry` is the last saved live bounds; `normalGeometry` is the last non-maximized restore target and is used to reapply maximized windows safely across restart/recreation.
- The `Window` menu is rebuilt from active workspace state and is the primary focus/restore entrypoint for visible, hidden, and recreatable windows.
- Workspace windows use native Casciian `hide()`, `show()`, `maximize()`, and `restore()` behavior; Magenta persists only the restore/layout state Casciian does not keep across process lifetime or true window recreation.
- Workspace-window title-bar close is hide-on-close for normal workspace operation.
- True close is an explicit host action from the `Window` menu or chat `/close` path.
- Non-chat windows true-close by destroying the live instance while retaining overlay state so they can be recreated later from config plus overlay/default geometry.
- Chat `/close` is a true-close operation that tears down the backing session, routes, listeners, and window without exiting the full TUI runtime.
- Workspace schema is versioned via `schemaVersion` and currently supports `1`.
- Validation is strict/fail-fast:
  - unknown top-level YAML keys rejected,
  - unsupported schema versions rejected,
  - unknown window kinds rejected,
  - duplicate window IDs rejected,
  - invalid geometry rejected.
- Validation failures use `WorkspaceValidationException` with structured fields (`status`, `code`, `workspaceId`, `field`).

## Theme contract

- Theme profiles load from `configs/themes/*.yaml` with filename-derived IDs.
- Runtime supports menu-driven switching among loaded profiles.
- Default profile is dark (non-framework-blue baseline).
- Themes can override desktop/window/menu/editor and transcript role colors.

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
- Each live chat window owns its own session, routes, and session-event listeners so true-close can destroy that chat independently.
- Tool execution remains security-wrapped through runtime-owned `SessionConfig.toolBridge` path.
- Approval prompts are callback-based, include tool reason + argument preview, and deny-by-default on prompt failures/interruption.
- TUI window extension is registry-driven; unknown kinds fail fast.
- Workspace/window lifecycle diagnostics are emitted as structured workspace events and surfaced in runtime event viewers.
- Layout transitions remain Casciian-native (`cmTile` / `cmCascade`), then synchronize back into overlay state so maximize/hide/restore semantics stay restart-safe.
