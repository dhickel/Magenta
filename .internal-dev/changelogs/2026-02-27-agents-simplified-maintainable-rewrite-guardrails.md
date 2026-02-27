## Date
2026-02-27

## Change Summary
Updated top-level `AGENTS.md` to define Magenta2 as a simplified-architecture pair-programming rewrite and added strict implementation guardrails for legacy feature referencing and scope control.

## Files
- `AGENTS.md`
- `.internal-dev/plans/agents-simplified-maintainable-rewrite-guardrails/phase-01-agents-simplified-maintainable-rewrite-guardrails.md` (created, then archived)

## Behavioral Impact
- Agents may reference `/home/hickelpickle/Code/Java/Magenta` only for feature intent/behavior.
- Agents must not copy legacy implementations verbatim or backport legacy scaffolding.
- Agents must keep changes minimal, domain-targeted, and simplification-oriented.
- Agents must stop and recommend plan mode when implementation requires out-of-scope supporting code.
- Agents should avoid splitting every logical concern into separate classes/interfaces unless extraction has clear justification.

## Risks
- Interpretation of "out-of-scope supporting code" may still vary across contributors.

## Follow-up Items
- If ambiguity around scope decisions appears repeatedly, add a short decision rubric with examples under `AGENTS.md`.
