---
schema_version: 1
document_type: evidence-index-contract
status: planning
owner: advanced-planner
created: 2026-05-29
canonical_future_path: artifacts/dashboard-widget-suite/validation-summary.json
---

# Evidence Index Contract

The future implementation must create `artifacts/dashboard-widget-suite/validation-summary.json` with this shape:

```json
{
  "task_slug": "dashboard-widget-suite",
  "status": "planning_only",
  "model_constraints": {
    "implementation_workers": "gpt-5.5-high",
    "code_validators": "gpt-5.5-xhigh",
    "planning_red_team": "gpt-5.3-codex-xhigh",
    "playwright": "gpt-5.5-high-or-xhigh-if-selectable"
  },
  "work_units": [],
  "commands": [],
  "validators": [],
  "browser": {
    "required": true,
    "artifact_directory": null,
    "scenarios": [],
    "screenshots": [],
    "result": "pending"
  },
  "artifacts": [],
  "superseded_artifacts": [],
  "tooling_constraints": [],
  "residual_risks": [],
  "stale_reference_sweep": {
    "completed": false,
    "queries": [],
    "findings": []
  },
  "final_reconciler": null
}
```

Status must remain conservative. `fully_validated` is allowed only after all unit validators, integration validator, startup smoke, docs/spec closeout, stale-reference sweep, and browser proof are reconciled.
