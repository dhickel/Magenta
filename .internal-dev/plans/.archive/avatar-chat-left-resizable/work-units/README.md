# Work Units

## WU-01: Shell Geometry

Owns component order and CSS grid structure for left rail, divider, and right dashboard fill.

Dependencies: none.

Editable areas:

- `AvatarDashboardComponents.java`
- `avatar-dashboard.css`

Acceptance mapping: left chat, dashboard fills right side, mobile stacks.

## WU-02: Divider Interaction

Owns pointer math, clamping, click-without-drag behavior, and width persistence.

Dependencies: WU-01, because the target coordinate space is the shell grid.

Editable areas:

- `avatar-shell.js`
- optional static assertions in `AvatarDashboardControllerTest.java`

Acceptance mapping: draggable divider, no jump, no lock, persistence.

## WU-03: Sticky Chat And Visual Contract

Owns desktop sticky containment and chat card height/scroll behavior.

Dependencies: WU-01.

Editable areas:

- `avatar-dashboard.css`

Acceptance mapping: chat follows scroll, transcript remains usable, mobile unaffected.

## WU-04: Tests And Docs

Owns focused controller assertions and user/technical documentation updates.

Dependencies: WU-01 and WU-02.

Editable areas:

- `AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/<date>-avatar-chat-left-resizable.md`

Acceptance mapping: tests/docs reflect implemented behavior.

## Sequencing Rationale

Run this as a single implementation phase because all work is tightly coupled in three high-conflict files. Splitting across parallel workers would create avoidable merge and behavior drift risk.
