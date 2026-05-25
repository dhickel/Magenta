# Senior Engineer Guidance

Keep this change at the shell level. The chat runtime, SSE stream, model routing, Avatar database, Work Area explorer, and dashboard widget persistence are not implicated by the reported sidebar behavior. The safest fix is component order, CSS grid geometry, sticky containment, and coordinate-space-correct pointer math.

The main failure mode is measuring one coordinate system and applying it to another. Any code that derives rail width from `window.innerWidth` while the shell is centered or capped will break on wide screens. Measure against the actual grid element that owns the resizable columns, clamp against that same grid width, and write one CSS variable.

For sticky behavior, remember that sticky elements are constrained by their containing block. A sticky chat card inside a content-height rail can appear non-sticky even when the CSS property is present. Prefer making the rail grid item sticky on desktop and keep the chat card visually bounded inside it.

Keep the UI operational and dense. This should feel like the existing Magenta dashboard/agent console, not a new product landing page. Thin borders, small radii, compact controls, and predictable tab/dashboard density still apply after the left rail move.

Do not use Playwright in the current planning pass. For implementation, use focused unit/controller validation and bounded startup. When browser validation is allowed again, visual proof must check layout quality, not merely that `/avatar` returns 200.
