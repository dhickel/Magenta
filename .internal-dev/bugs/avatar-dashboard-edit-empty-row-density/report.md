# Summary

`/avatar?tab=dashboard&edit=true` can render a high ratio of empty row shells relative to populated rows, which creates a tall low-signal edit surface and weakens the operational shell density target.

# Scope

- Avatar dashboard edit mode only
- Existing persisted Avatar layout data with multiple empty rows
- Visual/browser quality issue, not a backend crash

# Reproduction

1. Start the app and open `/avatar?tab=dashboard&edit=true`.
2. Use an Avatar layout state that contains several empty rows.
3. Compare the first viewport and overall page density against `/dashboard` or the non-edit Avatar shell.

# Expected

Edit mode should stay compact and operational. Empty rows should not dominate the page or push useful widgets far below the fold.

# Actual

The page can render many repeated `.avatar-empty-row-shell` sections with row decoration chrome, which makes the edit surface much taller than the operational shell baseline and lowers first-viewport usefulness.

# Evidence

- Playwright validation on 2026-05-24 found `12` total row shells with `7` empty rows and `9` widgets.
- The delegated validation compared Avatar edit mode against the operational dashboard shell and flagged the edit surface as materially less dense in the first viewport.
- GitHub mirror: `https://github.com/dhickel/Magenta/issues/8`

# Impact

- Edit mode feels visually noisy and less aligned with the dense operational shell used elsewhere.
- Useful widget content is pushed down, reducing scan efficiency.
- The issue is likely to recur for users who accumulate empty rows over time.

# Status

Open
Mirrored to GitHub issue `#8`.

# Next Action

Design a compact empty-row strategy for Avatar edit mode. Candidate directions include collapsing repeated empty rows into a grouped affordance, surfacing one primary empty-row insertion target per region, or otherwise reducing chrome-to-content ratio without silently destroying layout data.
