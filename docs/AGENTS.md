# Documentation Guide

This guide governs files under `docs/`.

## Source-Of-Truth Policy

- Documentation is user-visible intended truth.
- Code remains the logical source of truth.
- If documentation and code diverge, record the mismatch in the current task output and create or update the appropriate `.internal-dev` artifact: bugs for defects, specifications for intended contract drift, or plans/reviews for scoped follow-up.

## Update Policy

- Any technical change updates the relevant technical docs.
- Any user-facing behavior change updates the relevant end-user docs.
- API or controller changes update API docs.
- New docs must be linked from an index before the task is complete.
- Avoid speculative future docs unless the content is explicitly marked as future-facing.
- Do not route current documentation work to retired focus, notes, or inbox stores.

## Folder Contract

- `README.md`: top-level documentation entry point and audience routing.
- `end-user/`: operator-facing guides for using Magenta.
- `technical/`: contributor-facing architecture, service, persistence, UI, and operations docs.
- `api/`: route, payload, SSE, and integration contracts.
- `maestro/`: older Maestro design/planning material; keep it intact unless a task explicitly targets it.

## Writing Rules

- Keep docs accurate, concrete, and tied to implemented behavior.
- Prefer short task-focused pages over large mixed-purpose documents.
- Use relative links for repo-local documentation.
- When a section is intentionally incomplete, say what will own the content next instead of filling it with guesses.
