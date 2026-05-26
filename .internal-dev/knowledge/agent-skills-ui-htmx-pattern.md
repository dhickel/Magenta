# Topic

Agent Skills browser/editor HTMX implementation pattern.

# Source References

- `.internal-dev/plans/agent-skills-system/worker-directives/phase-05-skill-browser-guided-creation-ui.md`
- `src/main/java/io/mindspice/magenta2/api/web/SkillFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/selector/EntitySelectorComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/orchestration.css`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/components-and-modules-catalog.md`

# Key Takeaways

- The `/skills` UI should remain an operational master/detail surface: filterable list on the left, selected skill detail/editor on the right, and stacked mobile behavior through CSS.
- Standard interactions stay HTMX-first: list refresh/filter, detail selection, file viewer swaps, editor saves, file creation, guided creation, and assignment updates all return server-rendered fragments.
- Catalog-affecting detail mutations must refresh the left list out-of-band. `SKILL.md` save, detail refresh/revalidate, guided creation, assignment, and unassignment should include an OOB `#skills-list` fragment so description/status/diagnostics/assignment indicators do not go stale.
- Guided creation can use a single server-backed form for the MVP as long as it asks for skill name, when-to-use description, workflow instructions, optional directories, and optional starter files, then writes a valid `SKILL.md` scaffold.
- Optional directory creation in the browser is intentionally limited to top-level `scripts/`, `references/`, and `assets/`. This supports skill-resource visibility without turning `/skills` into a general file manager.
- Reuse the shared `EntitySelectorComponents` agent selector for assignment forms instead of a page-specific lookup widget.
- Do not add custom JavaScript for skills CRUD. The existing selector validation hook and SimplyPages/HTMX behavior are sufficient for the current UI.
- File tables should follow the Work Area explorer information pattern: stable rows, path labels, separate viewer/editor panel, and backend-owned path confinement.

# Engine Relevance

Future Agent Skills UI work should extend `SkillFragments` and `AgentSkillManagementService` instead of adding parallel browser controllers or JavaScript transports. Keep claims scoped to the Magenta root `skills/` repository, agent-level assignment, text-file editing, and non-executing resource visibility until deferred project/user/layered/script capabilities are accepted.

# Open Questions

- Whether a later multi-step guided creator should preserve partial draft state across requests.
- Whether optional resource file previews should add Markdown rendering after browser validation proves the plain text editor layout.
