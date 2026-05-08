# Scope

Audit the recent Wedding Cake chat-plan executions and the surrounding plan/context/tool workflow after the user observed that the model returned quickly, showed about 2,900 context usage, and produced a weak description despite being asked to read 50-100 forum posts.

Reviewed:

- `chat-memory.db` conversations `2768d58d-6a8a-4dcf-b041-c0364d187af2` and `48e9dc4f-5aab-4d8f-bba4-b430bf451362`
- `ai_chat_plans`, `ai_chat_plan_steps`, and `ai_chat_memory`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- Generated data-root files under `/home/hickelpickle/.magenta/root`

# Findings

1. The latest artifact-generation execution did not satisfy the requested 50-100 post scope.

Conversation `48e9dc4f-5aab-4d8f-bba4-b430bf451362` saved a plan requiring a minimum of 50 posts and up to 100 if needed. Its generated `collector.py` instead hard-coded four categories, queried `LIMIT 10` per category, and stopped after collecting 40 posts. The tool transcript for `python3 collector.py` reports `Total posts collected: 40`. The final assistant answer also states `Completeness: Information extracted from 40 targeted forum posts`.

2. The latest execution read a generated extraction artifact, not 50-100 individual database posts through iterative model review.

The model created `/home/hickelpickle/.magenta/root/summaries.md`, ran `collector.py`, then called `file_read` once on `summaries.md`. That file contains raw post text grouped by category, but it is a noisy extraction artifact. Several entries only mention Wedding Cake in passing or inside lists/crosses, not as evidence for the requested strain description. This supports the user's suspicion: the model mostly extracted and then synthesized, rather than doing careful post-by-post analysis.

3. Tool transcript storage capped the raw evidence before persistence.

The `file_read` result for `summaries.md` returned 43,927 characters, while `ToolTranscriptService` stores raw tool output at a 40,000-character cap. The transcript marker is stored as `truncated=true`, `largeResult=true`. The model had access to the live tool response during that tool loop, but persisted conversation memory does not retain the exact full extraction.

4. The earlier 50-post execution also did not verify its own output before claiming completion.

Conversation `2768d58d-6a8a-4dcf-b041-c0364d187af2` ran `process_strain_data.py`; the successful shell output says `Processed 50 posts in batches` and `Final report generated: wedding_cake_report.md`. However, the model did not read `summary_batches.txt` or `wedding_cake_report.md` afterward before announcing completion. The generated script itself had brittle logic: it sampled posts mentioning Wedding Cake, generated a report with sentence snippets, and included a hard-coded claim `Synthesized from 50 sampled forum posts`.

5. Plan execution has no objective completion contract.

`ChatService.executeSavedPlan()` marks a saved plan `COMPLETED` after a chat turn returns without throwing. There is no check that plan steps were followed, no verification artifact, no minimum evidence count, no read-back requirement for generated files, and no distinction between "model says complete" and "plan satisfied".

6. The UI context meter is not a reliable proxy for work performed.

Tool work is stored as system transcript markers, and browser history renders only terse tool summaries. Depending on when usage is sampled and whether large tool outputs have been summarized/truncated, context usage can look small even after tool execution. The current UI does not show number of tool calls, post counts, extraction counts, or whether raw evidence was read versus merely generated.

7. The plan saved by the model was too permissive for research quality.

The saved plan allowed a keyword search and random sampling strategy but did not require deduplication, source IDs, relevance scoring, negative evidence handling, citeable snippets, or a final evidence ledger. For a noisy forum corpus, that is enough to produce plausible but inaccurate strain descriptions.

# Risk Assessment

High risk for research tasks that depend on corpus evidence. The current plan flow can produce a polished final answer from a small or noisy extraction while reporting the plan as completed. The bad description appears to be a combination of incomplete sampling, weak search space/relevance filtering, and lack of execution verification.

This does not look like "planning mode failed to call tools entirely." It did call tools. The failure is that plan execution did not enforce the actual research contract and did not surface enough work evidence for the user to detect the shortfall before reading the final answer.

# Recommendations

- Add an execution checklist/evidence ledger for research plans: target count, actual count, unique post IDs, query used, excluded/low-relevance count, generated artifact paths, and files read back into model context.
- Do not mark plans `COMPLETED` solely because the model returned. Add explicit completion evidence for plans with measurable constraints, or use a `REPORTED`/`NEEDS_REVIEW` state.
- Update the plan prompt to require measurable acceptance criteria when users specify counts like 50-100 posts.
- Update execution prompt to require the model to report actual counts and deviations before final synthesis.
- For corpus research, prefer scripts that output structured JSON/CSV rows with post IDs and relevance labels, then have the model read sampled chunks or an evidence table, not only a broad generated Markdown dump.
- Improve tool transcript/history rendering so the UI exposes tool call names, arguments, raw-output truncation, and key counters.
- Consider preserving large extraction artifacts by path plus hash in transcript metadata, so later turns can re-read exact evidence instead of relying on capped raw output.

# Follow-ups

- Bug logged: `.internal-dev/bugs/chat-plan-execution-verification/report.md`
- Bug logged: `.internal-dev/bugs/tool-work-evidence-telemetry/report.md`
