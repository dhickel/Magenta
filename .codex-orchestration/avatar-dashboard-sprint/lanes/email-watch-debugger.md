# Email Watch Debugger

Date: 2026-05-22  
Thread: `389108fc-9436-4975-b949-33913136497a`  
Coordinator reply floor: `<0100019e51a84178-8f2e9a50-161a-4523-b8b9-dc4a0141b2c3-000000@email.amazonses.com>` at `2026-05-22T21:47:20.936Z`

## Problem Observed

Prior watcher agents reported they were "still watching" but returned a completed final status, so orchestration considered the lane done and stopped monitoring. Two Dwight replies were missed.

## Why This Happens

1. Chat/completion semantics mismatch:
   - If the agent sends a normal final response, coordinator treats the watcher as completed even if the text says "still watching."
   - The watch must be represented by a still-running process/session, not by prose intent.

2. Watch command lifecycle mismatch:
   - `wait-for-reply` exits on first match, timeout, or error by design.
   - If wrapped incorrectly (or launched once without a blocking owner session), the watcher lane can complete immediately.

3. Since-floor mismatch risk:
   - `--since` must be the exact coordinator-reply timestamp floor, not a casual "now" that can be later than incoming reply timestamps in racey handoffs.
   - For this thread, correct floor is `2026-05-22T21:47:20.936Z`.

4. Hidden non-watch exits:
   - Without `--thread-id`, script uses websocket mode and can fail early if Python `agentmail` package is unavailable.
   - Any nonzero exit (error/timeout) must be surfaced as watcher failure, not "still watching."

## Corrected Watcher Pattern

Use a single owning long-lived session for at least 60 minutes with explicit polling cadence and status-file audit records.

Required properties:
- thread-scoped to `389108fc-9436-4975-b949-33913136497a`
- from filter `dwight.hickel@gmail.com`
- timestamp floor `2026-05-22T21:47:20.936Z`
- poll every 120 seconds
- no final-complete message until 60 minutes elapsed or parent cancels

Reference implementation used in this lane:
- long-running Python poll loop invoking `~/.codex/skills/agentmail/scripts/agentmail thread <thread_id>`
- status JSONL written to:
  - `.codex-orchestration/avatar-dashboard-sprint/lanes/email-watch-debugger-status.jsonl`
- logs `watch_start`, per-`poll`, `match`, and `watch_complete` events

## Reporting Contract on Match

On first or any new qualifying Dwight message after the floor timestamp, report immediately:
- `message_id`
- `subject`
- `thread_id`
- message timestamp
- extracted body (`extracted_text` preferred, fallback `text`/`preview`)
- if extracted body is empty, treat `extracted_html`/`html` as actionable and extract the authored text before quoted history blocks

Do not send outbound email unless explicitly instructed.
