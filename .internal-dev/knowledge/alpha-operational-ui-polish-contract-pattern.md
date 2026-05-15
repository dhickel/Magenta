# Topic

Alpha Operational UI Polish Contract Pattern

# Source References

- `.internal-dev/plans/alpha-operational-ui-polish-and-contract-completion/`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/editing-api-reference.md`

# Key Takeaways

Schema-shaped editors are the right default for Magenta operational pages. Plan fields that are lists should render as rows with add/update/delete endpoints, and ordered lists such as plan steps should expose move-up/move-down controls that persist the order back into the plan definition. Model-bearing surfaces should use model dropdowns instead of raw text fields.

Draft plan editors should not expose execution-only state. Execution evidence and validation feedback belong to plan execution views, while pending questions belong to planning chat state. The draft editor should focus on the plan definition itself.

Input/output examples are not part of the Magenta plan/task field schema. Field definitions should stay limited to name, type, array, description, required, and schema, with optional/array semantics represented through typed controls.

For SimplyPages operational views, HTMX partials remain enough for most improvements: list reloads, editor fragments, selected-node panels, settings forms, and tab panels can stay server-rendered. JavaScript should remain focused on behavior that needs local event coordination, such as tab active-state handling or workflow input/output filtering.

System Chat is a runtime profile, not hard-coded dashboard behavior. Its model, prompt, approved tools, enabled state, and context limit belong in runtime settings so the dashboard affordance can remain bounded while the canonical chat view stays separate.

# Engine Relevance

When adding future operational controls, keep the UI close to the persisted domain records and expose every important override or contract field through typed controls. Avoid adding dashboard-only behavior that cannot be inspected or configured from settings.

For planning chat launchers, prefer explicit full-page chat navigation with state encoded in query parameters. New planning chat can start with `/chat?startPlanning=true`; continuing an existing draft can use `/chat?continuePlanId=<planId>` and load that plan definition into session planning state before asking the next focused question.

# Open Questions

- Whether System Chat should get its own conversation persistence namespace or reuse the canonical chat storage with a dashboard-specific profile.
- How much of the future workflow graph editor should stay HTMX-rendered versus using narrowly scoped JavaScript for canvas interactions.
