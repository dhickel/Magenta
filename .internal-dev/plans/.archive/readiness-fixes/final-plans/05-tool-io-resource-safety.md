# 05 -- Tool I/O Resource Safety (OOM Hot Spots)

## Context (What Is Broken, Why)

Two tool I/O paths -- and one plan validator read -- allocate **unbounded memory proportional to their input source**. Under normal conditions the current `MAX_FETCH_CHARS` (20,000) post-hoc truncation protects the model-facing output, but the **raw HTTP body is fully allocated as a Java `String` before Jsoup parsing even starts**. Similarly, `file_replace` reads an entire file into memory, splits it into a `List<String>`, and creates multiple list copies before writing back, even if the final edit touches only a few lines.

A malicious or misconfigured remote server returning a multi-gigabyte response, or a multi-gigabyte file stored in the agent data root, can exhaust heap and crash the JVM before any truncation logic executes. The **maximum possible allocation is unbounded** -- it is gated only by the JDK `HttpClient` receive buffer and OS socket buffers, neither of which provide a hard Java-heap cap.

## Goal

Eliminate unbounded memory allocation in web/file tool read paths while preserving existing truncation semantics for normal-sized inputs.

## In Scope

- Bounded/streamed web response reads for fetch/search.
- File-size gates for full-buffer replacement paths.
- Bounded plan artifact reads used during validation flows.

## Out of Scope

- Replacing the file editing model with a new patch engine.
- Changing user-visible tool contracts beyond explicit truncation indicators.
- Reworking unrelated tool services that are already bounded.

### Affected Code Paths (with file:line)

| Path | File:Line | Allocation Method | Bound? |
|---|---|---|---|
| `fetch` full HTTP body | `AgentWebToolService.java:135` | `BodyHandlers.ofString()` | **No** |
| `fetch` Jsoup parse | `AgentWebToolService.java:111` | `Jsoup.parse(fullBody)` | **No** |
| `search` full HTTP body | `AgentWebToolService.java:70` | `BodyHandlers.ofString()` | **No** |
| `search` JSON parse | `AgentWebToolService.java:74` | `readTree(fullBody)` | **No** |
| `replace` full file read | `AgentFileToolService.java:262` | `Files.readString(target)` | **No** |
| `replace` split + copy | `AgentFileToolService.java:263-271` | `splitLines()` + `subList()` + `new ArrayList` | **No** |
| `readArtifact` full file read | `PlanCompletionService.java:209` | `Files.readString(resolved)` | Post-hoc 8K truncation at line 210 |
| `write` / `append` content | `AgentFileToolService.java:229,244` | Model-supplied `String` | Bounded by LLM output |
| `read` (file tool) | `AgentFileToolService.java:101-108` | `BufferedReader.readLine()` | Bounded by `MAX_READ_LINES=400` |
| `search` (file tool) | `AgentFileToolService.java:161-189` | `BufferedReader.readLine()` | Bounded by `MAX_MATCHES=100` |
| `shell` exec capture | `AgentShellToolService.java:151-170` | `ByteArrayOutputStream` | Bounded by `OUTPUT_LIMIT_BYTES=16384` |
| Config loader startup reads | `ExternalAiConfigLoader.java:125`, `AgentProfileSeeder.java:76` | `Files.readString()` | Startup-only, acceptable |

**The three critical unbounded paths are: `fetch`, `search` (web), and `replace` (file). `readArtifact` is secondary but still problematic.**

### Existing Truncation Semantics

The current user-visible truncation model:

- **Web fetch**: Characters are truncated to `maxCharacters` (default 12,000, max 20,000) at `AgentWebToolService.java:121-124` -- but this truncation happens **after the entire HTTP response body has been allocated**. The `truncated` boolean is set and returned in the `WebFetchResult` record.
- **File replace**: No size limits at all. The entire file is read, lines are split, anchor hashes are validated, and the replacement is performed entirely in memory.
- **File read**: Already chunked with `startLine`/`nextStartLine` and capped at `MAX_READ_LINES=400`. Safe.
- **File search**: Reads line-by-line with `BufferedReader` and caps matches at `MAX_MATCHES=100`. Safe.
- **Shell exec**: Captures up to `OUTPUT_LIMIT_BYTES=16384` with in-stream truncation. Safe.
- **Plan artifact read**: Post-hoc truncation at 8,000 chars (line 210). Reads full file first.

