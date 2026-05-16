# Phase Handoff Notes
- Shared invariants: `/chat` remains separate from operational pages; CRUD/filter/reorder flows stay HTMX-first; JavaScript remains limited to SSE chat transport and continuity state.
- Session-origin work is forward-only: new rows are stamped `CHAT` or `AGENT_CHAT`; historical nullable rows remain visible in `/chat`.
- Final implementation merged as one local lane because the same checkout owns all surfaces in this run.
