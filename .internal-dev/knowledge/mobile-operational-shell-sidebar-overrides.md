# Topic

Mobile operational shell sidebar overrides

# Source References

- `src/main/resources/static/css/orchestration.css`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/07-mobile-rendering-model-and-responsive-behavior.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-16-high-mobile-orchestration-shell-unusable/report.md`

# Key Takeaways

SimplyPages shell pages with side navigation render `.main-container.has-sidebar`, `.main-sidebar`, `.content-wrapper`, and `#content-area`. The framework mobile model expects the sidebar to move off-canvas at `max-width: 768px` and the shell grid to become a single content column.

When app CSS needs to override this layout, target `.main-container.has-sidebar` directly at the phone breakpoint. A lower-specificity `.main-container` rule is not enough to beat the desktop sidebar rule, and page-level layout rules such as `.browser-layout` do not affect the outer shell grid.

# Engine Relevance

Operational pages should keep CRUD and tab/content behavior server-rendered/HTMX-first. Standard responsive shell behavior should be fixed with CSS overrides rather than adding JavaScript transport or page-specific DOM rewrites.

# Open Questions

Validation still needs live browser measurement for the target `/agents/{agentId}` page at `390x780` after this implementation.
