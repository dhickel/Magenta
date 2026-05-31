# Validation Instructions

Each phase validator receives the corresponding worker directive, the worker's summary, changed files, test output, and any browser evidence.

## Validator Responsibilities

- Verify the worker followed the directive and did not expand scope.
- Inspect code, tests, docs, specs, changelog, and evidence.
- Run or review required commands. If commands cannot run, record the exact blocker.
- For UI/browser phases, create a concrete Playwright checklist and dispatch a separate browser validation agent. Reconcile browser evidence before pass/fail.
- Write the report to the phase-specific path named in the directive.

## Report Template

Each validation report should include:

- `Scope`
- `Criteria Checked`
- `Commands Run`
- `Evidence Reviewed`
- `Browser Proof Status`
- `Findings`
- `Required Remediation`
- `Residual Risk`
- `Pass/Fail`

## Remediation Routing

- `code_defect`: fresh scoped repair worker using `gpt-5.3` high unless trivial validator edit.
- `docs_or_evidence_defect`: fresh scoped repair worker using `gpt-5.3` high unless trivial validator edit.
- `browser_harness_defect`: repair browser script/evidence first; change product code only after evidence proves a product bug.
- `plan_defect`: return to planning for revised directive.
- Second failure for the same targeted issue: fresh `gpt-5.5` high-reasoning repair worker.

## Final Quality Review

Do not run final quality review until all phase validation reports pass. The final validator should use `gpt-5.5` xhigh reasoning and compare the full plan suite, validation reports, code, docs, changelogs, GitHub closeout status, email report records, and canonical evidence index.
