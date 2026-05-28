# Work Units

## Phase 01 - Assistant Dashboard Refactor

Single implementation worker, model `gpt-5.5-high`.

This phase owns the full focused refactor:

- general dashboard persistence and Assistant seed;
- agent-agnostic dashboard ownership for user widgets;
- `/` dashboard selector/home surface;
- create-dashboard modal and empty dashboard behavior;
- Assistant dashboard rendering/editing/chat preservation;
- removal of Avatar inner shell concepts;
- Work Area browser relocation to agent detail;
- navigation rename/order updates;
- focused tests;
- docs/spec/changelog closeout.

No dependent implementation phases are planned. If the worker discovers dashboard persistence cannot remain agent-agnostic, route cleanup requires unexpected external compatibility support, or Work Area ownership conflicts cannot be handled without runtime contract changes, it must stop and return to planning before coding further.
