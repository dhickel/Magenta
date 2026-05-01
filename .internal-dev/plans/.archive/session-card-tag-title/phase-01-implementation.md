Context

Conversation cards currently render the title as a boxed chip and the shortened UUID as an inset hash chip.

Goal

Replace the boxed title chip with a tag-style glyph and bold chat name while preserving the inset hash chip.

In Scope

- Update frontend session card CSS and markup.
- Update focused frontend test expectations.

Out of Scope

- Backend changes.
- Hash behavior changes.

Implementation Steps

- Replace title chip styling with a tag label row.
- Use a CSS-drawn point/tag glyph before the bold title.
- Update rendered class names and tests.

Validation

- Run JavaScript syntax validation.
- Run focused frontend tests.
- Run the project test suite.

Exit Criteria

- Chat titles no longer have a box border.
- Chat titles render with a point/tag glyph and bold name.
