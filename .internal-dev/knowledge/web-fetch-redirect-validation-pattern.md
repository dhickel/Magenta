# Web Fetch Redirect Validation Pattern

## Topic

Public-alpha redirect validation for model-accessible web fetch.

## Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-03-web-fetch-redirect-ssrf.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-10-high-tools-web-fetch-redirect-ssrf/report.md`

## Key Takeaways

- Do not let the HTTP client auto-follow redirects for SSRF-sensitive fetch behavior. The service must see and validate each `Location` before sending the next request.
- Validate the initial URL and every redirect target with the same public-web rules: http/https only, host required, DNS resolution required, and local/private addresses rejected.
- Treat redirect loops and malformed `Location` headers as hard failures, not fetch fallbacks.
- Keep unrelated tool behavior separate. `web_search` can retain the redirect-capable SearXNG client while `web_fetch` uses a no-redirect client.

## Engine Relevance

This protects model-approved public web fetch from using an apparently public URL as a bridge into local services, private networks, link-local metadata endpoints, or malformed redirect destinations.

## Open Questions

- Future network hardening may need a lower-level connection strategy to eliminate DNS rebinding windows between validation and the HTTP client's own connection resolution.
