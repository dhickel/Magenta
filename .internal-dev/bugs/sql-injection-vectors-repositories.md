# SQL Injection Vectors via String Concatenation in Repositories

**Severity:** CRITICAL | **Status:** open | **Filed:** 2026-05-25 by Reasonix Code

## Summary
Three repositories concatenate untrusted strings directly into SQL, creating injection vectors.

## Affected Files

### 1. `ChatSessionMetadataRepository.java:444,455`
Column names are concatenated directly into SQL without whitelist validation:
```java
"select " + column + " from ai_chat_session_metadata where conversation_id = ?"
"insert into ai_chat_session_metadata (conversation_id, " + column + ") values (?, ?)"
```

### 2. `AuditRepository.java:87`
```java
"alter table audit_event add column " + col + " " + type
```

### 3. `PlanRepository.java:426`
```java
"select count(*) from pragma_table_info('" + table + "') where name = ?"
```
Partially mitigated by regex `[a-zA-Z0-9_]+` check, but fragile.

## Recommended Fix
Use a whitelist (enum or `Set<String>`) of known columns. Reject any column name not in the whitelist before building the query.

## Mirror
Filed as https://github.com/dhickel/Magenta/issues/9
