# Phase 02: Job Item Failure Resilience

## Context

The `OrchestrationRunnerService.runJob()` method iterates through `OrchestrationJobItem` records. If any single item throws, the exception propagates to `runAssignment()`'s catch block (line 92), which calls `fail()` on the entire assignment. There is no:

- Per-item retry on transient failure
- `continue_on_failure` flag to skip failed items and proceed
- Distinction between transient errors (network, timeout) and permanent errors (invalid input, missing task)

This means a single flaky task in a 10-item job fails the entire job with no recovery path. The `OrchestrationJobItem` record has no fields for retry configuration.

## Goal

Add configurable retry and continue-on-failure behavior to job items, so transient failures are retried automatically and non-critical item failures don't block the rest of the job.

## In Scope

- Add `retryCount` and `continueOnFailure` fields to `OrchestrationJobItem`
- Add matching columns to `orchestration_job_items` table
- Implement retry loop in `OrchestrationRunnerService.runJobItem()` / `runJob()`
- Implement continue-on-failure: on item failure, if `continueOnFailure` is true, record the error in evidence and proceed to the next item instead of failing the job

## Out of Scope

- Exponential backoff between retries (keep it simple: immediate retry with optional small delay)
- Per-job or global retry policy configuration (item-level is sufficient for current needs)
- Retry for non-job-item assignment types (TASK_RUN, WORKFLOW_RUN as standalone assignments)
- Dead-letter queue or separate failure tracking

## Design Decisions

### retryCount semantics

`retryCount` is the number of *additional* attempts after the first failure. A value of 0 (default) means no retry — fail immediately. A value of 2 means try up to 2 more times (3 total attempts).

This is the standard semantics used by most job systems and avoids ambiguity about whether the count includes the first attempt.

### continueOnFailure semantics

When `continueOnFailure` is true and an item fails after exhausting retries:
- The error is recorded in the job's evidence map under the item ID (with `status: FAILED`, `error: <message>`, `retriesExhausted: true`)
- The job continues to the next item
- The overall job assignment completes successfully (unless a later non-continue-on-failure item fails)

When `continueOnFailure` is false (default), behavior is unchanged: the job fails.

### retry delay

Simple fixed delay: 500ms between retries. No exponential backoff — the current use cases (task execution, workflow execution) are not network-call-heavy. If the task's `runSynchronously()` fails, it's likely a data/input issue that won't resolve with backoff. The retry is mainly for transient DB contention or rare timing issues.

### Where to add retry logic

In `runJob()`, wrap the `runJobItem()` call in a retry loop. Don't modify `runJobItem()` itself — it stays a single-shot dispatcher. The retry orchestration belongs at the job level, not the item dispatch level.

## Implementation Steps

### Step 1: Add fields to OrchestrationJobItem

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJobItem.java`

Add two fields with defaults:
```java
int retryCount,           // default 0 — no retries
boolean continueOnFailure  // default false — fail job on item error
```

The compact constructor (or the service layer) should validate:
- `retryCount >= 0 && retryCount <= 10` (sane upper bound)

### Step 2: Add columns to orchestration_job_items

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`

In `ensureSchema()`, add migration blocks after the `create table if not exists`:
```java
java.util.List<String> jobItemColumns = jdbcTemplate.queryForList(
    "select name from pragma_table_info('orchestration_job_items')", String.class
);
if (!jobItemColumns.contains("retry_count")) {
    jdbcTemplate.execute("alter table orchestration_job_items add column retry_count integer not null default 0");
}
if (!jobItemColumns.contains("continue_on_failure")) {
    jdbcTemplate.execute("alter table orchestration_job_items add column continue_on_failure integer not null default 0");
}
```

