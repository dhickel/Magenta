# Avatar Planner Organizer Data And Modals

## Summary

- Added durable Avatar planner task, subtodo, note-link, and calendar-projection tables.
- Added planner records for recurrence, task links, subtodos, and projections.
- Added repository/service support for planner CRUD, note links, recurrence projection, and snapshot inclusion.
- Added `/avatar` organizer modal tabs for planner, todos, calendar, and notes, plus HTMX planner task/subtodo creation endpoints.
- Added reusable compact recurrence form rendering for planner task creation.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.avatar.*Test,io.mindspice.magenta2.api.web.AvatarDashboardControllerTest' test`

## Notes

- Planner tasks remain distinct from executable Magenta work units.
- `CRON` recurrence is stored as advanced planner metadata only; scheduler/contact-user/wait-for-input automation remains deferred.
