# Spring HTMX WebJar Serving

SimplyPages `ShellBuilder` enables HTMX by default and emits this script path:

```html
<script src="/webjars/htmx.org/dist/htmx.min.js" defer=""></script>
```

For Magenta to serve that versionless WebJar URL as real HTMX:

- Keep `org.webjars.npm:htmx.org` on the application classpath.
- Keep a WebJar locator dependency such as `org.webjars:webjars-locator-core` on the classpath.
- Keep `spring.web.resources.chain.enabled=true` so Spring MVC installs the WebJar resolver in the static resource chain.
- Do not add controller mappings for `/webjars/htmx.org/dist/htmx.min.js`; controller mappings shadow Spring's static WebJar resource handler.

A quick runtime proof is:

```bash
curl -fsS http://localhost:<port>/webjars/htmx.org/dist/htmx.min.js | head -c 120
```

The response should start with HTMX library code, not `window.htmx=...compat-noop`.
