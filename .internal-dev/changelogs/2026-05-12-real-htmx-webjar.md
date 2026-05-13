# Real HTMX WebJar Serving

Date: 2026-05-12

## Summary
- Removed the `FrontendController` compatibility route that shadowed `/webjars/htmx.org/dist/htmx.min.js` with a noop `window.htmx` stub.
- Declared `org.webjars.npm:htmx.org:1.9.10` and `org.webjars:webjars-locator-core:0.59` so Spring MVC can serve the real HTMX asset from the classpath.
- Enabled the Spring static resource chain so SimplyPages' versionless WebJar URL resolves to the versioned HTMX WebJar asset.
- Updated controller tests to assert rendered shells include the HTMX WebJar script path.

## Validation
- `mvn -q -Dtest=FrontendControllerTest,OrchestrationControllerTest test`
- Bounded live startup on port `18080` and `curl http://localhost:18080/webjars/htmx.org/dist/htmx.min.js`; response began with the real HTMX UMD wrapper, not the former noop stub.

## Notes
- The first live startup attempt inside the sandbox failed because the Docker/Podman socket was blocked with `Operation not permitted`; the same bounded smoke passed when rerun with approved elevated permissions.
