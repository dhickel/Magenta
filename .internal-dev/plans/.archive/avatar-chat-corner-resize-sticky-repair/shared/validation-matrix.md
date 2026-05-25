---
schema_version: 1
document_type: validation-matrix
status: active
created: 2026-05-25
owner: unassigned
---

# Validation Matrix

| Criterion | Validation | Required Evidence | Negative Checks |
| --- | --- | --- | --- |
| AC1 left rail + remaining dashboard | Playwright desktop `/avatar` and static test | Screenshot and bounding boxes showing chat left of dashboard | Fail if chat is right-side or dashboard has unused side gutter caused by bad grid math |
| AC2 no divider reliance | Static DOM/test and screenshot | No primary `.avatar-chat-resizer` divider interaction; docs mention corner handle | Fail if validation drags a divider or docs still instruct divider dragging |
| AC3 visible corner handle | Playwright screenshot and DOM query | Handle visible at bottom-right of chat on desktop | Fail if handle is hidden, too small to hit, or overlaps composer controls |
| AC4 horizontal resize affects dashboard | Playwright drag and measurements | Chat width changes by meaningful pixels and dashboard width changes inversely | Fail if chat box changes but dashboard width does not |
| AC5 vertical resize changes height | Playwright drag and measurements | Chat height changes by meaningful pixels after vertical drag | Fail if only width changes or transcript/composer become clipped |
| AC6 bounds preserve usability | Playwright min/max drags | Dashboard remains at least planned min width; chat remains usable | Fail if dashboard collapses, chat gets tiny, or max width locks out shrink |
| AC7 chat follows scroll | Playwright scroll and measurements | After scroll, chat top remains within small tolerance of target top margin | Fail if chat scrolls away with the dashboard |
| AC8 mobile usable | Playwright narrow viewport | Screenshot, no handle visible, no horizontal overflow, readable stack | Fail if desktop dimensions force side-by-side squeeze or overflow |
| AC9 code validation | Maven and startup commands | Passing command outputs recorded in implementation notes | Fail if startup not attempted or failure is hand-waved |
| AC10 stale state eliminated | Playwright setup | Fresh context or localStorage clear documented | Fail if validation may be using old persisted rail values |

## Commands

Focused code validation:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
```

Bounded app startup:

```bash
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Recommended live Playwright app command:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-avatar-corner-resize-playwright.sqlite --magenta.executor.chat-threads=4'
```

Use a fresh `/tmp` database name or delete the old one before validation when clean state matters. Use a fresh browser context or clear at least:

```js
localStorage.removeItem("magenta.avatar.chatRailWidthPx");
localStorage.removeItem("magenta.avatar.chatPanelHeightPx");
```

## Senior Engineer Notes

The strongest validation is geometry, not screenshots alone. Screenshots catch visual nonsense, but bounding boxes prove that the dashboard width responds and sticky actually pins. Require both.
