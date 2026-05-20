## Topic
Rendered markdown spacing for chat surfaces

## Source References
- `src/main/resources/static/css/magenta.css`
- `src/main/resources/static/css/orchestration.css`

## Key Takeaways
Rendered markdown containers need explicit nested-content spacing because browser defaults can place list markers and block content outside the visual padding of message backgrounds.

Use container-scoped rules for `.chat-message-body`, `.planning-preview-document`, and `.chat-thinking-body` so markdown lists use outside markers with roughly `1.45rem` left padding, blockquotes get an internal left border and padding, and `pre` elements cap at container width with horizontal overflow.

Avoid later rules that reset rendered markdown lists to `padding-left: 0` or `list-style-position: inside`; those make markers align inconsistently and can visually flatten nested markdown.

## Engine Relevance
Magenta renders assistant and planning output as HTML markdown inside reusable chat containers. Keeping markdown spacing scoped to those containers preserves consistent UI behavior across the main chat shell and orchestration dashboard without changing global list or quote styles.

## Open Questions
None.
