# Web Fetch Redirects Can Reach Private Hosts

## Summary

`web_fetch` validates the original URL but can follow redirects to private/local hosts without revalidating the final URI.

## Scope

`AgentWebToolService`.

## Reproduction

1. Fetch a public URL that redirects to `localhost`, link-local, or private IP.
2. Observe that the HTTP client follows the redirect.

## Expected

Every redirect target should be validated against private/local host rules before the request proceeds.

## Actual

Resolved in implementation: production `web_fetch` no longer relies on automatic redirects. It follows redirects manually and validates each target before the redirected request is sent.

## Evidence

- `AgentWebToolService` now keeps `web_search` on a redirect-capable client while `web_fetch` uses a no-auto-redirect client.
- `AgentWebToolService` validates the initial URI and every redirect target for http/https scheme, public host, and DNS/private/local rules.
- `AgentWebToolServiceTest` covers direct private host rejection, public-to-private redirect rejection, redirect loop cap, valid public redirect success, and invalid redirect target rejection.
- Implementer validation passed with `mvn -Dtest=AgentWebToolServiceTest,ChatToolRegistryTest test`, `git diff --check`, and bounded startup reaching port `42287`.
- Validator reran focused web tool/registry tests, `git diff --check`, and bounded startup reaching port `44333`.

## Impact

High: possible SSRF into local/private services.

## Status

Implemented and validated in public alpha remediation domain 02 subplan 03.

## Next Action

Continue with public alpha remediation domain 02 subplan 04.
