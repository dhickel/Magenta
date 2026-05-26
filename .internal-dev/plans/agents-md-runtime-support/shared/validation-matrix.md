# Validation Matrix

## Functional Test Matrix

| behavior | required coverage | likely test target |
| --- | --- | --- |
| No-file behavior | Empty result, no prompt injection block, no error. | Resolver unit test; prompt/context test |
| Root-only loading | Root `AGENTS.md` appears as broadest layer. | Resolver unit test |
| Nested-only loading | Nested `AGENTS.md` under bound root is loaded even without root file. | Resolver unit test |
| Root plus nested layering | Both layers appear in root-to-leaf order. | Resolver unit test; prompt/context test |
| Closest-wins conflict precedence | Prompt/context states closest layer wins on conflict. | Prompt/context test |
| Ancestor context retention | Non-conflicting root/ancestor text remains included with nested text. | Prompt/context test |
| Context change between subtrees | Moving from `a/file` to `b/file` swaps nested layers. | Resolver unit test; prompt/context test |
| Unload/de-emphasis | No-longer-applicable nested layer is absent or explicitly marked inactive. | Prompt/context test |
| Root confinement | `..` or absolute path outside bound root fails closed. | Resolver unit/security test |
| Symlink escape | Symlinked outside path does not leak `AGENTS.md` content. | Resolver unit/security test |
| No overwrite | Existing `AGENTS.md` remains byte-for-byte unchanged. | Workspace/agent service test |
| First creation generation | Newly-created agent workspace gets starter `AGENTS.md`. | Workspace/agent service test |
| Project-bound runtime | Project/effective workspace root is used for project-bound work. | Runtime/prompt integration test |
| Work Area-bound runtime | Selected Work Area/narrowed context is resolved predictably and documented. | Runtime/prompt integration test |
| Runtime injection | System/turn context includes ordered source labels and precedence instructions. | Prompt/context test |
| Official spec adherence | Validator checks implementation against <https://agents.md/> and allows only documented divergence. | Final `gpt-5.5` xhigh spec validator |

## Required Commands

Each worker should run the narrowest relevant tests for its unit. Before final closeout, run:

```bash
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If Spring startup requires unavailable local dependencies or secrets, record the exact blocker and do not mark the work fully validated without user approval.

## Browser Validation

Browser validation is not required by default. If any visible UI changes, a validator must define a focused Playwright checklist covering the changed surface, desktop/mobile screenshots, interaction proof, and visual quality critique.

## Validator Requirements

Every validator must check:

- Plan criteria and negative criteria.
- Architecture and service graph fit.
- Official spec adherence outside Magenta's documented divergence.
- Path confinement and symlink/traversal safety.
- No-overwrite behavior.
- Prompt/context ordering and precedence clarity.
- Docs/spec consistency.
- Test quality, including negative tests and not just happy paths.
- `.internal-dev` closeout requirements for completed implementation work.
