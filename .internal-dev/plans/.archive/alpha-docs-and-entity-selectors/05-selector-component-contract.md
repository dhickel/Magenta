# Selector Component Contract

## Context

Magenta's operational UI is built with SimplyPages and should remain HTMX-first. The selector should be reusable and component-shaped, not copy-pasted raw HTML strings.

The current helper style lives in `OrchestrationController`, with methods like `agentSelect(...)`, `modelSelectWithCurrent(...)`, and shared `label(...)`. This work should extract a reusable component/helper rather than adding more one-off form code.

## Goal

Create a reusable searchable selector component for entity IDs that supports recommendations, manual entry, validation, and dependent refreshes.

## In Scope

- Server-rendered SimplyPages component/helper.
- HTMX search and validation attributes.
- CSS for result rows, selected state, error state, loading/empty state.
- Narrow JS only if needed for keyboard/focus synchronization.
- Progressive enhancement so manual typing still submits a normal form field.

## Out of Scope

- Full custom client-side state management.
- A general app-wide design system refactor.
- Replacing normal enum selects.

## Target Design

Component API example:

```java
public record EntitySelectorConfig(
    String name,
    EntityKind kind,
    String currentValue,
    String label,
    String placeholder,
    boolean required,
    Map<String, String> contextParams
) {}
```

Rendering contract:

- Visible text/id input uses the real form `name`.
- A result panel has a stable generated id.
- `hx-get` searches `/selectors/{kind}/options`.
- `hx-trigger` is `keyup changed delay:300ms, focus`.
- `hx-target` points at the result panel.
- `hx-include` includes the input and any context fields.
- `hx-get` validation runs on `change` or `blur` against `/selectors/{kind}/validate`.
- Result rows are buttons or links that request a server fragment replacing the selector with the selected value, so the no-JS path remains viable.

Preferred HTMX-only selection pattern:

1. User types in the visible `name` input.
2. HTMX swaps search results into the panel.
3. Clicking a result performs `GET /selectors/{kind}/selected?...`.
4. Server returns the entire selector component with the chosen value.
5. Form submission sends the chosen ID through the original field name.

Narrow JS allowance:

- A small `entity-selector.js` may add arrow-key navigation, escape-to-close, and focus management.
- It must not fetch data itself if HTMX can do it.
- It must not become the submission transport.
- File header must explain why JS is used.

## Implementation Steps

1. Study SimplyPages component docs before editing:
   - `docs/reference/components-and-modules-catalog.md`
   - `docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
   - `docs/operations/03-writing-tests-for-components-and-modules.md`
2. Implement `EntitySelectorComponents`.
   - Use `HtmlTag`, `Div`, `TextInput`, `Button`, and existing CSS class conventions.
   - Avoid raw HTML except where the existing SimplyPages API lacks an equivalent.
   - Use stable ids based on field name and kind, with an optional unique suffix for repeated rows.
3. Add CSS in `src/main/resources/static/css/orchestration.css`.
   - Classes: `.entity-selector`, `.entity-selector-results`, `.entity-selector-option`, `.entity-selector-status`, `.entity-selector-invalid`, `.entity-selector-selected`.
   - Ensure compact layout works inside `.field-row`, `.orch-form-grid`, and `.orch-form-stack`.
4. Add optional JS only if keyboard support cannot be achieved cleanly with HTMX alone.
   - Suggested file: `src/main/resources/static/js/orchestration/entity-selector.js`.
   - Load only on pages using operational shell if current asset pattern allows.
5. Add component rendering tests.
   - Required field includes validation hook.
   - Optional field allows blank.
   - Context params are included.
   - Existing missing current value renders warning state.

## Validation

- Component tests assert rendered structure and HTMX attributes.
- CSS does not break existing select/input styling.
- JS usage, if added, is documented in `docs/technical/frontend-htmx.md`.
- No selector component creates a nested card or broad JS transport surface.

## Exit Criteria

- Page integrations can replace manual ID fields by calling one helper/component.

