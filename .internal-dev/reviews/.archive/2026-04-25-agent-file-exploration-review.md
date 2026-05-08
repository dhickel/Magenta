# Scope

Review whether Magenta should expose explicit file exploration tools or rely on bash commands for listing and discovering files in a Linux-only release.

# Findings

- Magenta already exposes explicit file exploration tools through `file_list` and `file_search`, alongside `file_read`, `file_write`, and `file_replace`.
- Current file tools are bounded to the configured agent data root, reject traversal and symlink escapes, and return concise structured JSON.
- Claude Code exposes both bash and explicit read-only discovery tools such as Glob, Grep, and Read.
- Gemini CLI exposes both file-system tools such as list_directory, glob, search_file_content, and read_file, plus a shell tool for command execution.
- OpenAI's shell tool guidance treats shell access as a broad local/system capability suited to filesystem and process diagnostics, but recommends sandboxing or allow/deny controls.

# Risk Assessment

Relying only on bash would be intuitive for stronger coding models and Linux-native users, but it increases ambiguity and risk for smaller models:

- models may choose verbose, recursive, or destructive command forms;
- shell output is less structured and harder to constrain;
- command injection and path confinement become harder to reason about;
- common discovery operations compete with broader system command execution in the model's tool choice space.

Adding many overlapping file tools could also confuse smaller models, especially if names and responsibilities are not crisp.

# Recommendations

Keep explicit file exploration tools as the primary path for agent data-root work. Treat bash as a separate, optional operational tool for tasks that truly need shell semantics, such as running scripts, inspecting processes, checking disk usage, or invoking Linux utilities.

For Magenta's current scope, keep the tool set small:

- `file_list`: directory and file metadata discovery;
- `file_search`: content discovery;
- `file_read`: chunked reading;
- `file_write`: create/overwrite text files;
- `file_replace`: anchored edits.

Do not add a generic bash tool merely to compensate for file exploration. If bash is introduced, gate it separately from file tools and make approvals/configuration explicit.

# Follow-ups

- Consider whether `file_list` should support a lightweight glob/filter parameter before adding any new tool.
- Consider updating agent prompts/config examples to encourage `file_list` and `file_search` before `file_read`.
- If bash is added later, document when to use bash versus file tools and keep command execution observability separate from file tool transcripts.
