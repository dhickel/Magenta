# Remove Leftover Alpha Auth Prompts (Docs)

## Date

2026-05-19

## Change Summary

Updated documentation to remove stale statements that alpha unsafe routes require HTTP Basic authentication and CSRF. Reframed docs to the current open-alpha application-layer posture and preserved references to existing non-auth safeguards.

## Files

- `docs/technical/security.md`
- `docs/technical/api-reference.md`
- `docs/technical/frontend-htmx.md`
- `docs/api/00-index.md`
- `docs/technical/configuration-operations.md`
- `docs/technical/architecture.md`
- `docs/end-user/quickstart.md`

## Behavioral Impact

No runtime behavior change. This is documentation alignment only.

Updated operator expectations:
- App-layer routes are currently open (no built-in auth/CSRF gate).
- Failure expectations focus on domain validation and lifecycle status responses.
- Frontend HTMX guidance no longer references auth-specific headers or auth trigger handling as active requirements.

## Risks

- If auth removal code does not land with these docs, the docs would temporarily describe a future state.
- Security language in historical `.internal-dev` artifacts is intentionally unchanged and may still describe prior behavior.

## Follow-up Items

- Confirm doc language remains aligned after final backend/frontend auth-removal merge.
- If shell helper file naming is normalized away from `alpha-security.js`, refresh doc file references in `docs/technical/frontend-htmx.md`.
