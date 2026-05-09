# Topic

Chat thinking block markdown alignment

# Source References

- `src/main/resources/static/css/magenta.css`
- User-provided screenshot of expanded thinking output on 2026-05-09

# Key Takeaways

Native `<summary>` disclosure markers can render outside the expected padded content area, especially when custom padding is applied to the summary text. For Magenta chat thinking blocks, use a CSS-controlled marker inside the flex summary row so the label cannot run against the card edge.

Thinking markdown content should normalize paragraph and list margins together. Ordered and unordered lists inside `.chat-thinking-body` use `list-style-position: inside` and zero left padding so markers align with paragraph text in the compact thinking card.

# Engine Relevance

This keeps model thinking/reasoning output readable when it contains short plans, numbered lists, or markdown paragraphs. It also avoids visual drift between streaming-rendered thinking blocks and loaded chat history thinking blocks because both use the same `.chat-thinking` classes.

# Open Questions

None.
