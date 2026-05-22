# Avatar Email Ingress Correction

## Date

2026-05-22

## Change Summary

Removed the Phase 04 public Avatar email alert HTTP ingress before continuing the sprint. Even with a configured token, `POST /api/avatar/email-alerts` was still the wrong integration shape for the current product direction.

## Behavioral Impact

- Avatar no longer exposes a public HTTP endpoint for email alert ingestion.
- The first-pass `EMAIL_ALERT_RECEIVED` event enum entry was removed so no partial email event contract remains in this sprint.
- Email processing remains deferred until it can enter through the scripting API, internal messaging, or agents using approved tools to add messages.
- The `/avatar` dashboard alerts widget reads existing internal inbox messages and Avatar events only.

## Validation

- Focused Avatar and dashboard tests should be rerun after this correction.
- Browser validation should confirm the alerts widget does not depend on external email ingress.

## Follow-up Items

- Design internal mail processing after endpoint lockdown, including redaction rules, event publication, and tool/messaging authorization boundaries.
