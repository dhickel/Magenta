# Avatar UI Style Guidelines

## Date

2026-05-22

## Change Summary

Added Playwright-backed internal UI style guidance for future `/avatar` redesign work. The note records the existing `/dashboard` and per-agent dashboard layout, density, visual language, and interaction patterns so later agents keep Avatar styling consistent with Magenta's operational console.

## Files

- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.codex-orchestration/avatar-ui-style-guide/notes.md`

## Behavioral Impact

No runtime behavior changes. Future agents now have a durable style reference before changing Avatar dashboard surfaces.

## Risks

The note reflects the dashboard state inspected on 2026-05-22. Re-check `/dashboard` and `/agents` before major future styling changes if those screens have materially changed.

## Follow-up Items

Use the guide during the next `/avatar` redesign pass.

## Validation

- `gpt-5.4` medium subagent inspected `/dashboard`, `/agents`, representative agent detail routes, and `/avatar` with Playwright against the running local app.
- Screenshots were saved under `target/playwright-avatar-style-guide/`.
