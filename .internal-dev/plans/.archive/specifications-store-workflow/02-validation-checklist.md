---
status: active
created: 2026-05-25
owner: validator
model: gpt-5.5
reasoning: high
source_intent: .internal-dev/plans/specifications-store-workflow/replacement-handoff.md
---

# Validation Checklist

## Review Scope

Validate a documentation/workflow implementation only. Product tests are not required unless product code, UI files, route docs with behavior claims, or application configuration were touched.

## Checklist

- Confirm `replacement-handoff.md` was treated as source intent and not replaced by a revived prior plan.
- Confirm `.internal-dev/specifications/` is flat and contains only the required living files unless a new class was explicitly justified in `index.md`.
- Confirm `index.md` tells agents to update existing living files instead of creating duplicates, except for genuinely new specification classes.
- Confirm `workflow.md` records focus and notes migration/drop review before deletion.
- Confirm `.internal-dev/focus/` and `.internal-dev/notes/` are gone after relevant data was copied or intentionally dropped.
- Confirm `schema.md` includes examples for service/API/web/architecture/specification entries, decision rows, deferred-feature rows, horizon-idea rows, drift records, and no-impact notes.
- Confirm `decisions.md` migrated active and superseded focus decision rows with source, date, knowledge references where useful, caveats, status, and review timing.
- Confirm `deferred-features.md` captures accepted future capability from focus unfinished work and notes.
- Confirm `horizon-ideas.md` captures product-directional future hints that are not accepted deferred capability.
- Confirm `architecture.md`, `service-graph.md`, `services.md`, `api.md`, `web.md`, and `simplypages.md` have enough intended-contract content to give future agents a routing home.
- Confirm `AGENTS.md` includes beginning-workflow specification and knowledge gates.
- Confirm `AGENTS.md` includes mid-workflow routing across specifications, knowledge, decisions, changelogs, bugs, plans, and reviews.
- Confirm `.internal-dev/AGENTS.md` no longer routes active work to focus or notes.
- Confirm `.internal-dev/AGENTS.md` contains knowledge filename/search/deep-grep gates and web/official-docs fallback wording.
- Confirm both AGENTS files instruct agents to update knowledge/specifications after false assumptions, repeated mistakes, important user corrections, repeated reverification, and missing reusable context.
- Confirm future product hints route to horizon ideas, accepted deferred product capability routes to deferred features, and durable decisions route to decisions with justification/caveats.
- Confirm bug guidance still requires mirroring `.internal-dev/bugs/` reports to GitHub issues.
- Confirm active Avatar, workspace/output/task/workflow, and SimplyPages guidance no longer points at deleted `.internal-dev/notes/` paths.
- Confirm a changelog exists and includes `Specification Impact`.
- Confirm no unrelated product-code or UI behavior changes are present.
- Confirm `git diff --check` passes.

## Required Commands

Run these commands from repo root and inspect both output and exit status:

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

Expected command results:

- no nested directory output from the second `find`;
- no missing-file echoes from the `for` loop;
- no active focus/notes references from the active-reference `rg`, except explicitly justified inactive historical references;
- schema and workflow grep commands must find real guidance, not placeholder text;
- `git diff --name-status` must be limited to workflow/docs artifacts and deletion of retired focus/notes stores;
- `git diff --check` must pass.

## Remediation Handoff Shape

If validation fails, return a short handoff with:

- failing criterion;
- exact file and line when available;
- command output snippet;
- required correction;
- whether revalidation can resume from the failed command or must restart from the full checklist.

## Product Validation Decision

If only docs/workflow files changed, record:

`Product validation not run: documentation/workflow-only change; no Java, resource, route, UI, schema, or configuration behavior changed.`

If product files changed, require the relevant automated tests and bounded Spring startup before acceptance. If UI behavior changed, require delegated focused Playwright validation under the repo policy.
