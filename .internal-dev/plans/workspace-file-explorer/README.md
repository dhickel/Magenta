# Workspace File Explorer Plan Suite

Status: planning-only
Created: 2026-05-24
Owner: unassigned

## Purpose

This suite is the durable implementation contract for replacing Magenta2's current Work Area directory browser with a familiar native-feeling workspace file explorer, while extracting the reusable browser/picker UI into SimplyPages as an upstream PR.

No product code, tests, docs outside this plan directory, schemas, config, or runtime behavior were changed while creating this suite.

## Artifacts

- `00-specification-lock.md` - objective, source inputs, locked decisions, assumptions, non-goals, acceptance criteria, validation criteria, stop rules, and user gates.
- `01-current-state-analysis.md` - verified Magenta and SimplyPages source areas, current behavior, risks, and exact inspection targets.
- `02-target-architecture.md` - target service/API/schema/UI design, path confinement, metadata, action logging, viewer/editor behavior, picker modes, and compatibility.
- `03-domain-work-units.md` - implementation work units with ownership, dependencies, exact edit targets, criteria traceability, validation gates, failure modes, and senior engineer notes.
- `04-upstream-simplypages-pr-plan.md` - upstream reusable FileExplorer/FilePicker module plan for `/home/hickelpickle/Code/Java/cannasite/java-html-framework`.
- `05-orchestration-handoff.md` - phase orchestration, safe parallel lanes, worker prompts, validation prompts, commit gates, and GitHub publishing expectations.
- `06-validation-redteam-plan.md` - Java, Spring context, API, path traversal, encoding, delete confirmation, tag-follow, picker, Playwright, and upstream validation plan.
- `07-closeout-plan.md` - docs, `.internal-dev`, bugs/issues, focus, knowledge, changelog, archive, commit, and PR closeout requirements.

## Binding Rules

- Downstream agents must read `00-specification-lock.md` first, then the artifact matching their assigned lane.
- Implementation must start on a dedicated branch before phase work begins.
- Commit after each completed implementation phase.
- Use direct AgentMail daemon/wait state; do not recreate a repo-local email ledger.
- Magenta-specific services own workspace roots, path confinement, DB metadata, tags, action logging, Work Area semantics, and Avatar integration.
- SimplyPages owns reusable rendering modules, HTMX fragment patterns, picker/explorer shells, confirmation UI patterns, inspector slots, card/list rendering primitives, and generic viewer/editor shells.
