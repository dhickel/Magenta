# Public Alpha Quality Review Fixtures

The validation campaign created isolated SQLite databases for clean startup, warm startup, and Playwright browser validation.

Generated `.db` files are intentionally not committed because the warm database copy can contain local user/runtime data. Durable validation evidence is recorded in:

- `.internal-dev/reviews/public-alpha-quality-review/automated-validation-evidence.md`
- `.internal-dev/reviews/public-alpha-quality-review/playwright-public-pages-evidence.md`

Fixture paths used during the campaign:

- `clean-db/clean-startup.db`
- `warm-db/chat-memory-warm-copy.db`
- `playwright.db`
