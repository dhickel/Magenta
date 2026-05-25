---
schema_version: 1
document_type: senior-engineer-guidance
status: active
created: 2026-05-25
owner: unassigned
---

# Senior Engineer Guidance

This task has failed because prior work reasoned from code instead of proving browser behavior. The implementation should be intentionally small, but the investigation must be real: identify the scroll container, measure the chat/dashboard boxes, and confirm pointer events fire on the actual handle the user will drag.

Do not preserve the divider as a backup interaction. The user explicitly asked to stop relying on it. A lingering full-height divider will split attention in validation and can let a worker accidentally certify the wrong behavior.

Use CSS custom properties as the contract between JavaScript and layout. JavaScript should only compute and persist clamped dimensions; CSS should own how those dimensions affect the grid and chat card. This keeps the implementation traceable and avoids imperative layout churn.

Avoid native CSS `resize` for the main implementation. It can resize a box, but this feature requires resizing the grid track and preserving dashboard width semantics. A custom pointer handler is more explicit and easier to validate.

Sticky behavior must be checked against SimplyPages shell CSS. `.content-wrapper` currently has `overflow-y: auto`; that can become the sticky containing block. If sticky does not follow, look at ancestor overflow and scroll container before changing `top`, `height`, or z-index values.

Keep mobile boring. Desktop can be resizable; mobile should stack, hide the handle, ignore saved desktop dimensions, and remain readable without horizontal scrolling.

Do not touch chat runtime code unless a direct initialization failure is proven. `avatar-chat.js` owns SSE chat. `avatar-shell.js` should own only shell resize behavior.

When validation fails, classify the failure before fixing. If the worker implemented the plan incorrectly, dispatch a scoped fix. If Playwright shows the plan's sticky assumptions are wrong, revise this plan first rather than layering hacks.
