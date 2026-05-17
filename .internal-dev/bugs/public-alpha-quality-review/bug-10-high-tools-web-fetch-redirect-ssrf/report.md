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

The service uses `HttpClient.Redirect.NORMAL`, validates the original URI, and has private/local host detection that is not reapplied to the redirected URI.

## Evidence

- `AgentWebToolService.java:55` configures normal redirects.
- `AgentWebToolService.java:107` validates original URI.
- `AgentWebToolService.java:218` parses/validates host.
- `AgentWebToolService.java:233` has private/local detection.

## Impact

High: possible SSRF into local/private services.

## Status

Open.

## Next Action

Disable automatic redirects or manually follow redirects with validation on every hop.
