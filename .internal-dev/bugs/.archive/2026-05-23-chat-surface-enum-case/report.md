# Chat Surface Enum Rejects Lowercase API Values

## Summary

`ChatRequest.MsgRequest.surface` binds directly to `ChatSessionSurface`, so lowercase JSON values such as `browser` and `avatar` are rejected before request handling.

## Scope

This affects `/api/chat` and `/api/chat/stream` callers that provide the optional `surface` field. The current browser JavaScript uppercases the value before sending, so the main UI path is not blocked.

## Reproduction

Send a chat request with a lowercase surface value:

```bash
curl -s -X POST http://localhost:18080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":"00000000-0000-0000-0000-000000000099","message":"ping","model":"test","surface":"browser"}'
```

## Expected

The chat API should either accept known surface values case-insensitively or normalize them at the request boundary.

## Actual

Jackson rejects lowercase enum values with a malformed request error before the controller can normalize or report a domain-level validation message.

## Evidence

During closeout validation, the running app logged malformed request errors for lowercase `browser` and `avatar` values:

- `Cannot deserialize value of type ChatSessionSurface from String "browser"`
- `Cannot deserialize value of type ChatSessionSurface from String "avatar"`

## Impact

Low-to-medium. Browser JS currently sends uppercase values, but direct clients, tests, and future callers may reasonably use lowercase JSON strings and receive a 400.

## Status

Fixed and validated on 2026-05-23. Mirrored to GitHub issue: https://github.com/dhickel/Magenta/issues/7

## Resolution

Added case-insensitive JSON deserialization for `ChatSessionSurface` and kept blank/unknown values rejected.

## Validation

- `mvn -q -Dtest=ChatControllerTest,JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest test`
- `mvn -q test`
- `git diff --check`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
