# Context

The chat page had gained a right-side outputs panel, but the shared page container still capped `/chat` at the same centered width as normal content pages. The side panels also used fixed widths, leaving the chat transcript narrower than intended on wide screens.

# Goal

Make `/chat` use the full available viewport width, keep chat as the dominant column, and render session output counts as green capsule badges that read `<N> Outputs`.

# In Scope

- Chat page container and grid sizing.
- Session output count markup in server-rendered fragments and client-side rendering.
- Focused test and documentation updates.
- Browser layout validation with screenshots.

# Out of Scope

- Backend output artifact behavior.
- Chat SSE/model behavior.
- Replacing the existing chat session JavaScript renderer.
- Broader SimplyPages shell redesign.

# Implementation Steps

1. Split `.chat-page` out of the centered `.mag-page`/`.orch-page` CSS rule.
2. Replace fixed chat grid side-panel widths with a normalized `25fr 60fr 25fr` layout using `minmax`.
3. Let session and output panels fill their grid tracks and collapse the layout to one column at constrained widths.
4. Replace plain `Outputs: N` text with a scoped `.chat-session-output-badge` capsule in both Java and JavaScript render paths.
5. Bump static asset cache query strings for the changed CSS and JavaScript.
6. Update focused tests and user-facing chat documentation.

# Validation

- `mvn -q -Dtest=FrontendControllerTest test`
- `mvn -q -Dtest=FrontendControllerTest,ChatControllerTest,ChatFileControllerTest test`
- `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Focused Playwright browser validation against `/chat` on desktop, laptop, and mobile viewports.

# Exit Criteria

- Wide `/chat` uses the available page width instead of the previous centered cap.
- Chat is wider than either side panel on desktop.
- Narrow viewports do not horizontally overflow.
- Sessions with outputs show a green `<N> Outputs` badge.
