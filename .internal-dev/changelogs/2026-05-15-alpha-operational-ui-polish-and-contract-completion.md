# Date

2026-05-15

# Change Summary

Implemented the alpha operational UI polish and contract completion plan across dashboard, plans, workflows, projects, agents, runtime settings, and model override surfaces.

- Dashboard inbox copy now reports messages instead of approval-only counts.
- Dashboard work/project/agent summary sections have clearer bordered grouping.
- System Chat has an expandable dashboard affordance and persisted runtime configuration for enablement, model, prompt, approved tools, and context limit.
- Plan editing now exposes more of the finalized plan contract, including settings override JSON, planning task, final message, ordered/movable steps, and row-based input/output editing without example fields.
- Draft plan editing no longer surfaces execution evidence, validation feedback, or pending questions; those are execution/chat concerns rather than draft-plan fields.
- New Plan now creates a persisted draft first, then opens the full editor so row-based fields are visible immediately instead of being hidden behind the old unsaved form.
- Plans now expose a New Plan Chat button that starts planning chat with `/plan`, and existing plans can continue in chat by loading their structured state.
- Workflow editing now labels route columns and exposes selected-node adapter/route inspection through HTMX.
- Project setup uses Manager Type language and agent selection controls.
- Agent detail now keeps Profile, Submit, Docker, and Chat surfaces in the tabbed/accordion detail module, with model dropdowns for overrides.
- Runtime settings schema and migration logic now cover System Chat fields.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanFieldDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskFieldDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/TaskTools.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettings.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `src/main/resources/schema.sql`
- `src/main/resources/static/css/orchestration.css`
- `src/main/resources/static/js/chat-client.js`
- `src/test/java/io/mindspice/magenta2/api/web/FrontendControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact

Operational screens are closer to the backing plan/workflow/project/agent contracts and rely on HTMX fragment updates for standard CRUD and inspection flows. Existing runtime settings rows are migrated in place with new System Chat columns. Plan input/output examples are no longer part of the Java schema, AI task tooling contract, or editor UI.

# Risks

Full automated tests and bounded Spring startup pass. Playwright MCP browser validation was blocked by an existing shared browser profile lock, so final route checks were performed through live HTTP responses instead of browser interaction.

# Follow-up Items

- Resolve the Playwright MCP profile lock so the next validation pass can perform full browser interaction checks.
- Continue the separately deferred graph-canvas workflow editor work recorded in `alpha-deferred-targets.md`.
