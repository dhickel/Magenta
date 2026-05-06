# Task Workflow Creation Prompts

## Task 1: Research Notes

Create a reusable task named `Fixture Research Notes`.

Runtime inputs:
- `topic`, type `string`, required, description `Research topic`.

Named outputs:
- `research_notes`, type `long_text`, required, description `Concise research notes for downstream processing`.

Goal: gather stable seed research notes for `<topic>`.
Deliverable: `research_notes`.
Validation criterion: `research_notes` is present and mentions the topic.

## Task 2: Structured Summary

Create a reusable task named `Fixture Structured Summary`.

Runtime inputs:
- `research_notes`, type `long_text`, required, description `Notes from the research step`.
- `format_instruction`, type `string`, required, description `Formatting instruction`.

Named outputs:
- `structured_summary`, type `long_text`, required, description `Summary structured according to the instruction`.

Goal: transform `<research_notes>` into a structured summary.
Deliverable: `structured_summary`.
Validation criterion: `structured_summary` is present.

## Task 3: Final Report

Create a reusable task named `Fixture Final Report`.

Runtime inputs:
- `structured_summary`, type `long_text`, required, description `Structured summary from the previous step`.
- `audience`, type `string`, required, description `Target audience`.

Named outputs:
- `final_report`, type `long_text`, required, description `Audience-ready final report`.

Goal: write a final report from `<structured_summary>` for `<audience>`.
Deliverable: `final_report`.
Validation criterion: `final_report` is present.
