## Date
2026-05-13

## Change Summary
Removed dead private code paths identified by symbol-usage analysis and validated by full test/startup checks.

## Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
  - Removed unused private method: `legacyToolChat(...)`.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
  - Removed unused private constants: `INBOX_JS`, `OUTPUTS_JS`.
  - Removed unused private methods: `nodeTypeBadge(...)`, `truncateId(...)`, `summaryCard(...)`.

## Behavioral Impact
No runtime behavior change expected. Removed members were unreferenced and private.

## Risks
Low. Risk limited to accidental hidden references; mitigated by `mvn test` and bounded Spring Boot startup smoke pass.

## Follow-up Items
- Consider adding a maintained static analysis profile for unused private members that supports Java 25 classfiles.
