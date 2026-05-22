# Chat Planning Question Composer Notes

## Global Assumptions
- `/chat` anonymous planning questions should appear above the main chat composer, visually similar to saved plan chat.
- The prompt should show the current question count, such as `Question m/n`, in the card corner.
- `/chat` planning approval/action controls should remain available and should not regress execution, continue, or cancel controls.
- Saved plan chat and `/chat` should share reusable UI rendering where practical.

## Active Agents
- Planner: pending.
- Implementer: pending.
- Validator: pending.
- Architecture summary: pending.

## Completed Work
- Created dedicated branch `chat-planning-question-composer`.
- Implemented shared planning prompt-card UI and routed `/chat` active question submissions through the main composer.
- Added docs, changelog, reusable knowledge entry, and archived the finalized task plan.
- Completed requested `.internal-dev` bug and artifact triage; remaining active empty-job bug was mirrored to GitHub issue #6.

## Validation Results
- `node --check src/main/resources/static/js/chat-client.js` passed.
- `mvn -Dtest=FrontendControllerTest,OrchestrationControllerTest#namedPlanChatCreationOpensChatTabWithChatModuleStructure test` passed.
- Browser validation passed for desktop/mobile `/chat` prompt card, saved plan chat prompt card, and `/chat` answer routing.
- 2026-05-22 validator pass (local Playwright CLI fallback): focused tests passed (`FrontendControllerTest`, `OrchestrationControllerTest#namedPlanChatCreationOpensChatTabWithChatModuleStructure`) and JS syntax check passed.
- `/chat` active-question submit routing verified by browser network capture: main `#chat-form` submit posted `POST /api/chat/validation-conv-1/plan/answers` with slash-prefixed answer text in payload; no `/api/chat/stream` request during that submit.
- `/chat` prompt card visual/state verified with `#chat-planning-panel.active.question-active`; compact prompt card rendered without double framing (new panel style) and question count badge shown.
- Saved plan chat verified as HTMX-wired with `#plan-chat-form` + `#plan-chat-input`, `hx-post=/plans/_editor/{id}/planning-chat/answers`, and shared `.planning-question-card` prompt with count badge.
- Artifacts: `test-results/chat-planning-question-composer-2026-05-22/` (`chat-desktop-prompt-card.png`, `chat-mobile-prompt-card.png`, `saved-plan-chat-desktop-prompt-card.png`, `validation-summary.json`, `network-requests.json`, `console-messages.json`).
- Note: Playwright MCP browser endpoints were blocked locally with `ERR_BLOCKED_BY_CLIENT`; browser validation executed via local Playwright automation instead.

## Remediation Notes
- Implemented shared prompt-card rendering via `ChatModuleRenderer.planningQuestionCard(...)` and reused it in saved plan chat.
- `/chat` now stores active prompt-question metadata on `#chat-planning-panel`; main `#chat-form` answers route to `/api/chat/{conversationId}/plan/answers`, including slash-prefixed answer text.
- Saved plan chat remains HTMX-owned through `#plan-chat-form`/`#plan-chat-input`; `/chat` remains SSE/client-state owned.
- Focused controller test, JS syntax check, and bounded Spring Boot startup smoke test passed.

## Blockers
- None known.

## Closeout Work
- Relevant docs updated.
- `.internal-dev` changelog and knowledge entry added.
- Finalized plan artifact moved to `.internal-dev/plans/.archive/chat-planning-question-composer/`.
- Commit implementation and `.internal-dev` updates only.

## Final Validation Status
- Passed with caveats: Playwright MCP local navigation was blocked, so validation used local Playwright browser automation; live model-backed transitions were limited by local invalid API key responses.
- PASS with minor caveat: one expected 400 console error occurred when intentionally forcing a synthetic conversation id (`validation-conv-1`) to isolate submit routing; no unexpected JS/runtime errors observed in validated flows.

## Handoff Notes
- Existing unrelated worktree changes must not be reverted or staged.
- Planner findings: `/chat` uses `ChatModuleRenderer.sessionChatModule()` with `#chat-planning-panel` above `#chat-form`; `chat-client.js` currently renders pending questions as a nested answer form in that panel and submits to `/api/chat/{conversationId}/plan/answers`. Saved plan chat renders its prompt/composer server-side in `OrchestrationController.savedPlanChatPanel()` via HTMX `#plan-chat-form`. Best implementation path is a shared `ChatModuleRenderer` prompt-card component plus JS that stores the active `/chat` question metadata on `#chat-planning-panel` and routes main `#chat-form` submissions to the existing answer endpoint while preserving existing plan action buttons.
2026-05-22 coordination note: internal artifact triage archived finalized plan directories (`plan-chat-split-output-hardening`, `public-alpha-quality-review`, `services-ux-architecture-refactor`, `workspace-file-architecture-refactor`) into `.internal-dev/plans/.archive/`; use archived paths for historical references.
