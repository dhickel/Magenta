# Issue Ledger — Docker Runtime Parity Validation

| ID | Severity | Surface | Type | Expected | Actual | Evidence | Next Action | Status |
|---|---|---|---|---|---|---|---|---|
| BUG-01 | blocker | Docker UI | stop-status-mismatch | UI shows STOPPED after Sleep | UI shows IDLE | Phase 02 | Fresh post-stop inspection implemented; browser revalidation pending | Fixed in code |
| GAP-01 | high | Docker/Runtime | missing-feature | UI for container exec | No UI for execInAgent() | Phase 05 | Added bounded Exec tab on agent detail | Fixed in code |
| GAP-02 | high | Plans/Tasks | missing-feature | UI for plan/task run history and streaming | No run history or stream UI | Phase 05 | Added recent-runs surface to plan editor | Fixed in code |
| GAP-03 | high | Workflows | missing-feature | UI for workflow run monitoring | No run monitoring UI | Phase 05 | Added workflow runs surface with resume control | Fixed in code |
| GAP-04 | high | Jobs | missing-feature | UI for job run start/cancel | No run start/cancel controls | Phase 05 | Added start/cancel run controls and runs panel | Fixed in code |
| GAP-05 | medium | Agents | missing-feature | UI for assignment cancel/pause/resume | No controls for these actions | Phase 05 | Added queue controls | Fixed in code |
| GAP-06 | medium | Jobs | missing-feature | UI for job recurrence | No recurrence config UI | Phase 05 | Added recurrence editor | Fixed in code |
| GAP-07 | medium | Workspaces | missing-feature | UI for lease management | No lease management UI | Phase 05 | Add lease controls to workspace tab | Open |
| GAP-08 | medium | Settings | missing-endpoint | REST endpoint for available models | No /api/models endpoint | Phase 05 | Added GET /api/models | Fixed in code |
| GAP-09 | low | Projects | missing-feature | UI for project network | No network visualization | Phase 05 | Added project network summary | Fixed in code |
| GAP-10 | low | Projects | missing-feature | UI for project workspace | No workspace tab | Phase 05 | Add workspace tab to project detail | Open |
| GAP-11 | low | Inbox | js-review | inbox.js (192 lines) could use HTMX polling | JS polling in inbox.js | Phase 05 | Evaluate HTMX polling replacement | Open |
| GAP-12 | low | Settings | js-review | Dual save paths (JS + HTMX) | Settings uses both | Phase 05 | Kept settings persistence HTMX-only | Fixed in code |
| ENV-01 | env-blocker | Phase 07 | incomplete | Test Docker unavailable scenarios | Not tested | Phase 07 | Schedule dedicated negative-test session | Open |
| ENV-02 | env-blocker | Phase 06 | incomplete | Test full workflow + job + project journeys | Not tested | Phase 06 | Schedule after workflow/job UI gaps are filled | Open |
