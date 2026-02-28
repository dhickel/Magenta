# Context
Magenta2’s runtime has been simplified to a lean architecture centered on `RuntimeConfig`, `Magenta`, `SessionManager`, `ContextManager`, `ModelRunner`, and `OllamaClient`, with callback-driven integrations and ADT-backed context history.

Documentation exists but is still uneven in depth and polish. The current objective is to create one comprehensive, high-quality internal documentation system that is technically rigorous, coherent, and directly usable for implementation and extension work.

# Goal
Produce a single, sweeping documentation initiative that delivers:

- architecture-level understanding of the entire runtime,
- exact API and contract clarity,
- practical integration guidance with realistic examples,
- operational troubleshooting guidance,
- long-term documentation maintenance discipline.

# In Scope
- Runtime architecture documentation across all implemented subsystems.
- API surface documentation for runtime entry points and key managers.
- Deep contract documentation for callback integration and tool/security wrapping.
- Context and compaction behavior documentation, including strategy contracts.
- Model execution and Ollama transport documentation, including mode-selection behavior.
- Internal docs navigation and discoverability improvements.
- AGENTS.md reflection/alignment with implemented runtime terminology and service boundaries.

# Out of Scope
- End-user/tutorial/product docs for non-engineering audiences.
- Full documentation for unimplemented runtime services (except clearly labeled future placeholders).
- Runtime feature additions that are unrelated to documentation correctness.
- External website or control-plane documentation.

# Implementation Steps
1. Establish canonical internal docs structure under `docs/internal/` with a clear index and stable navigation model.

2. Author a single primary runtime developer guide that explains:
- how the full system fits together,
- how control and data flow across configuration, session, context, model, and callbacks,
- how contracts compose without class explosion,
- how extension seams are intended to be used safely.

3. Produce subsystem deep-dive documents that each include:
- design intent,
- responsibilities and explicit non-goals,
- invariants,
- state transitions,
- failure behavior,
- extension points and integration boundaries.

4. Document the API surface as behavior contracts, not only signatures:
- lifecycle semantics (`start`, `resume`, `fork`) and what each guarantees,
- callback execution semantics (`onTokenStream`, `onMessageStored`, `toolBridge`, `onError`),
- model turn-loop behavior, tool loop behavior, and fallback semantics,
- compaction strategy selection and fallback behavior.

5. Add integration-quality examples that demonstrate actual runtime usage patterns:
- terminal streaming,
- UI event fanout,
- autonomous execution mode,
- security-wrapped tool bridge,
- blocking-only deterministic mode.

6. Add end-to-end sequence walkthroughs (narrative + flow diagrams) for:
- startup and config resolution,
- user turn with no tool calls,
- user turn with tool call loop,
- compaction summarize path with fallback.

7. Add operational guidance and troubleshooting documentation:
- parse/validation failure diagnosis,
- session lifecycle error diagnosis,
- model transport failure diagnosis,
- callback/tool bridge failure diagnosis,
- common implementation mistakes and direct corrections.

8. Add explicit “known constraints” sections across docs to prevent over-promising behavior:
- in-memory resume limitations,
- deferred persistence seams,
- callback-owned policy behavior,
- current model-provider assumptions.

9. Reflect architecture terminology and runtime contracts in `AGENTS.md`:
- remove stale terms and mismatched services,
- align to implemented managers and runner/client split,
- document expectation to update docs whenever architecture materially changes.

10. Add and maintain changelog records in `.internal-dev/changelogs/` for each finalized documentation alignment pass.

11. Define and apply a documentation quality checklist for future runtime changes:
- implementation accuracy,
- contract completeness,
- example quality,
- cross-link integrity,
- mismatch disclosure when code/docs diverge.

# Validation
- Verify each documented class/method/contract exists and matches code behavior.
- Verify all lifecycle semantics in docs match implementation exactly.
- Verify callback and tool bridge examples align with actual `SessionConfig` and runtime flow.
- Verify compaction and model docs reflect current strategy and transport behavior.
- Verify internal docs index includes all canonical documents.
- Verify AGENTS architecture language matches current runtime implementation.
- Verify all examples are coherent, engineering-grade, and implementation-oriented.

# Exit Criteria
- Internal docs provide a complete, implementation-accurate runtime narrative from startup to turn execution.
- Every major architecture path (config, session, context/compaction, model/transport, callback integration) has detailed technical coverage.
- Engineers can integrate new frontends/runners and tool/security wrappers using docs without first reverse-engineering source.
- AGENTS and internal docs are terminology-aligned with runtime code.
- Documentation quality bar and maintenance workflow are defined and usable for future changes.
