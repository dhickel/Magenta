## Topic

HTMX inline editing pattern for persisted plan/task collection rows

## Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `.internal-dev/changelogs/2026-05-13-operational-ui-parity-fixes-pass-01.md`

## Key Takeaways

- For editable list rows, each input needs a stable `name` (`section + index`) so server-side update routes can resolve target rows.
- `hx-put` should target a section endpoint and include only the closest row (`hx-include="closest .field-row"`) to avoid accidental cross-row payload coupling.
- Returning a fully re-rendered section fragment (`hx-target` section container + `innerHTML`) keeps row ordering and remove-button indices consistent after each edit.

## Engine Relevance

- Reduces inert UI controls that appear editable but fail silently.
- Keeps SimplyPages + HTMX flow server-rendered and predictable without adding JS orchestration complexity.

## Open Questions

- Whether to normalize all collection edits through one generic section mutation API or keep explicit endpoints per section.
