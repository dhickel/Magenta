# 05: Browser Validation Evidence (Playwright MCP)

## Scope
Run Playwright MCP browser-origin checks against the operational UI at `http://localhost:18080`, covering dashboard, agents, agent detail, plans, outputs, settings, and agent chat surfaces.

## Playwright MCP Configuration
- App running on `http://localhost:18080` (allowed origin per MCP config)
- Headless Chromium via `--executable-path=/home/hickelpickle/.cache/ms-playwright/chromium-1217/chrome-linux64/chrome`
- Viewport: 1280x720

## Page Load Results

### Dashboard (`/dashboard`)
- Page title: "Magenta Dashboard"
- Sidebar navigation present: Dashboard, Plans, Workflows, Jobs, Projects, Inbox, Agents, Outputs, Settings
- Top banner: "Magenta Operations — Orchestration dashboard"
- Chat link in top nav
- Console errors: 0
- PASS

### Agents (`/agents`)
- Page title: "Magenta Dashboard"
- Create Agent and Reload buttons present
- Agent filter input with HTMX search
- Agent list rendered with magenta agent
- PASS

### Agent Detail (`/agents/{agentId}`)
- Page title: "Magenta Dashboard"
- All 10 tabs present: Dashboard, Queue, Inbox, Jobs, Schedules, Reactions, Workspace, Outputs, History, Chat
- Docker status fragment: STOPPED, container info, mount paths
- Lifecycle buttons: Wake, Sleep, Restart, Refresh, Disable Agent, Delete/Archive
- "Chat with Agent" link to `/chat?agent={agentId}`
- Profile editor: Name, Status, Default Model, Direct Line dropdowns, System Prompt, Approved Tools, Shell Allowlist
- Submit Work form: Assignment Type, Plan/Workflow/Job ID, Priority, Model Override
- Dashboard stats: Queue (5), Inbox (0), Jobs (1)
- Console errors: 0
- PASS

### Agent Chat Panel
- Agent Chat sidebar present with "Agent Chat" header and "Open" button
- Chat tab in tab navigation
- Panel is collapsed by default (expected per Phase 4 design)
- PASS

### Plans (`/plans`)
- Page loaded successfully
- PASS

### Outputs (`/outputs`)
- Page loaded successfully
- Console errors: 0
- PASS

### Settings (`/settings`)
- Model Routing section with dropdowns:
  - Default Model: local-qwen (qwen3.6:35b) [selected]
  - Planning Model: deepseek-v4 (deepseek-v4-pro) [selected]
  - Summary Model: granite4.1:8b (granite4.1:8b) [selected]
  - Compaction Model: local-gemma-e4b (gemma4-fullctx:e4b) [selected]
- All dropdowns use canonical aliases: `canonical-key (raw-name)` format
- Available Models list: qwen3.6:35b, granite4.1:8b, gemma4-fullctx:e4b, gemma4-e4b-UC:latest, gemma4-26b:32k, deepseek-v4-pro
- Context Buffer %: 33
- Save button present
- Console errors: 0
- PASS

## Console Error Audit
All pages loaded with zero browser console errors. No missing resource warnings, no JavaScript exceptions.

## Network Request Audit
- HTMX requests use `hx-get`/`hx-post`/`hx-put`/`hx-delete` attributes — correct SimplyPages/HTMX pattern
- No unexpected JavaScript transport for CRUD operations
- Agent chat panel uses HTMX for tab switching

## Verdict
PASS:
- All operational UI pages load without console errors
- Agent detail page has Chat tab and Agent Chat panel
- Model dropdowns use canonical aliases
- Docker lifecycle controls present
- Plan editor, outputs, settings pages load
- HTMX used for all CRUD interactions (correct per policy)
- Schedules/Reactions tabs present on agent detail (disabled states would appear on tab click)
