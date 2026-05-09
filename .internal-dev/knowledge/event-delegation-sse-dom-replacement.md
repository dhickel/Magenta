# Topic

Event delegation robustness under SSE-driven DOM replacement

# Source References

- `src/main/resources/static/js/chat-client.js` — `renderPlanningPanel()`, `updatePlanStatus()`, plan action click handler
- `src/main/resources/static/js/orchestration/app.js` — `DOMContentLoaded` init with direct `addEventListener` calls
- `src/main/resources/static/js/magenta-tools.js` — similar `DOMContentLoaded` + direct listener pattern
- `src/main/resources/static/js/framework.js` (SimplyPages) — global document-level event delegation for accordion/tabs/callouts
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java` — `ChatModule` composition, `chat-planning-panel` placement

# Key Takeaways

1. **Event delegation on a parent survives `innerHTML` replacement — but the parent must not be removed.** Attaching `addEventListener('click', ...)` on `#chat-planning-panel` and using `event.target.closest('[data-plan-action]')` is correct event delegation. However, if any code replaces the entire panel element (not just its children), listeners are lost. Moving to `document` level mimics the `framework.js` pattern and is maximally robust.

2. **`defer` scripts execute after HTML parse but before `DOMContentLoaded`.** `chat-client.js` loads with `defer` in the `<head>`, so its IIFE runs when the DOM tree is complete. All `byId()` lookups should succeed. The distinction from `DOMContentLoaded` is important: `defer` scripts execute in source order before the `DOMContentLoaded` event fires. `app.js` uses `DOMContentLoaded` instead, which means it runs AFTER all `defer` scripts finish.

3. **`display: contents` on a parent does not block event bubbling.** The `.chat-module-composer-region` uses `display: contents`, making it transparent in the box tree, but events still bubble through normally.

4. **SSE streaming creates DOM churn.** During streaming, `updateStreamingAssistantMessage()` updates message body HTML on every chunk, and `updatePlanStatus()` replaces the plan panel HTML on `start`/`tool`/`system`/`done` events. Any direct event listeners on elements inside these regions would be lost on replacement. Event delegation is mandatory here, not optional.

5. **`requestInFlight` gates all planning actions.** Even with correct event delegation, `runPlanningAction()` silently returns when `requestInFlight` is `true`. The flag resets in `finally` blocks, so it's only true during active SSE streams or fetch operations.

# Engine Relevance

When building UIs with SSE streaming and dynamic DOM replacement:
- Use document-level event delegation for click handlers on dynamically-created elements.
- Avoid direct `addEventListener` on elements whose parents get `innerHTML` replaced during streaming.
- Gate user actions with a `requestInFlight` flag but ensure it resets reliably in `finally` blocks.
- The SimplyPages `framework.js` pattern (document-level `click` listener checking `.closest(selector)`) is the recommended approach for all dynamic UIs in this project.

# Open Questions

- Could the plan buttons benefit from optimistic UI (show button immediately, disable during request) rather than waiting for `requestInFlight` to clear?
- Should `updatePlanStatus` debounce when called rapidly from successive SSE events?
