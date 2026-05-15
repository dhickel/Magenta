# Topic
Shell navigation mode for full-document pages in SimplyPages

# Source References
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `io.mindspice.simplypages.builders.TopNavBuilder` contract (default HTMX nav attributes)

# Key Takeaways
- `TopNavBuilder` enables HTMX navigation by default (`hx-get`, `hx-target`, `hx-push-url`).
- For routes that return full HTML shells, HTMX nav can inject a full page into `#content-area`, producing duplicated shell chrome.
- Use `withHtmxNavigation(false)` for top-level shell route links (`/`, `/chat`, `/dashboard`, and other full-document pages).
- Keep HTMX enabled for fragment endpoints and in-page partial refreshes, not full-shell transitions.

# Engine Relevance
Prevents regressions where border/banner/nav UI appears duplicated after route navigation and clarifies where HTMX is expected versus where plain anchors are required.

# Open Questions
- Should we introduce a shared shell/top-nav factory that defaults to non-HTMX navigation for full-document routes to enforce this consistently?
