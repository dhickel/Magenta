# About project

This project is an agentic orchestration end-user frame work, in the vain of Openclaw, but with its own approach.
This project aims to let users control a team of agents, configure reoccuring tasks, and be an "AI Platform" at home.
This project will act as an assistant to the user and their household.



## Library Documentation
- Simply Pages (UI/Web framework): `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`
- Spring AI `https://docs.spring.io/spring-ai/reference/index.html`

## `.internal-dev` Development Document Store

`.internal-dev/` is the persistent engineering document store for plans, bugs, changelogs, reviews, notes, and reusable knowledge.

### When you are finish task you must use internal-dev for (after asking the user it if time to first):
- Making a changelog to: `.internal-dev/changelogs/`:
- Add any general knowledge to : `.internal-dev/knowledge/`
- Add any notes to : `.internal-dev/notes/`, using or creating the future_consideration.md for future improvement/concerns that should be addressed
- Add any out-of-scope bugs to:`.internal-dev/bugs/`


When generating plans or reviews you are to always use  `.internal-dev/plans/` or `.internal-dev/reviews/`, large multistep plans should have their own directory.

- Operating guide and templates: `.internal-dev/AGENTS.md`
- `.internal-dev/` is intentionally untracked in this repo so the workflow can stay stable across repos.
- Structure:
- `.internal-dev/bugs/`: out-of-scope bugs found during other work (log immediately).
- `.internal-dev/plans/`: active plans in nested plan directories with phase files.
- `.internal-dev/reviews/`: review outputs.
- `.internal-dev/notes/`: deferred ideas/future considerations.
- `.internal-dev/knowledge/`: reusable research and learner-facing summaries.
- `.internal-dev/changelogs/`: finalized change records.
- Do not read `.internal-dev` broadly by default.
- Use controlled access: read only files needed for the active task.
- Ask before logging future considerations in `notes/` when they are out of scope.
- Move finalized bug/plan artifacts to sibling `.archive/` directories.
- Create changelog entries for finalized work.
- Keep AGENTS and `.internal-dev` documentation aligned with major architecture/process changes.


## Core Package

This package owns shared application services and cross-domain building blocks.

### Responsibilities
- Hold shared code only when more specific packages would otherwise duplicate real behavior.
- Keep core abstractions small and justified by current callers.
- Avoid turning this package into a catch-all.

### Change guidance
- Prefer domain packages over `core` for feature-specific behavior.
- Add shared abstractions only when they simplify existing code now.
- Keep names concrete and easy to understand.
- Keep this guide updated when shared responsibilities or core conventions change.

### Validation
- Add focused tests for reusable behavior introduced here.
- Run tests for all packages that call changed core APIs.
