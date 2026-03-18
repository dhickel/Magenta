# TUI Subtree Agent Guide

This `ui.tui` subtree contains Magenta2's Casciian-based terminal UI implementation.

## Aim

The TUI should remain a lean, framework-aligned terminal experience that supports the current and planned deliverables of the rewrite:
- application shell and menu baseline
- chat interaction window
- event/document viewer windows
- workspace switching and layout operations
- future theme and extension hardening

The aim is not to outsmart or replace Casciian. The aim is to compose Magenta2's TUI using Casciian correctly.

## Read These First

Before implementing any TUI fix or feature in this subtree, consult these references first:

- Casciian wiki:
  - https://github.com/crramirez/casciian/wiki
- Casciian demo source directory:
  - https://github.com/crramirez/casciian/tree/main/code/src/main/java/demo
- Local Casciian notes index:
  - `/home/hickelpickle/Code/Java/Magenta2/.internal-dev/notes/casciian/00-index.md`
- Local Casciian framework reference map:
  - `/home/hickelpickle/Code/Java/Magenta2/.internal-dev/notes/casciian/2026-03-17-framework-reference-map.md`
- TUI rewrite plan context:
  - `/home/hickelpickle/Code/Java/Magenta2/.internal-dev/plans/2026-03-17-casciian-tui-rewrite/`

Use those sources before you change code. Do not start from local implementation assumptions alone.

## Operating Rules

- Use the official Casciian wiki and demos first, then inspect local code.
- Prefer framework primitives over custom TUI behavior.
- If Casciian already provides the behavior, use it instead of reimplementing it.
- If the local code appears to fight the framework, rebase your approach on the references above.
- If you feel you are fighting the framework, implementing TUI features yourself, or are just lost in general, stop and return to the wiki, demos, and local notes before continuing.
- Avoid repeated manual geometry compensation, framework-state mirrors, synthetic focus handling, or custom window-management behavior unless the framework references clearly justify it.
- Treat current code as mutable and possibly wrong; treat the framework references as the starting point for correction.

## Reference Demos

Start with the demo closest to the problem:

- `DemoApplication.java` for app shell, menu, and window lifecycle patterns:
  - https://github.com/crramirez/casciian/blob/main/code/src/main/java/demo/DemoApplication.java
- `DemoTextWindow.java` for `TText` resize and scroll behavior:
  - https://github.com/crramirez/casciian/blob/main/code/src/main/java/demo/DemoTextWindow.java
- `DemoEditorWindow.java` for `TEditor` sizing and editor behavior:
  - https://github.com/crramirez/casciian/blob/main/code/src/main/java/demo/DemoEditorWindow.java
- `Demo7.java` for `TPanel` and layout-manager usage:
  - https://github.com/crramirez/casciian/blob/main/code/src/main/java/demo/Demo7.java

## Scope Reminder

Use the TUI rewrite plan for intended feature scope and deliverables, but do not copy plan implementation steps into code or use the plan as a substitute for framework research.

## Note Capture Rule

If a fix or feature implementation produces useful, validated knowledge from the Casciian wiki or demos, ask the user whether that knowledge should be captured as a note in `.internal-dev/notes/casciian/`.

Do not create those follow-up knowledge notes automatically. This avoids storing untested or misguided framework interpretations.
