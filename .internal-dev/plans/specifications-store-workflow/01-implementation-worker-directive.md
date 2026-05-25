---
status: active
created: 2026-05-25
owner: implementation-worker-agent
model: gpt-5.5
reasoning: medium
source_intent: .internal-dev/plans/specifications-store-workflow/replacement-handoff.md
---

# Implementation Worker Directive

## Assignment

Implement the specifications-store workflow as one documentation/workflow change. Keep scope to `.internal-dev` workflow artifacts, `AGENTS.md`, and directly related docs references. Product code, application behavior, routes, tests, and UI assets are out of scope unless an existing active workflow reference cannot be corrected without touching a doc next to that behavior.

## Files To Edit

Expected edits:

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/index.md`
- `.internal-dev/specifications/workflow.md`
- `.internal-dev/specifications/schema.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/specifications/horizon-ideas.md`
- `.internal-dev/changelogs/2026-05-25-specifications-store-workflow.md`

Expected deletions after migration/drop audit:

- `.internal-dev/focus/`
- `.internal-dev/notes/`

Possible edits only if validation finds active stale references:

- docs files that actively point agents to `.internal-dev/focus/` or `.internal-dev/notes/`
- package `AGENTS.md` files that actively route workflow data to focus/notes
- `.internal-dev/knowledge/*.md` only if implementing this workflow teaches a reusable lesson or an active knowledge file must be updated to point to the new specification store

## Work Steps

1. Confirm `git status --short` and preserve unrelated user changes. Do not touch `.internal-dev/reviews/test-suite-quality-review.md` unless the user separately asks.
2. Read the source files listed in `00-specification-lock.md`.
3. Create the flat `.internal-dev/specifications/` structure.
4. Populate `AGENTS.md`, `index.md`, `workflow.md`, and `schema.md` first. These define the rules future agents will follow.
5. Populate living domain specs with concise intended contracts and observed anchors from existing guidance, focus rows, notes, knowledge filenames, and recent changelogs. Prefer brief, high-signal entries over exhaustive code inventories.
6. Migrate focus data according to the triage map in `00-specification-lock.md`.
7. Inventory active notes and migrate/drop each one. Record each source file and its destination/drop reason in `specifications/workflow.md`.
8. Delete `.internal-dev/focus/` and `.internal-dev/notes/` only after the migration/drop audit is written.
9. Rewrite `AGENTS.md` and `.internal-dev/AGENTS.md` so active workflow guidance routes through specifications, knowledge, decisions, changelogs, bugs, plans, and reviews.
10. Update active references that point to deleted notes/focus files. Example: Avatar style guidance should point to `specifications/web.md` and/or `specifications/simplypages.md`, not `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`.
11. Create the changelog and include a `Specification Impact` section.
12. Run the static validation commands below. Fix failures before handing off.
13. If no product code or UI files were touched, explicitly state that Maven, Spring startup, and Playwright are not applicable.

## Required Wording Patterns

Use wording with hard routing rules, not soft suggestions. Preferred wording examples:

- "Before non-trivial work, list or search `.internal-dev/knowledge/` filenames and read only files whose domain matches the task."
- "When lost, blocked by project context, or correcting a false assumption, run a deeper grep across `.internal-dev/knowledge/` before inventing a new explanation."
- "Use web or official documentation when the missing information is external framework, library, tool, protocol, or platform behavior and local knowledge is absent or stale."
- "Update existing living specification files by default. Create a new specification file only for a genuinely new specification class and update `specifications/index.md` in the same change."
- "Future product direction goes to `specifications/horizon-ideas.md`; accepted deferred product capability goes to `specifications/deferred-features.md`; durable decisions go to `specifications/decisions.md`."
- "Do not route active workflow material to `.internal-dev/focus/` or `.internal-dev/notes/`."

## Static Validation Commands

Run from repo root:

```bash
git status --short
find .internal-dev/specifications -maxdepth 2 -type f | sort
find .internal-dev/specifications -mindepth 2 -type d -print
for f in AGENTS.md index.md workflow.md schema.md architecture.md service-graph.md services.md api.md web.md simplypages.md decisions.md deferred-features.md horizon-ideas.md; do test -f ".internal-dev/specifications/$f" || echo "missing $f"; done
test ! -e .internal-dev/focus
test ! -e .internal-dev/notes
rg -n --glob '!**/.archive/**' --glob '!plans/specifications-store-workflow/replacement-handoff.md' '\.internal-dev/(focus|notes)|focus/|notes/' AGENTS.md .internal-dev/AGENTS.md .internal-dev/specifications docs
rg -n 'update existing|new specification file|genuinely new specification class|flat' .internal-dev/specifications/index.md .internal-dev/specifications/AGENTS.md
rg -n 'service entry|API entry|web entry|architecture entry|specification entry|decision row|deferred-feature row|horizon-idea row|drift record|no-impact note' .internal-dev/specifications/schema.md
rg -n 'filename|domain|deeper grep|official docs|false assumption|repeated mistake|user correction|repeated reverification' AGENTS.md .internal-dev/AGENTS.md .internal-dev/specifications/workflow.md
rg -n 'GitHub Issue|GitHub issue|mirrored directly' AGENTS.md .internal-dev/AGENTS.md
rg -n 'DECISION-20260524-03|DECISION-20260522-01|ARCH-20260523-01|FOCUS-20260525-01|UNFINISHED-20260524-03|IDEA-20260525-03' .internal-dev/specifications
rg -n 'Avatar dashboard|workspace file explorer|plugin/scripting runtime|planner recurrence|email processing|Spring AI 2.0|SearXNG|shell command line parser' .internal-dev/specifications .internal-dev/knowledge .internal-dev/bugs
test -f .internal-dev/changelogs/2026-05-25-specifications-store-workflow.md
git diff --name-status
git diff --check
```

The `find .internal-dev/specifications -mindepth 2 -type d -print` command must print nothing. The active-reference `rg` command must print nothing unless the match is a clearly inactive historical reference that the implementation report explains.

## Stop Rules

Stop and ask the main thread/user before proceeding if:

- migration review shows a current note/focus entry is an active defect but no GitHub issue can be created or linked;
- a required source file is missing and the destination cannot be inferred safely;
- preserving an important source would require adding nested spec directories;
- implementation would need product-code, API, UI, or schema behavior changes;
- deleting `.internal-dev/focus/` or `.internal-dev/notes/` would remove data that is neither migrated nor defensibly dropped.

## Closeout

The work report must include:

- files created/edited/deleted;
- migration/drop summary for focus and notes;
- static validation command results;
- statement that product-code validation was not required, or exact product validation if product code was touched;
- changelog path;
- any new/updated knowledge path, if reusable learning was captured;
- git commit hash after validation and commit.