### Step 3: Update repository save/load

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`

Update `saveJobItem()` INSERT/UPSERT to include the new columns:
```sql
insert into orchestration_job_items (
    id, job_id, item_order, item_type, task_id, workflow_id, model_override, priority,
    config_json, retry_count, continue_on_failure, created_at, updated_at
)
values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
on conflict(id) do update set
    ...
    retry_count = excluded.retry_count,
    continue_on_failure = excluded.continue_on_failure,
    ...
```

Update `toJobItem()` row mapper to read `rs.getInt("retry_count")` and `rs.getBoolean("continue_on_failure")`.

### Step 4: Add retry wrapper to runJob()

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`

In `runJob()` (lines 116-157), replace the single `runJobItem()` call at line 144 with a retry loop. The logic in context:

```java
// Inside the for loop, after the WAIT_FOR_MESSAGE guard:

Object itemOutput = null;
Exception lastError = null;
int maxAttempts = item.retryCount() + 1;
for (int attempt = 0; attempt < maxAttempts; attempt++) {
    try {
        itemOutput = runJobItem(current, item);
        lastError = null;
        break;
    } catch (RuntimeException e) {
        lastError = e;
        if (attempt < maxAttempts - 1) {
            try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
        }
    }
}

if (lastError != null) {
    if (item.continueOnFailure()) {
        // Record failure in evidence and continue
        outputs.put(item.id(), Map.of("status", "FAILED", "error", lastError.getMessage()));
        evidence.put(item.id(), Map.of(
            "itemType", item.itemType().name(),
            "status", "FAILED",
            "error", lastError.getMessage(),
            "retriesExhausted", true,
            "failedAt", Instant.now().toString()
        ));
        current = checkpointed(current, i + 1, Map.of(
            "jobId", job.id(), "nextItemIndex", i + 1,
            "completedItemId", item.id(), "model", assignmentService.resolveModel(current, item)
        ), outputs, evidence);
        current = assignmentService.save(current);
        continue; // skip to next item
    } else {
        throw new RuntimeException("Job item failed: " + item.id(), lastError);
    }
}

// Normal success path (unchanged)...
outputs.put(item.id(), itemOutput);
// ...
```

### Step 5: Update schema.sql

**File:** `src/main/resources/schema.sql`

If `orchestration_job_items` is present in schema.sql (it's not currently — it's created in `ensureSchema()`), add the columns. If not, no change needed — the `ensureSchema()` migration handles it.

### Step 6: Update OrchestrationJobService.saveItem() validation

**File:** `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJobService.java`

Add validation to `saveItem()`:
```java
if (item.retryCount() < 0 || item.retryCount() > 10) {
    throw new IllegalArgumentException("retryCount must be between 0 and 10");
}
```

## Validation

1. **Unit test**: Create a job item with `retryCount=2`, mock `runJobItem()` to fail twice then succeed on the 3rd attempt — verify the item eventually succeeds
2. **Unit test**: Create a job item with `retryCount=1`, mock `runJobItem()` to always fail — verify the item fails after 2 total attempts
3. **Unit test**: Create a 3-item job where item 2 has `continueOnFailure=true` and fails — verify items 1 and 3 complete, evidence contains the failure record for item 2
4. **Unit test**: Create a 3-item job where item 2 has `continueOnFailure=false` (default) and fails — verify the job fails and item 3 never executes (backward-compatible behavior)
5. **Schema migration**: Verify existing databases get the new columns without error
6. **Smoke test**: Spring context starts, ensureSchema completes cleanly

## Exit Criteria

- [ ] `OrchestrationJobItem` has `retryCount` and `continueOnFailure` fields
- [ ] `orchestration_job_items` table has matching columns
- [ ] Repository persists and loads the new fields correctly
- [ ] `runJob()` retries failed items up to `retryCount` times
- [ ] `runJob()` respects `continueOnFailure` — records error, proceeds to next item
- [ ] Default behavior (retryCount=0, continueOnFailure=false) is backward-compatible
- [ ] Existing tests pass
- [ ] Validation bounds enforced (retryCount 0-10)
