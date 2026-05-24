# Closeout Report Plan

Status: required final email/report plan

## Delivery

Use the global `agentmail` / `email-followup-wait` workflow after final quality review passes.

Before sending:

```bash
mailctl status
```

After sending, start low-token listening:

- first hour: 5 minute cadence;
- after first hour: 10 minute cadence;
- default cap: 6 hours unless user overrides;
- cancel/adjust if the user responds in chat.

## Final HTML Email Shape

Subject:

```text
Magenta2 workspace file explorer rewrite complete
```

HTML body outline:

```html
<h1>Workspace File Explorer Rewrite</h1>
<p>Short summary of what changed and whether it passed validation.</p>

<h2>Implemented Behavior</h2>
<table>
  <tr><th>Area</th><th>Behavior</th><th>Files</th></tr>
  <tr><td>Explorer</td><td>Details/list table with toolbar, breadcrumbs, required columns, compact rows, no cards.</td><td>...</td></tr>
  <tr><td>Inspect Panel</td><td>Right-side metadata panel with full tags and operations.</td><td>...</td></tr>
  <tr><td>Viewer</td><td>Markdown/text/image modal behavior and safe unsupported fallback.</td><td>...</td></tr>
  <tr><td>Operations</td><td>Rename/delete/copy/move/tag behavior with root confinement.</td><td>...</td></tr>
  <tr><td>Docs/Internal Dev</td><td>Docs, changelog, knowledge, focus/decision updates.</td><td>...</td></tr>
</table>

<h2>Validation Evidence</h2>
<ul>
  <li>Targeted tests: ...</li>
  <li>Full mvn test: ...</li>
  <li>Spring startup: ...</li>
  <li>Playwright desktop/mobile screenshots: ...</li>
  <li>Final quality review: ...</li>
</ul>

<h2>Residual Risks</h2>
<ul>
  <li>External filesystem metadata drift caveat, if still applicable.</li>
  <li>Any user-approved blocker or deferred item.</li>
</ul>

<h2>Senior Engineer Recommendations</h2>
<ul>
  <li>Recommendation on next useful follow-up.</li>
  <li>Release/PR note if SimplyPages dependency/source drift matters.</li>
</ul>

<h2>Commits And Artifacts</h2>
<ul>
  <li>Phase commits with hashes.</li>
  <li>Plan suite path.</li>
  <li>Changelog path.</li>
  <li>Knowledge path.</li>
</ul>
```

## Plain-Text Fallback Shape

```text
Workspace File Explorer Rewrite

Summary:
- ...

Implemented Behavior:
- Explorer: ...
- Inspect panel: ...
- Viewer: ...
- Operations: ...
- Docs/internal-dev: ...

Validation Evidence:
- Targeted tests: ...
- Full mvn test: ...
- Spring startup: ...
- Playwright: ...
- Final quality review: ...

Residual Risks:
- ...

Senior Engineer Recommendations:
- ...

Commits And Artifacts:
- ...
```

## Gate Email Shape

Every phase gate email subject:

```text
Magenta2 workspace explorer gate passed: <gate name>
```

Every phase gate email body includes:

- gate name;
- changed files;
- concise behavior summary;
- validation commands/results;
- Playwright screenshot summary when applicable;
- red-team findings and disposition;
- residual risks/blockers;
- next gate.

## Safety Rules

- Do not include credentials, API keys, local secrets, ignored config contents, or unrelated private workspace details.
- Do not include raw stack traces unless needed and scrubbed.
- Do not attach or paste large screenshots by default; summarize paths/evidence.
- Include `.internal-dev/changelogs/` context so the email stands alone.
- Keep the report terse but complete.

## Senior Engineer Notes

The final email is not a marketing recap. It is the durable operational handoff: exact behavior, exact evidence, exact risks. If a reader cannot tell what shipped and how it was validated, the report is not ready.
