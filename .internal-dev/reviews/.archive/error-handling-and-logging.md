## Code Review Results

**Scope**: Error handling and logging across model execution, tool execution, UI relay, and database metadata.
**Files Reviewed**: 5
**Total Findings**: 1 Critical, 3 Major, 1 Minor

### Findings

| # | Severity | File | Line | Description | Suggested Fix |
|---|----------|------|------|-------------|---------------|
| 1 | Critical | `AuditRepository.java`, `AuditService.java` | N/A | The audit system lacks the capability to record errors or exceptions in the database schema and service interface. | Add a `recordError` method to `AuditRepository` and `AuditService`. Consider schema updates for `error_message` or `stack_trace`. |
| 2 | Major | `GlobalExceptionHandler.java` | 20-68 | Exceptions map to HTTP status codes but are not logged server-side via SLF4J or persisted to the DB. | Add an SLF4J logger and log all caught exceptions. Pass `conversationId` to `AuditService` if present. |
| 3 | Major | `ChatController.java` | 175-191 | Stream errors in `streamResolved` discard user messages and send SSE errors but do not log to the database. | Call a method on `ChatService` or `AuditService` to log the error before completing the emitter. |
| 4 | Major | `ChatService.java` | 546-556, 834-837 | `recordExecutionFailure` and `toolChat` log via SLF4J and internal state but don't persist errors to the DB via `AuditService`. | Update `ChatService` to use `AuditService` to record errors during model or tool execution exceptions. |
| 5 | Minor | `ChatController.java` | 175-185 | Inconsistent error handling between plan execution (records failure) and regular execution (discards user message). | Ensure both paths log the error to the database for observability, despite differing recovery mechanisms. |

### Summary

The overall application relays model and tool execution exceptions effectively to the UI via Server-Sent Events and HTTP mappings, providing a recoverable experience for the user. However, it completely lacks database persistence for these exceptions. While `AuditRepository` successfully logs prompts, completions, context, and tool activity, it has no schema or method for logging failures. Because `GlobalExceptionHandler` and `ChatController` also omit database persistence for faults, debugging failed sessions retrospectively will be challenging. Adding error observability through the existing audit layer is a priority to ensure session-linked issue tracking.