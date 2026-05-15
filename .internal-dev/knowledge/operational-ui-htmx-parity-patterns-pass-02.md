# Operational UI HTMX Parity Patterns (Pass 02)

## Practical patterns applied
- For pages previously using JS transport, prefer HTMX shell + fragment endpoints:
  - page: renders containers with `hx-get` + `hx-trigger=load`
  - fragment: renders tables/cards and action buttons directly from server
  - action endpoints return fresh fragment HTML for replacement
- For editable list rows in plan editor, use deterministic field names (`sectionValue{index}`) and dedicated section `PUT` endpoints.
- For typed task inputs, parse request strings at submit time into scalar/array values by declared field type before creating the assignment.
- For cross-surface deep links (e.g., `/jobs/{id}`), preserve the list+detail shell and preload the detail panel via HTMX instead of introducing a separate page model.

## Runtime correctness pattern
- For job execution visibility, mark each job item run status transitions (`RUNNING`, `COMPLETED`, `FAILED`) in sync with assignment progress and include child run IDs when available.
