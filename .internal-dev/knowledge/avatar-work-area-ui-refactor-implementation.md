# Avatar Work Area UI Refactor Implementation

## Summary

The Avatar UI refactor landed as a serial branch implementation on `feature/avatar-dashboard-sprint`. It replaced the old flat widget layout contract with row/widget persistence, added Work Areas as runtime-owned metadata, routed assignment execution/output paths through selected Work Areas, added the Avatar Work Area explorer, added durable planner organizer data, and added Work Area pickers to operational submit forms.

## Durable Boundaries

- Avatar user-facing state stays in `avatar.sqlite`.
- Runtime assignment, workspace, output, job, task, workflow, and Work Area state stays in `magenta.sqlite`.
- Work Areas are metadata around confined directories under an agent/project owned root, not new roots.
- Selected Work Area becomes `workspace/` during assignment execution.
- The broader owner root is exposed as `root/`.
- Planner tasks are personal organizer records and are not executable Magenta task definitions.

## Validation Notes

- UI validation repeatedly caught real CSS/interaction issues:
  - SimplyPages global `.col-*` max-width/flex rules collapsed Avatar grid widgets until overridden inside `.avatar-dashboard-row`.
  - The Organizer modal needed a distinct id and specific submit labels to avoid Playwright/user ambiguity.
  - The orchestration mobile sidebar override kept the sidebar open over agent detail content until it was aligned with framework `mobile-open` behavior.
- Work Area submit pickers reuse the shared HTMX entity selector and add an explicit `Browse Work Areas` action because search-only text inputs read as raw ID fields during browser validation.

## Deferred Automation

Planner recurrence projects calendar occurrences only. Reminder delivery, scheduler execution, contact-user automation, wait-for-input automation, and planner-task-to-assignment automation remain future work.
