# Design: Magenta2 vs OpenAI Codex Technical Blueprint

## 1. Problem Statement
**Magenta2** is a Java-based runtime providing a foundation for agentic workflows through built-in tools, session management, and context compaction. To maintain its competitive edge, Magenta2 must be benchmarked against industry leaders like the **OpenAI Codex CLI**. This project will produce a high-quality "Technical Blueprint" report that analyzes Codex's tools, harnessing, and agentic workflows to identify gaps and drive future development for Magenta2.

## 2. Requirements

### Functional Requirements
- **Comprehensive Tool Mapping**: 1:1 schema-level analysis of Codex (Rust) vs Magenta2 (Java) toolsets.
- **Full Codex Tool Catalog**: Detailed descriptions, JSON-Schema argument definitions, and return types for all Codex tools.
- **Harnessing & Safety Review**: Direct comparison of Codex's execution loops, safety gates, and environment isolation with Magenta2's `ToolManager`.
- **Workflow & Context Analysis**: In-depth review of Codex's agentic interaction patterns, context management, and compaction techniques.
- **Strategic Roadmap**: A 3-phase prioritized roadmap for future Magenta2 improvements.

### Non-Functional Requirements
- **Technical Rigor**: Precision in schema definitions for direct Java implementation.
- **Actionability**: Clear "how" and "why" for every recommendation.
- **Monolithic Output**: A single, well-structured Markdown document.

### Constraints
- **Language Gap**: Codex (Rust) to Magenta2 (Java) translation.
- **No Code Execution**: Output is a document artifact only.
- **Scope Boundary**: Functional core and agentic logic only (excluding UI/UX).

## 3. Approach

### Selected Approach: Hybrid Capability-Schema Synthesis
This approach balances high-level functional context with low-level technical specifications, organized by system layer.

- **System Layers**:
  1. **Tooling Layer**: Direct schema comparison and cataloging.
  2. **Harnessing Layer**: Reliability, safety, and execution loops.
  3. **Workflow Layer**: Interaction models, context, and compaction.
- **Analysis Methodology**: Recursive scanning of `openai/codex` and `io.mindspice.magenta`, normalization to a unified "Capability Schema," and synthesis of a strategic roadmap.

## 4. Architecture

### Report Organization
1. **Executive Summary**
2. **Tooling Layer**: Parity matrix, full catalog, and Java-style recommendations.
3. **Harnessing Layer**: Reliability, safety, and `ToolManager` updates.
4. **Workflow & Context Layer**: Interaction models, persistence, and compaction logic.
5. **Strategic Roadmap**: Phase 1 (Tools), Phase 2 (Harnessing), Phase 3 (Architecture).

## 5. Agent Team
- **Architect (`architect`)**: Comparative workflow and harnessing analysis.
- **API Designer (`api_designer`)**: Tool layer and schema mapping (Rust to Java).
- **Technical Writer (`technical_writer`)**: Synthesis and report generation.
- **Code Reviewer (`code_reviewer`)**: Final quality gate and technical precision.

## 6. Risk Assessment & Mitigation
- **Risk: Language Gap**: Use `api_designer` for primitive-level type mapping (Rust to Java).
- **Risk: Hidden Context**: Use `google_web_search` and `web_fetch` to identify proprietary patterns.
- **Risk: Scope Creep**: Strict adherence to the "Hybrid Synthesis" methodology and approved architecture.

## 7. Success Criteria
1. Monolithic Markdown report generated.
2. Full Codex Tool Catalog included with descriptions and arguments.
3. 80% of Codex tools mapped to Java-compatible JSON Schemas.
4. Harnessing and Workflow gaps identified with actionable recommendations.
5. 3-phase strategic roadmap included.
6. Final technical quality gate passed.
