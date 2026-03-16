# Design Document: Magenta2 vs. Codex CLI Comparison Report

## 1. Problem Statement
Magenta2 (our repo) uses a granular `todo` tool (create/list/update/delete) with strict prompt-level enforcement (TODO discipline) to manage complex tasks like the `cannabis3.md` research workflow. However, we are seeing issues with models accurately mapping and adhering to these complex tasks. Other CLIs, specifically Codex CLI (openai/codex), seem to use different mechanisms (like an integrated 'Planning Phase' and `AGENTS.md`) for workflow and task adherence. We need a comprehensive comparison of these two approaches to identify improvements for Magenta2's autonomous agents.

## 2. Requirements

### Functional Requirements:
- **Code & Prompt Analysis:** Deep dive into Magenta2's `TodoTools.java` and `cannabis3.md` prompt discipline.
- **Codex Research:** Detailed analysis of Codex CLI's 'Planning Phase' and its impact on task adherence.
- **Comparison Matrix:** Generate a 'Feature vs. Outcome' matrix comparing 'todo' tool steps to 'Codex Planning Phase' steps.
- **Improvement Roadmap:** Provide actionable recommendations for Magenta2 (e.g., Planning Phase, new tools/ideas) that help agents better navigate tasks.
- **Future Feature Write-up:** A detailed write-up of suggested future features for robust autonomous agents.

### Non-Functional Requirements:
- **Simplicity:** Recommendations must prioritize simplicity and avoid "over-architecture," as this is a hobbyist project.
- **Finality:** The only output is the comparison document and suggestions; no code edits or implementation plans for the improvements are required.

### Constraints:
- No code modifications allowed.
- Primary comparison baseline: `cannabis3.md` research workflow.
- Primary external reference: `https://github.com/openai/codex`.

## 3. Approach

### Selected Approach: Feature-Outcome Matrix
We will conduct a side-by-side analysis of Magenta2's `todo` tool (via `TodoTools.java` and `cannabis3.md`) and Codex CLI's 'Planning Phase' (via `codex-rs` code and documentation). This matrix will map each granular 'todo' step (e.g., `todo_create`, `todo_update`) to a corresponding 'Codex Planning Phase' step (e.g., plan generation, user approval, execution state) for a comparable complex task. This approach is best for identifying granular differences in how task adherence is maintained and where friction points exist.

### Alternatives Considered:
- **Lifecycle Walkthroughs:** Tracking a single complex task from start to finish in both systems. While good for workflow friction, it might miss granular tool implementation differences.

### Selected Tooling:
- `grep_search` and `read_file` for internal code/prompt analysis.
- `web_fetch` and `google_web_search` for `openai/codex` repository research.
- `technical_writer` subagent for document generation.

## 4. Architecture (Document Structure)
The final artifact will be a structured report with the following sections:
1. **Executive Summary:** Overview of the comparison and key takeaways.
2. **Magenta2 Internal Analysis:** Detailed look at the 'todo' toolset, its implementation in `TodoTools.java`, and the `cannabis3.md` prompt discipline.
3. **Codex CLI External Analysis:** Analysis of the `openai/codex` repository, specifically its 'Planning Phase', `AGENTS.md` context, and `SKILL.md` workflows.
4. **Feature-Outcome Comparison Matrix:** Side-by-side comparison of Magenta2's state-heavy tool use vs Codex's plan-centric workflow.
5. **Recommendations for Improvement:** Practical, "no over-architected" tools or ideas from Codex (e.g., Planning Phase integration, simplified task state).
6. **Future Feature Roadmap:** Suggestions for robust autonomous agent capabilities and tool-based intelligence.

### Agent Team:
- **Technical Writer:** Responsible for drafting and structuring the final comparison report and suggestions.
- **Codebase Investigator:** Responsible for detailed internal analysis of Magenta2's `TodoTools.java` and prompt files (`cannabis3.md`, `system.md`).
- **Generalist:** Responsible for deep research into the `openai/codex` repository and planning phase implementation.

## 5. Risk Assessment & Mitigation

### Risk 1: Codex CLI Complexity
Codex's `codex-rs` repository is a large, complex Rust workspace. It may be difficult to find the exact internal planning logic without deep analysis.
- **Mitigation:** Focus research on the `codex-rs/core/` and `codex-rs/cli/` crates, specifically looking for `plan` and `state` related modules. Use `web_fetch` and `google_web_search` for targeted discovery.

### Risk 2: 'No Over-Architecture' Constraint
Codex CLI is a highly robust, professional tool. Its features (like MCP or TUI-based planning) may be too complex for a hobbyist project like Magenta2.
- **Mitigation:** Distill Codex's high-level *principles* (e.g., "Planning before Execution," "Project-level Context") into simpler, tool-based implementations for Magenta2 that don't require a full UI or protocol rewrite.

### Risk 3: Task Specificity
The `cannabis3.md` workflow is very strict. Results may not generalize to all agent use cases.
- **Mitigation:** While using `cannabis3.md` as the primary baseline, also consider the general-purpose `system.md` prompts to ensure recommendations are useful for broader agentic work.

## 6. Success Criteria
The task is successful if:
- **Comprehensive Report:** A large, structured document is generated that compares Magenta2 and Codex CLI.
- **Side-by-side Matrix:** A detailed 'Feature vs. Outcome' matrix mapping `todo` tools to the Codex Planning Phase.
- **Actionable Recommendations:** Clear, practical suggestions for Magenta2 improvements (especially the Planning Phase).
- **Future Feature Write-up:** A detailed, forward-looking roadmap for autonomous agent capabilities.
- **Simplicity:** All recommendations and suggestions adhere to the "no over-architected" and "hobbyist project" constraints.
- **Finality:** The report is complete and requires no code changes or implementation plans to fulfill the current user request.
