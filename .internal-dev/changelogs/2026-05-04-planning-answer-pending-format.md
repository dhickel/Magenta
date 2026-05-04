# Date
2026-05-04

# Change Summary
Fixed the pending chat bubble for planning-question answers so the final answer in a queued question sequence renders in the same `Planning answer / Question / Answer` format as persisted history.

# Files
- `src/main/resources/static/js/chat-client.js`

# Behavioral Impact
- Planning answers submitted from the panel now display consistently while the backend is processing, including the last queued answer that resumes the planning model.
- Backend answer persistence and API payloads are unchanged.

# Risks
- None expected; this only changes the optimistic client-side message text.

# Follow-up Items
- None.
