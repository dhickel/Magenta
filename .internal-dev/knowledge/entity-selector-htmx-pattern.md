# Topic

Reusable HTMX entity selectors for Magenta operational UI.

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/selector/`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`
- `docs/technical/frontend-htmx.md`

# Key Takeaways

- Keep entity lookup in a shared web-layer service instead of scattering per-page search endpoints.
- Selector components should render a normal named form input so progressive enhancement and existing form submission semantics are preserved.
- HTMX can handle search, result rendering, selected-value replacement, and validation fragments without turning selectors into a JavaScript transport surface.
- Server-side validation must remain on unsafe form handlers because client-side selector validation is only a usability affordance.
- Preserve older, more specific validation messages when tests define them; add selector existence checks around those contracts rather than replacing them.

# Engine Relevance

Future operational pages that ask the user to choose a Magenta entity should call the shared selector component and lookup service. This keeps UI behavior consistent, keeps controllers thin, and gives end-user docs one pattern to explain.

# Open Questions

- Whether exact run ID filters should eventually gain a broad run selector, or remain manual because exact run lookup is an operator diagnostic pattern.
- Whether keyboard navigation should be added with a narrow JavaScript helper after live user testing.