---

## Current Architecture (Full Read Flow)

### 1. Web Fetch -- `AgentWebToolService.fetch()` (lines 99-126)

```
fetch(url, maxCharacters)
  |
  v
validatedHttpUri(url)                    -- validates scheme, host
  |
  v
send(uri, accept)                        -- line 128-136
  |  HttpRequest.newBuilder(uri)
  |    .timeout(12s)
  |    .header("Accept", "text/html,...")
  |    .GET().build()
  |
  v  httpClient.send(request, BodyHandlers.ofString(UTF_8))
  |  ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ ALLOCATION HOTSPOT #1
  |  Entire response body becomes a String in heap.
  |  No Content-Length pre-check. No streaming.
  |  Max allocation: UNBOUNDED (limited only by socket buffers + GC).
  |
  v
response.statusCode() check              -- 200-299
  |
  v
contentType inspection (line 107-108)    -- reads body() again (cheap, already in memory)
  |
  v
IF html:
  Jsoup.parse(response.body(), uri)      -- ALLOCATION HOTSPOT #2
  |  Parses full String into DOM tree.
  |  Additional allocation ~2-5x body size.
  |
  v
document.select("script,style,...").remove()  -- removes elements from DOM
  |
  v
document.body().text()                   -- extracts text, another allocation
  |
IF plain text:
  text = response.body()                 -- uses already-allocated String
  |
  v
normalizeWhitespace(text)                -- regex \s+ replacement (yet another string copy)
  |
  v
truncate to maxCharacters (12K-20K)      -- substring on already-allocated full text
  |                                        THIS IS TOO LATE for OOM prevention.
  |
  v
return WebFetchResult(url, title, text, truncated, contentType)
```

### 2. File Replace -- `AgentFileToolService.replace()` (lines 254-287)

```
replace(path, startAnchor, endAnchor, replacement)
  |
  v
resolveTextFile(path)                    -- path confinement check
  |
  v
Files.readString(target, UTF_8)          -- ALLOCATION HOTSPOT #5
  |  Entire file becomes String in heap.
  |  Max allocation: UNBOUNDED (file size on disk).
  |
  v
splitLines(original)                     -- List<String> from split()
  |  Another allocation ~file size.
  |
  v
validateAnchor(start, lines)             -- hash check
validateAnchor(end, lines)
  |
  v
replacementLines = splitLines(replacement)
  |
  v
updated = new ArrayList<>()              -- ALLOCATION HOTSPOT #6
  |  Collects subList() copies, replacement, subList() copies.
  |  Yet another ~file size allocation.
  |
  v
String.join(lineSeparator, updated)      -- Final string allocation
  |
  v
Files.writeString(target, updatedText)   -- Write back
  |
  v
return FileReplaceResult(...)
```

### Maximum Possible Allocations

| Path | Source of Unboundedness | Realistic Max |
|---|---|---|
| `fetch` HTTP body | Remote server response size | 100+ MB from a misconfigured server |
| `fetch` Jsoup DOM | ~2-5x body size | 200-500 MB |
| `search` HTTP body | SearXNG response size | Normally <1 MB, but unbounded |
| `search` Jackson tree | ~1-2x JSON size | 1-2 MB normally |
| `replace` file read | File on disk | Operating system file size limit |
| `replace` line split + copy | ~3x file size in transient allocations | 3x OS file size limit |
| `readArtifact` | File on disk | OS file size limit |

---

## Target Architecture

### Design Principles

1. **Pre-read size guard**: Check `Content-Length` header (web) or `Files.size()` (file) before reading. If above a configurable threshold, refuse or switch to streaming mode.
2. **Streaming read with bounded in-memory window**: Read input into a bounded `ByteArrayOutputStream` (or equivalent) that stops at a hard ceiling.
3. **Inline truncation**: Truncate during the read operation, not after full allocation.
4. **Clear truncation markers**: The tool output must include explicit truncation indicators. The existing `truncated` boolean in result records is good; for replace, a new truncation marker in the error message or a `contentTruncated` field must be added.
5. **Preserve existing semantics**: The user-visible truncation behavior (max characters for fetch, anchored line replacement for files) must remain identical for inputs within safe bounds.
6. **Configurable thresholds with safe defaults**: Hard limits must be in code constants, not external config, to prevent misconfiguration. Provide constants that can be overridden via system properties for emergency tuning.

