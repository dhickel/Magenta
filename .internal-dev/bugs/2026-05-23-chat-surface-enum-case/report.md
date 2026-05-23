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

Open. Mirrored to GitHub issue: https://github.com/dhickel/Magenta/issues/7

## Next Action

Add case-insensitive deserialization for `ChatSessionSurface` or replace direct enum binding with a small normalizer that accepts known lowercase/uppercase values and rejects unknown values clearly.
