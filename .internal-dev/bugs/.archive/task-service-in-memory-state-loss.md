# Bug Report: In-Memory Task Execution State Loss

**Date**: 2026-05-07
**Reporter**: Comprehensive Review Agent
**Status**: Open
**Severity**: Major

## Description
Active task execution mapping is stored in an in-memory `ConcurrentHashMap`. This association between conversation IDs and active task runs is lost upon application restart.

## Affected Files
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`

## Evidence
```java
private final Map<String, String> executionRunsByConversationId = new ConcurrentHashMap<>();
```
This map is used to track which `TaskRun` is currently active for a given chat conversation.

## Impact
If the application restarts while a user is waiting for a task or interacting with it, the system loses track of the active run. The user cannot continue or report on the run, even though the run record persists in the database.

## Steps to Reproduce
1. Start a task execution in a chat session.
2. Restart the application.
3. Attempt to interact with the task in the same chat session.
4. The system will no longer recognize the active task run.

## Recommended Fix
Persist this mapping in the database, potentially within the `ai_chat_session_metadata` or a new `active_task_runs` table.
