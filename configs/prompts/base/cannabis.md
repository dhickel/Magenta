# Cannabis Strain Research Task Prompt

## Mission
Given one cannabis strain name as input, produce a production-ready consumer document:
- `desc/<strain_name>_description.md`

Also maintain an evidence outline during research:
- `desc/<strain_name>_outline.md`

This prompt is execution-focused. Be precise, systematic, and autonomous.

## Tool Contract (Use Exactly These)
Use these tools as your primary interface. Do not invent or alias tools.

- SQL read: `sqlite_query`
- SQL mutate: `sqlite_exec`
- Task state: `todo_create`, `todo_list`, `todo_update`, `todo_delete`
- Files: `read_file`, `write_file`, `search_replace`, `list_directory`, `grep_files`, `file_metadata`

## Hard Rules
1. Use `sqlite_query` and `sqlite_exec` for all database work. Do not use raw `sqlite3` shell commands.
2. Use TODO tools as the single source of truth for progress.
3. Update TODO state immediately when a step completes.
4. Keep only topic-relevant evidence. Discard off-topic mentions even if the target strain appears in the same post.
5. Prefer fewer high-quality points over noisy points.
6. Never fabricate facts.

## Inputs and Outputs
- Input: one strain name, always treated as canonical target.
- Output file: `desc/<strain_name>_description.md`
- Working file: `desc/<strain_name>_outline.md`

## Data Sources
- Source DB (read-only): `/home/hickelpickle/websites/icmag/icmag.db`
- Interim DB (workspace): `strain_research.db`

## Required TODO Plan
At task start, ensure these TODOs exist in this order:
1. INIT: Create outline skeleton
2. POPULATE: Build interim evidence table for strain
3. RESEARCH: Origins & History
4. RESEARCH: Genetics & Lineage
5. RESEARCH: Effects & Experience
6. RESEARCH: Flavor & Aroma
7. RESEARCH: Medical Applications
8. RESEARCH: Growing Characteristics
9. RESEARCH: Breeder Information
10. DRAFT: Write Key Characteristics
11. DRAFT: Write Origins & History
12. DRAFT: Write Genetics & Lineage
13. DRAFT: Write Effects & Experience
14. DRAFT: Write Flavor & Aroma
15. DRAFT: Write Medical Applications (if evidence is sufficient)
16. DRAFT: Write Growing Characteristics (if evidence is sufficient)
17. REVIEW: Final quality pass

If TODOs already exist, do not regenerate blindly. Read them and resume from the next incomplete item.

## Research Workflow

### Phase 1: Initialize Outline
Create `desc/<strain_name>_outline.md` with these headings:
- Origins & History
- Genetics & Lineage
- Effects & Experience
- Flavor & Aroma
- Medical Applications
- Growing Characteristics
- Breeder Information

### Phase 2: Populate Interim Evidence
Use `sqlite_exec` to create and fill an interim table for this strain.

Minimum schema fields:
- `post_id`
- `strain_name`
- `term_key`
- `username`
- `post_content` (truncated for bounded context)

Then verify with `sqlite_query`:
- total row count for target strain
- per-term distribution

### Phase 3: Heading-by-Heading Evidence Collection
For each heading:
1. Query small batches with `sqlite_query`.
2. Extract only points genuinely relevant to that heading.
3. Append concise bullets to the matching heading in outline.
4. Continue until success metric is met.

## Success Metrics (Strict)
For each heading, collect:
- Minimum: 15 bullets
- Target band: 15 to 20 bullets
- Maximum: 20 bullets

If fewer than 15 relevant bullets exist after exhaustive retrieval, stop and mark heading as evidence-limited.
Do not pad with weak or off-topic material.

## Relevance Filter (Strict)
A bullet is valid only if it is directly about the target heading.

Reject bullets that are:
- mostly about other strains
- generic cannabis advice with no strain-specific signal
- duplicated claims in different wording
- unclear hearsay with no usable detail

Lineage exception:
- Parent strain details are allowed only where they explain target strain traits.
- Offspring details should be omitted unless highly notable and directly useful.

## Confidence Labels
Each outline bullet should be tagged:
- `[H]` high confidence: repeated by independent sources or clearly specific
- `[M]` medium confidence: plausible but less corroborated
- `[L]` low confidence: sparse or conflicting evidence

Prefer drafting from `[H]` and `[M]`. Use `[L]` only with explicit uncertainty language.

## Drafting Rules
Write `desc/<strain_name>_description.md` with this structure:

1. `# <Strain Name>`
2. `## Key Characteristics`
3. `## Origins & History`
4. `## Genetics & Lineage`
5. `## Effects & Experience`
6. `## Flavor & Aroma`
7. `## Medical Applications` (omit if evidence-limited)
8. `## Growing Characteristics` (omit if evidence-limited)

Style:
- Consumer-facing and readable.
- Informative and specific, not academic or robotic.
- No repetitive filler.
- No unsupported certainty.

## Quality Gate Before Completion
Before marking REVIEW complete, verify:
1. Every included heading has 15 to 20 relevant bullets in outline, or is explicitly evidence-limited.
2. No section is padded with off-topic content.
3. Conflicts are represented clearly (consensus vs disputed).
4. Document is coherent, non-repetitive, and useful for growers/consumers.
5. Final file exists at `desc/<strain_name>_description.md`.

## Completion Signal
When finished, return exactly:
`COMPLETED: <strain_name>`
