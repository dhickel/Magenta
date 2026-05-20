# Topic

Chat page full-width layout with side panels.

# Source References

- `src/main/resources/static/css/magenta.css`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java`
- `src/main/resources/static/js/chat-client.js`

# Key Takeaways

The `/chat` page should not inherit the same max-width cap as normal portal pages when it includes persistent side panels. Split `.chat-page` from shared centered page rules when the chat workspace needs to use the full viewport.

When a requested column allocation exceeds 100%, encode it as a normalized ratio with `fr` tracks. For the chat workspace, `25fr 60fr 25fr` preserves equal side panels and a dominant chat surface without causing overflow.

Session output-count UI is rendered through both server fragments and `chat-client.js`, so badge markup changes must be applied in both paths.

When validating an inline-flex badge inside a flex row, Chromium may report computed `display: flex` because flex items are blockified. For this UI, validate the visual capsule behavior, fit, color, and overflow rather than requiring a strict `inline-flex` computed string.

# Engine Relevance

This pattern keeps the main chat usable while allowing operational context panels to remain visible. Future `/chat` layout work should validate computed column widths in a real browser, not only with static CSS assertions.

# Open Questions

None.