### Component Design

#### A. Pre-Read Size Guard (Web Fetch & Search)

New constant(s) in `AgentWebToolService`:

```java
// Maximum HTTP response body size in bytes (default: 5 MB)
// Override with system property: -Dmagenta.web.maxBodyBytes=5242880
private static final long MAX_RESPONSE_BODY_BYTES =
    Long.parseLong(System.getProperty("magenta.web.maxBodyBytes", "5242880"));
```

Use `HttpResponse.BodyHandlers.ofInputStream()` and manually read with bounded buffer -- this lets us check `Content-Length` from the response headers after the GET starts, and abort reading if it exceeds the limit. This avoids the extra round-trip of a HEAD request.

#### B. Streaming/Truncating Read for Web Fetch

Replace `BodyHandlers.ofString()` with a custom body handler that:

1. Reads the response into a bounded `ByteArrayOutputStream` with `MAX_RESPONSE_BODY_BYTES` ceiling.
2. Returns a `String` containing at most `MAX_RESPONSE_BODY_BYTES` bytes of content.
3. Sets a `contentTruncated` flag if the input exceeded the ceiling.

The existing `normalizeWhitespace()` and character-based truncation (`maxCharacters`) then operate on a safely bounded string.

For `search()`, a similar bounded read applies, but the ceiling can be lower (SearXNG JSON responses are typically <100 KB).

#### C. File Size Gate for File Replace

New constants in `AgentFileToolService`:

```java
// Maximum file size in bytes for full-buffer operations (default: 10 MB)
// Override with system property: -Dmagenta.file.maxReplaceBytes=10485760
private static final long MAX_REPLACE_FILE_BYTES =
    Long.parseLong(System.getProperty("magenta.file.maxReplaceBytes", "10485760"));
```

In `replace()` before `Files.readString()`:

```java
long fileSize = Files.size(target);
if (fileSize > MAX_REPLACE_FILE_BYTES) {
    throw new IllegalArgumentException(
        "File exceeds maximum size for replace operations (" +
        fileSize + " bytes > " + MAX_REPLACE_FILE_BYTES + " bytes). " +
        "Use streaming tools (file_read, file_write) for large files."
    );
}
```

#### D. Streaming Bounded Read for Plan Artifact Read

In `PlanCompletionService.readArtifact()`:
1. Check `Files.size(resolved)` first.
2. If > 32 KB, read only the first 32 KB and add a truncation marker.

#### E. Truncation Communication to User

- **Web fetch/search**: The existing `truncated` boolean on result records is sufficient. The text content at truncation point should have an inline marker like `"[Response body truncated at N bytes]"`.
- **File replace**: The size gate throws a clear error message with the actual file size, the maximum, and a recommendation to use streaming tools.
- **Plan artifact read**: The existing `"[truncated at 8000 chars]"` marker is sufficient.

---

## Implementation Steps

### Step 1: Add Configuration Constants

**File**: `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java`

Add constants at the top of the class:

```java
// Maximum HTTP response body to read into memory before truncation (5 MB).
private static final long MAX_RESPONSE_BYTES =
    Long.parseLong(System.getProperty("magenta.web.maxResponseBytes", Long.toString(5_242_880)));

// Maximum SearXNG JSON response body (2 MB).
private static final long MAX_SEARCH_RESPONSE_BYTES =
    Long.parseLong(System.getProperty("magenta.web.maxSearchResponseBytes", Long.toString(2_097_152)));
```

**File**: `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`

Add constants after line 39:

```java
// Maximum file size for full-buffer operations (replace, validate). Files larger
// than this are rejected with a clear message directing the caller to streaming tools.
private static final long MAX_FULL_BUFFER_BYTES =
    Long.parseLong(System.getProperty("magenta.file.maxFullBufferBytes", Long.toString(10_485_760))); // 10 MB
```

**File**: `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`

Add constant:

```java
// Maximum artifact file size to read for validation (32 KB).
private static final int MAX_ARTIFACT_READ_CHARS = 32_768;
```

### Step 2: Implement Bounded Streaming Body Handler for Web Fetches

