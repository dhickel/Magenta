# Domain Frontend/Static Review

## Agent

- Agent: domain-frontend-static
- Agent id: `019e3723-6351-78c1-9d79-52a567b21e69`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed static JS/CSS, SimplyPages/HTMX integration, public UI modules, stale DOM ids/copy, and workflow graph island behavior.

## Files and Routes Reviewed

- Routes: `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/projects/{projectId}`, `/inbox`, `/outputs`, `/agents`, `/agents/{agentId}`, `/settings`.
- Files: `OrchestrationController.java`, `workflows.js`, `plans.js`, `projects.js`, `agents.js`, `agent-chat.js`, `inbox.js`, `outputs.js`, `api.js`, `dom.js`, `orchestration.css`.

## Commands and Probes

- `find .. -name AGENTS.md -print`
- `rg --files | rg 'static|templates|frontend|web|ui|Controller|AGENTS|\\.js$|\\.css$'`
- Route mapping scans over web controllers
- Static asset load/reference scans
- Docker/stale-id scans
- `innerHTML`/`fetch` scans in orchestration JS

## Findings

- High: workflow graph composer has stored/DOM XSS exposure through unescaped workflow/node values.
- Medium: workflow JS graph island is only partly justified and currently overrides the HTMX-first editor.
- Medium: workflow graph network failures are under-reported or become silent state changes.
- Medium: agent delete/archive HTMX target is a stale Docker id and does not exist.

## Explicitly Ruled Out

- Plans/projects/agents/settings are mostly HTMX-first in the active route surface.
- Active `/inbox` and `/outputs` pages use server-rendered HTMX fragments; their JS modules appear stale/dead-code risk rather than active page behavior.
- `magenta-tools.js` contains older direct JS transport, but no current controller/page reference loads it.
- Reviewed CSS has responsive collapse rules and no viewport-scaled font sizes or negative letter spacing.
