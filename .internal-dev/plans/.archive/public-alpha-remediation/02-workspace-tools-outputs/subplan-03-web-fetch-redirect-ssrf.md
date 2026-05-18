# Subplan 03: Web Fetch Redirect SSRF

## Goal

Ensure `web_fetch` cannot validate a public initial URL and then follow redirects to private/local hosts.

## Implementation Steps

1. Disable automatic redirects or replace them with manual redirect handling.
2. Validate scheme, host, resolved address, and private/local rules on every hop.
3. Cap redirect count and reject invalid redirect targets.
4. Add tests for public-to-private redirect, redirect loop, valid public redirect, and direct private host rejection.

## Validation

Focused web tool tests prove every redirect hop is validated.
