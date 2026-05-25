---
schema_version: 1
document_type: closeout-report-plan
status: active
created: 2026-05-25
owner: unassigned
---

# Closeout Report Plan

## Trigger

Send this report only after implementation, code validation, Playwright validation, final quality review, and commit/GitHub gates pass.

## Delivery Workflow

- Use `agentmail` / `email-followup-wait`.
- Check `mailctl status` before sending and before waiting.
- Send HTML with a plain-text fallback.
- Wait for Dwight's response using the low-token wait workflow.

## HTML Shape

```html
<h1>Avatar Chat Corner Resize Repair</h1>
<p>Short summary of the implemented behavior and whether validation passed.</p>

<h2>Implemented Behavior</h2>
<table>
  <tr><th>Area</th><th>Change</th><th>Files</th></tr>
  <tr><td>Chat resize</td><td>Bottom-right corner resize for width and height.</td><td>...</td></tr>
  <tr><td>Dashboard layout</td><td>Chat width now claims/releases dashboard width.</td><td>...</td></tr>
  <tr><td>Sticky follow</td><td>Chat remains pinned while dashboard scrolls.</td><td>...</td></tr>
  <tr><td>Docs/tests</td><td>Updated tests, user docs, technical docs, changelog.</td><td>...</td></tr>
</table>

<h2>Validation Evidence</h2>
<ul>
  <li>Focused Maven test result.</li>
  <li>Bounded Spring startup result.</li>
  <li>Playwright desktop resize screenshots and bounding boxes.</li>
  <li>Playwright scroll/sticky screenshot and measurements.</li>
  <li>Playwright mobile/narrow screenshot and overflow check.</li>
  <li>Final quality review result.</li>
</ul>

<h2>Residual Risks</h2>
<ul>
  <li>List accepted non-blocking risks, or say none known.</li>
</ul>

<h2>Senior Engineer Recommendations</h2>
<ul>
  <li>Short recommendation on whether to ship, monitor, or follow up.</li>
</ul>

<h2>Traceability</h2>
<ul>
  <li>Branch and commit.</li>
  <li>Plan path.</li>
  <li>Changelog path.</li>
  <li>PR/issue links if applicable.</li>
</ul>
```

## Plain-Text Fallback

Use the same sections in plain text:

- Summary
- Implemented behavior
- Validation evidence
- Residual risks
- Senior engineer recommendations
- Traceability

## Safety

Do not include credentials, API keys, local secrets, ignored config contents, private unrelated workspace details, or full browser logs that might contain sensitive prompt/content data.

## Senior Engineer Notes

The email should be terse but complete. It should let Dwight understand what actually changed and why this pass is more trustworthy than the prior failed static-only attempt: the report must lead with Playwright evidence for resize and sticky behavior.
