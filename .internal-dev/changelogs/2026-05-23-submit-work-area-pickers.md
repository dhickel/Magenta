# Submit Work Area Pickers

## Summary

- Added Work Area support to the shared HTMX entity selector.
- Replaced raw selected/output Work Area ID fields in operational submit forms with reusable Work Area picker controls.
- Added an explicit `Browse Work Areas` HTMX action to Work Area selector instances.
- Added active Work Area listing support for picker search.
- Preserved direct output directory input as an owner-root-relative existing directory field.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.api.web.OrchestrationControllerTest,io.mindspice.magenta2.api.web.selector.*Test,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest' test`
