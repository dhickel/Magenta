# Closeout Report Plan

After each issue/fix completion gate, the main thread sends an HTML/plain-text email report through the global `email-followup-wait` skill.

## Per-Gate Email Inputs

Include:

- Issue number(s), title(s), and GitHub URL(s).
- Commit hash and pushed branch.
- One-paragraph summary of behavior fixed or verified.
- Tests and startup/browser validation run.
- Validator report path.
- Browser artifact path when applicable.
- Docs/spec/changelog updates.
- Residual risks or blockers.
- GitHub issue closeout link or status.

Exclude:

- Secrets, credentials, ignored config values, local private workspace details unrelated to the issue, and raw logs that include sensitive paths beyond relevant repo artifact paths.

## Report Timing

The main thread sends the report only after:

1. Worker completes.
2. Validator passes.
3. Required browser validation is reconciled.
4. Commit is created.
5. Branch is pushed.
6. GitHub issue is closed referencing the commit.

If a phase is blocked or fails validation twice, send a blocked/status report only if the user requests it or orchestration policy requires it.
