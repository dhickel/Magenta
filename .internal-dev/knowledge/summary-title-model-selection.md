## Topic

Summary, compaction, and conversation-title model selection

## Source References

- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/settings/RuntimeSettingsService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`

## Key Takeaways

- External AI config should define `summaryModel`; the legacy misspelled `summeryModel` remains a backward-compatible fallback only.
- Context compaction uses the effective compaction model. If no compaction model is configured, it falls back to the effective summary model.
- Conversation title jobs use the effective summary model and should not use the chat turn's selected model.
- `agent_jobs.selected_model` records the model selected for that background job; for newly enqueued title jobs, this is the summary model.

## Engine Relevance

Keep internal utility model calls cheap and predictable. Summary-style jobs should remain independent from the primary chat model unless a workflow explicitly needs the chat model's reasoning/context behavior.

## Open Questions

None.
