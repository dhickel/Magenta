Date
2026-05-31

Change Summary
Added recursive directory tag inheritance to the transparent file versioning research and registered it as a deferred product capability. The research now treats tags as part of the future workspace file abstraction, with directory-applied tags modeled as recursive policies for existing and future descendant files.

Files
- `.internal-dev/plans/transparent-file-versioning-research/00-investigation-report.md`
- `.internal-dev/specifications/deferred-features.md`

Behavioral Impact
No runtime behavior changed. Current Work Area tags still use the existing file/directory target-type implementation until the versioned filesystem abstraction is designed and implemented.

Specification Impact
Updated `deferred-features.md` with `DEFERRED-20260531-01` for unified tags and recursive directory inheritance.

Risks
The future implementation must avoid treating recursive directory tags as a one-time bulk assignment only. File create/import/move/copy/restore paths and shell/process post-scan reconciliation need to apply inherited tag policies consistently.

Follow-up Items
- Include recursive tag policy migration in the versioned filesystem specification lock.
- Decide whether files can opt out of inherited directory tags.
- Define API/UI language for direct tags versus inherited effective tags.
