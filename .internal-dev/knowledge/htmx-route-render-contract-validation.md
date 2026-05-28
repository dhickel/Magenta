---
document_type: knowledge
status: active
created: 2026-05-28
---

# Topic

HTMX route/render contract validation during UI route refactors.

## Source References

- `.internal-dev/plans/assistant-dashboard-refactor/`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/shell-navigation-htmx-vs-full-page.md`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

## Key Takeaways

- In HTMX-heavy surfaces, the route contract is the rendered interaction graph, not only the controller mappings or first page render.
- Route refactors must inventory every emitted `hx-get`, `hx-post`, `hx-put`, `hx-delete`, form `action`, data URL, modal close URL, and fragment target produced by the changed components.
- A shell-level route rewrite is not enough when nested fragments render deeper modal, editor, picker, tag, save, delete, preview, or navigation actions.
- For greenfield-alpha route removals, prefer deletion-oriented validation. Prove old route families are not emitted, documented, or asserted by tests unless the plan has a named allowlist.
- Tests should render representative fragment states and assert emitted HTMX URLs use the new route family. For shared renderers, test with the route prefix or context used by the new owner surface.
- Route sanity tests should catch malformed path mappings, including accidental spaces inside Spring mapping templates.
- Evidence artifacts must distinguish implementation checks from independent validation. Do not call a feature validated while validator or browser proof remains pending.

## Engine Relevance

Use this knowledge before planning or implementing Magenta web route migrations, Work Area browser relocation, SimplyPages/HTMX fragment reuse, or navigation shell cleanup.

Planning agents should include route/render inventory work in worker directives when moving an HTMX surface from one route family to another. Validation agents should inspect rendered HTML, not only controller annotations, and should fail stale route families when compatibility was explicitly not required.

## Open Questions

- Whether Magenta should add a reusable test helper that extracts and validates all HTMX/form URLs from rendered component HTML.
- Whether route-family allowlists should be standardized in validation evidence for greenfield route removals.
