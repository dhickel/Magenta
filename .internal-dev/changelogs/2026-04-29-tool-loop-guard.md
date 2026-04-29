# Date

2026-04-29

# Change Summary

Replaced the fixed eight-iteration tool loop cap with targeted loop guards for repeated failures and repeated identical calls.

# Files

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`

# Behavioral Impact

Tool-enabled chat turns can now continue past eight successful tool rounds. Execution stops when the model repeats the same tool name and arguments five times, or when five of the last eight tool responses look like errors.

# Risks

Error detection is heuristic because Spring AI tool responses are plain response strings. Some unusual tool failures may not be counted until tool-specific structured status is added.

# Follow-up Items

- Consider adding explicit success/error status to Magenta-owned tool result envelopes.