Replace `ofString()` with `ofInputStream()` and add a post-processing method:

```java
private record BoundedBody(String content, boolean bodyTruncated) {}

private BoundedBody readBounded(HttpResponse<InputStream> response, long maxBytes) throws IOException {
    try (InputStream in = response.body()) {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long total = 0;
        int read;
        boolean truncated = false;
        while ((read = in.read(buffer)) != -1) {
            long remaining = maxBytes - total;
            if (remaining <= 0) {
                truncated = true;
                while (in.read(buffer) != -1) { /* discard */ }
                break;
            }
            int toWrite = (int) Math.min(read, remaining);
            out.write(buffer, 0, toWrite);
            total += toWrite;
            if (read > remaining) {
                truncated = true;
                while (in.read(buffer) != -1) { /* discard */ }
                break;
            }
        }
        String content = out.toString(StandardCharsets.UTF_8);
        if (truncated) {
            content += "\n\n[Response body exceeded maximum size of " + maxBytes +
                      " bytes. Content was truncated during download to prevent memory exhaustion.]";
        }
        return new BoundedBody(content, truncated);
    }
}
```

### Step 3: Modify `send()` to Use Bounded Reads

Create a `sendBounded()` overload that returns `BoundedBody`, replacing the old `send()`:

```java
private BoundedBody sendBounded(URI uri, String accept, long maxBytes) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri)
        .timeout(REQUEST_TIMEOUT)
        .header("Accept", accept)
        .header("User-Agent", "Magenta/1.0 (+https://local.magenta)")
        .GET()
        .build();
    HttpResponse<InputStream> response = httpClient.send(request,
        HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
        BoundedBody errorBody = readBounded(response, 8192); // 8 KB for error messages
        throw new IllegalStateException("HTTP " + response.statusCode() + ": " + errorBody.content());
    }
    return readBounded(response, maxBytes);
}
```

### Step 4: Update `fetch()` to Use Bounded Send

1. Replace `send()` with `sendBounded(uri, accept, MAX_RESPONSE_BYTES)`.
2. Get `String body = bounded.content()` and `boolean bodyTruncated = bounded.bodyTruncated()`.
3. Use `body` (already safely bounded) for Jsoup parsing or plain text extraction.
4. The existing `maxCharacters` truncation still applies on top.
5. Set `truncated = bodyTruncated || (text.length() > limit)`.

### Step 5: Update `search()` to Use Bounded Send

1. Replace `send()` with `sendBounded(uri, "application/json", MAX_SEARCH_RESPONSE_BYTES)`.
2. Use `bounded.content()` for `objectMapper.readTree()`.
3. If `bodyTruncated` is true, set a flag on result.

### Step 6: Add File Size Gate to `replace()`

In `replace()` method, after `resolveTextFile(path)` and before `Files.readString()`:

```java
long fileSize = Files.size(target);
if (fileSize > MAX_FULL_BUFFER_BYTES) {
    throw new IllegalArgumentException(
        "File is too large for file_replace (" + fileSize + " bytes exceeds " +
        MAX_FULL_BUFFER_BYTES + " byte limit). To edit large files, use file_read " +
        "to inspect content, then use file_write to write the desired content."
    );
}
```

### Step 7: Add Bounded Read to Plan Artifact Reading

Replace `Files.readString(resolved)` with a bounded reader that checks `Files.size()` first:
- If > 32KB, read only first 32KB and add truncation marker
- Preserve existing 8000-char post-hoc truncation

### Step 8: Remove Dead `send()` Method

After `fetch()` and `search()` are migrated to `sendBounded()`, remove the old `send()` method to prevent accidental unbounded use.

---

## Validation

### Performance Safety Tests

### Test 1: Large HTTP Response Body (Web Fetch)
- Start a local HTTP server that responds with `MAX_RESPONSE_BYTES + 1` bytes of HTML.
- Call `fetch()`. Verify no `OutOfMemoryError`, response is returned with `truncated=true`.

### Test 2: Very Large File Replace Rejection
- Create a file with size `MAX_FULL_BUFFER_BYTES + 1`.
- Call `replace()`. Verify `IllegalArgumentException` is thrown with clear guidance.

