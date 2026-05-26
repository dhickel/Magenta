# Topic
Agent shell workspace alias resolution for operator Exec flows.

# Source References
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`
- Playwright revalidation on 2026-05-15 against `/agents` Exec tab.

# Key Takeaways
- Agent-context shell execution must run with `OrchestrationTaskContextHolder` populated with `agentId`; otherwise resolution falls back to host `dataRoot` behavior.
- The Exec UI default uses `workingDirectory=workspace`, so resolver must treat `workspace` as a first-class alias for the selected Work Area when one is active, otherwise the effective durable workspace root.
- Supporting `workspace/<subpath>` avoids accidental double-prefix resolution (`workspace/workspace/...`).
- Keep `.` as an equivalent alias for the workspace root for compatibility with prior operator usage.
- During assignment execution, `outputs/` is the active run-local output staging directory, physically `runs/<runId>/outputs/` under the relevant agent workspace root. Final output destinations are backend promotion targets, not shell working directories.
- Legacy `scratch` and job workspace aliases should remain compatibility-only if implementation still accepts them; new prompts and UI guidance should not advertise them as current workflow paths.

# Engine Relevance
This is a direct operator-flow reliability issue: if alias semantics diverge from UI defaults, production runtime appears broken even when backend isolation logic exists.

# Open Questions
Should we normalize the Exec form to a dropdown of allowed aliases such as `workspace`, `root`, `outputs`, and `run` to reduce free-text errors?
