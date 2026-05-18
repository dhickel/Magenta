# Web Fetch Redirect SSRF Hardening

## Date

2026-05-18

## Change Summary

Implemented public-alpha remediation bug-10 / domain 02 subplan 03. Production `web_fetch` now disables automatic redirects and manually validates every redirect hop before following it.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-10-high-tools-web-fetch-redirect-ssrf/report.md`
- `.internal-dev/knowledge/web-fetch-redirect-validation-pattern.md`

## Behavioral Impact

`web_fetch` rejects public URLs that redirect to localhost, link-local, private, multicast, or otherwise non-public hosts. It also rejects malformed redirect targets and stops redirect loops at a fixed cap. `web_search` continues using the existing production redirect-capable client.

## Risks

Public sites with more than five redirects or redirects through hosts that resolve to any private/local address are now rejected by `web_fetch`.

## Validation

- Focused validation passed with `mvn -Dtest=AgentWebToolServiceTest,ChatToolRegistryTest test`.
- `git diff --check` passed.
- Bounded Spring Boot startup reached a healthy app on ephemeral port `42287`; `timeout` then stopped the process with exit code 124.

## Follow-up Items

- Domain 02 subplans 04-07 remain out of scope for this change.
