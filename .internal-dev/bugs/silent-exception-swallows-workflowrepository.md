# 25+ Silent Exception Swallows in WorkflowRepository Schema Migrations

**Severity:** CRITICAL | **Status:** open | **Filed:** 2026-05-25 by Reasonix Code

## Summary
`WorkflowRepository.java:56-133` contains 25+ consecutive `catch (Exception ignored) {}` blocks for `ALTER TABLE` schema migrations. Real failures (disk full, constraint violation, lock timeout) are silently swallowed.

## Affected Code
```java
try {
    jdbcTemplate.execute("alter table workflow_definitions add column schema_version ...");
} catch (Exception ignored) { }
```
This pattern repeats 25+ times.

## Impact
- Disk-full → migration silently skipped, data lost
- Constraint violation → migration skipped, later queries fail with cryptic errors
- Lock timeout → schema drift accumulates undetected

## Recommended Fix
At minimum, log at WARN. Better: catch specific exception types and only ignore "column already exists." Best: adopt Flyway/Liquibase.

## Mirror
Filed as https://github.com/dhickel/Magenta/issues/10
