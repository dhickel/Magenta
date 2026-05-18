# Topic

Runtime wording cleanup for public-alpha remediation

# Source References

- `AGENTS.md`
- `src/main/resources/application.yml`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`
- `.internal-dev/plans/public-alpha-remediation/08-code-quality-stale-cleanup/subplan-03-stale-doc-comment-cleanup.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-22-medium-filesystem-allocation-continues/report.md`

# Key Takeaways

- The current public-alpha campaign runtime contract is filesystem/workspace-backed execution with host shell tools.
- Active Docker/Podman wording should be treated as stale unless it is explicitly scoped to optional external setup or historical evidence.
- Generic `container` wording is acceptable when it refers to DOM containers, HTMX target containers, CSS container concepts, or local UI variables.
- The stale bug-22 PlanService comment about Docker-level failure is no longer present; allocation failure now resolves before downstream execution.

# Engine Relevance

Future cleanup and validation agents should scan active source, tests, docs, config, README, and build files for Docker/Podman/container-runtime claims, then inspect matches manually before changing them. Historical review, bug, and archived plan evidence should remain unchanged unless a current progress or closeout artifact is being updated.

# Open Questions

- None.