### Test 3: Normal-Sized Operations Still Work
- Run existing tests to verify no regressions.
- Verify `fetch()` returns correct extracted text for normal HTML pages.
- Verify `replace()` correctly edits normal-sized files.

### Test 4: Plan Artifact Read with Large File
- Create an artifact file larger than `MAX_ARTIFACT_READ_CHARS`.
- Verify file is read only up to the limit with truncation marker.

### Test 5: Memory Pressure Test (Integration)
- Configure JVM with `-Xmx128m`.
- Run fetch calls against a server returning 100 MB responses.
- Verify process does not crash and all calls return with truncation markers.

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `alpha-milestone-gate-summary.md`: "`AgentWebToolService` and `AgentFileToolService` lack streaming for large payloads, creating OOM vulnerabilities."
- `security-and-performance-report.md`: "`AgentFileToolService.replace` reads the entire file into memory using `Files.readString`."
- `security-and-performance-report.md`: "`AgentWebToolService.fetch` uses `HttpResponse.BodyHandlers.ofString()`, which reads the entire response into memory before truncation."
- `security-and-performance-report.md`: "Update `AgentWebToolService` to use a streaming body handler or check `Content-Length` before reading the entire body."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, resource-safety test output, and any low-memory integration evidence.

Validation sub-agent prompt:
```text
You are validating the Tool I/O Resource Safety remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/05-tool-io-resource-safety.md`, then manually inspect `AgentWebToolService`, `AgentFileToolService`, `PlanCompletionService`, and tests. Do not trust the implementer's claim that memory is bounded without checking every read path.

Validation contract:
- Confirm web fetch/search no longer use `BodyHandlers.ofString()` or any equivalent unbounded full-body allocation.
- Confirm HTTP response reads enforce hard byte ceilings even when `Content-Length` is absent or dishonest.
- Confirm `file_replace` rejects or safely handles files above the configured full-buffer limit before `Files.readString`.
- Confirm plan artifact reads are bounded before full allocation.
- Confirm normal-sized fetch/search/replace output remains compatible and truncation is visible to the caller.

Return findings first, ordered by severity, with file/line references and any remaining unbounded allocation path.
```

Manual work proof to verify:
- Search changed code for `BodyHandlers.ofString`, `Files.readString`, `readAllBytes`, and `ObjectMapper.readTree` on unbounded inputs.
- Verify tests include oversized HTTP response, absent `Content-Length`, oversized file replace, normal file replace, large artifact read, and low-heap fetch behavior.
- Verify focused tool tests, `mvn test`, and startup smoke output.

---

## Risk Assessment and Rollback Strategy

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `BodyHandlers.ofInputStream()` behavior differs from `ofString()` in edge cases | Low | Medium | JDK HttpClient handles decompression transparently at socket level. InputStream gives decompressed bytes, same as ofString. |
| Truncation mid-stream could produce malformed HTML breaking Jsoup | Medium | Low | Jsoup is lenient. Test with truncated HTML to verify graceful degradation. |
| File size gate breaks workflows that edit large files | Medium | Medium | Error message directs users to file_read + file_write. |
| HEAD request for Content-Length adds latency | Low | Low | Preferred approach uses InputStream with bounded read, avoiding extra round-trip entirely. |

Rollback: All limits are configurable via system properties for emergency tuning, but do not use negative values unless the implementation explicitly supports them. Prefer raising the limit temporarily over disabling bounds. Changes are isolated to `send()` → `sendBounded()`, a size check in `replace()`, and a bounded read in `readArtifact()`.

---

## Exit Criteria

1. All three unbounded paths (`fetch`, `search`, `replace`) have hard memory bounds that cannot be exceeded regardless of input size.
2. Existing truncation semantics are preserved: For inputs within safe bounds, output is byte-identical to current implementation.
3. Truncation is clearly communicated: Tool output includes `truncated=true` and descriptive inline markers when content was bounded.
4. All existing tests pass without modification.
5. New resource safety tests pass: Large payload simulation tests verify OOM does not occur.
6. Plan artifact reads are bounded to prevent OOM during plan validation.
7. No new allocations are unbounded: Code review confirms no `Files.readString()`, `BodyHandlers.ofString()`, or `readTree()` on unbuffered inputs remain.

## Critical Files for Implementation

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ToolTranscriptService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
