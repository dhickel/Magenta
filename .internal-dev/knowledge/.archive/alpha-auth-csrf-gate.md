# Alpha Auth CSRF Gate

## Topic

Public-alpha authentication and CSRF gate for Magenta web/API mutation routes.

## Source References

- `.internal-dev/plans/public-alpha-remediation/01-security-access-control/subplan-01-auth-csrf-gate.md`
- `src/main/java/io/mindspice/magenta2/api/web/AlphaSecurityConfiguration.java`
- `src/main/resources/static/js/alpha-security.js`
- `src/test/java/io/mindspice/magenta2/api/web/AlphaSecurityConfigurationTest.java`

## Key Takeaways

- The alpha gate is intentionally method-based: `GET`, `HEAD`, and `OPTIONS` are public; unsafe methods require the configured alpha basic credential.
- CSRF uses the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` request header so HTMX and same-origin `fetch` calls can work without adding hidden fields to every fragment.
- HTMX security failures return non-2xx statuses plus a small fragment body, and the shared helper displays 401/403 messages without converting CRUD flows to JavaScript transport.
- Tests should avoid Mockito `@MockBean` for these concrete services under Java 25 in this checkout; hand-written test beans avoided the inline mock-maker failure.

## Engine Relevance

Future web/API mutation routes are protected by default as long as they use unsafe HTTP methods. New browser shells or standalone pages that issue mutations must include `/js/alpha-security.js` or provide equivalent CSRF header behavior.

## Open Questions

- The validation agent still needs to run the original bug-01 route matrix against the implemented gate before the finding is marked passed.
