Context

The conversation card currently shows controls on the top row, a tag-style title, and a shortened hash beneath the title.

Goal

Move the shortened hash into the top row between the checkbox and action buttons, shrink it to one UUID row, remove the title glyph, and drop the lower hash chip.

In Scope

- Frontend card CSS and markup.
- Client-side shortened hash length.
- Focused frontend test expectations.

Out of Scope

- Backend API or persistence changes.

Implementation Steps

- Update the top row layout to include a compact hash label.
- Remove the CSS tag glyph from title styling.
- Remove the lower hash chip from rendered session cards.
- Shorten the visible hash helper.
- Update tests and asset version.

Validation

- Run JavaScript syntax validation.
- Run focused frontend tests.
- Run project tests.

Exit Criteria

- Hash appears only in the top row.
- Title is bold without a glyph.
- Conversation cards are more compact.
