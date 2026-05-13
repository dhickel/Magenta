# Deferred Ideas: Unified Plan/Task Orchestration Refactor

## Context
Completion of the 5-phase orchestration refactor on 2026-05-11. These items were identified as valuable but out of scope for the current implementation.

## Items

### 1. Orchestration UI integration tests via Playwright
The browser validation pass only asserts static DOM presence. Full workflow integration tests (create plan, save, execute; create workflow with nodes, validate, run; add agent to project, send message) would provide better coverage. The Playwright MCP workflow documented in `live-chat-mcp-workflow-testing.md` could be extended for orchestration UI flows.

### 2. Full model-backed Docker execution end-to-end tests
Docker infrastructure was verified at the daemon/image level, but no end-to-end model-backed plan execution was tested because Ollama was not running during validation. This requires a running LLM service and a test plan that produces outputs through a Docker container.

### 3. Orchestration JS module unit tests
The ES modules under `static/js/orchestration/` have no unit test coverage. Vitest or Playwright component testing could provide coverage for the API client, DOM manipulation, and state management logic in these modules.

### 4. Sidebar auto-collapse on mobile
The sidebar is collapsible on mobile but not auto-collapsed. Users on small viewports see a compressed layout by default. Auto-collapsing on viewports below a threshold would improve mobile UX.

### 5. Model selects populated from API
The orchestration page model selects are currently hardcoded/empty. They could be populated from the `/api/agents` endpoint or a dedicated model listing endpoint for accurate, dynamic model selection.

### 6. Collapse FrontendController and OrchestrationController
The two controller classes could be collapsed into a single router that delegates to page builders, reducing duplication in shell template construction and nav bar configuration.

### 7. Workspace list endpoint
Currently `/api/workspaces` returns 404 (no list endpoint exists). Adding a workspace listing endpoint would complete the workspace API surface for the dashboard.
